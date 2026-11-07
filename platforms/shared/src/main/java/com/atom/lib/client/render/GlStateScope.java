// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;
import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.opengl.GL33C.*;

/**
 * A paired snapshot of core GL driver state. Uses no Blaze3D state mutator or
 * cache invalidation. Host object contents/lifetimes must not change in scope.
 *
 * <p>Two snapshot boundaries exist:
 * <ul>
 *   <li>{@link #capture()} keeps the full contract used by {@code withContext}
 *       and any arbitrary host GL caller.</li>
 *   <li>{@link #captureSkia()} serves the bridge's own Skia-only pass and may
 *       use the slim boundary. The slim boundary drops the state families the
 *       Ganesh GL backend provably never writes (uniform-buffer bindings, clip
 *       distances, sample mask). Writing nothing means there is nothing to
 *       restore, so no state can leak. See {@code SlimSnapshotEvidenceTest} and
 *       {@code docs/evidence/gl-scope-slim-20261106.md}.</li>
 * </ul>
 *
 * <p>Per-draw-buffer state stays in both boundaries: the probe test shows that
 * Skia's global {@code glBlendFuncSeparate} clobbers values a host may have set
 * with {@code glBlendFuncSeparatei}, so it has to be restored explicitly.
 */
public final class GlStateScope implements AutoCloseable {
    private static final ThreadLocal<Deque<GlStateScope>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    /**
     * P0-2: GL implementation limits are constant for one (context, capabilities)
     * pair. They only decide array lengths; the actual state snapshot is still
     * captured and restored in full, so the legacy GL contract is unchanged.
     * Cached only on the fast path; the legacy path keeps its per-frame queries.
     */
    private static final AtomicLong LIMIT_QUERY_COUNT = new AtomicLong();
    private static volatile Limits cachedLimits;
    private record SkiaTextures(long context, GLCapabilities capabilities, int units, int[] targets, int[] bindings) {}
    private static volatile SkiaTextures cachedSkiaTextures;

    /**
     * Slim boundary for the Skia-only path. Default on: the dropped families are
     * covered by a real-GL probe test, and the switch exists so a device A/B can
     * fall back to the pre-2026-11-06 boundary without a rebuild.
     */
    private static volatile boolean slimSnapshot = Boolean.parseBoolean(System.getProperty("atomlib.glscope.slim", "true"));
    /** Debug tripwire: capture the dropped families anyway, report instead of leaking. */
    private static volatile boolean VERIFY_SLIM = Boolean.getBoolean("atomlib.glscope.verify");
    private static volatile boolean logCalls = Boolean.getBoolean("atomlib.glscope.logCalls");
    /**
     * Experimental unit-count tier: {@code 0} keeps every Skia-reachable unit;
     * {@code 12} scans units 0..11 plus the last unit (Ganesh's scratch/upload
     * slot, GrGLGpu.cpp:3071-3080). Default keeps all units because a program
     * with more than 12 samplers would otherwise leave a binding behind.
     */
    private static volatile int SLIM_TEXTURE_UNITS = Integer.getInteger("atomlib.glscope.units", 0);
    private static final AtomicLong TOTAL_CAPTURE_CALLS = new AtomicLong();
    private static final AtomicLong TOTAL_RESTORE_CALLS = new AtomicLong();
    private static volatile int lastCaptureCalls, lastRestoreCalls;
    private static volatile String lastSlimViolation;

    private static SkiaTextures skiaTextures(long context, GLCapabilities capabilities) {
        SkiaTextures cached=cachedSkiaTextures;
        if(cached!=null && cached.context==context && cached.capabilities==capabilities) return cached;
        boolean external=false;
        for(int i=0,n=glGetInteger(GL_NUM_EXTENSIONS);i<n;i++)
            if("GL_OES_EGL_image_external".equals(glGetStringi(GL_EXTENSIONS,i))) external=true;
        // Skia da51f0d60e: GrGLCaps.cpp:496-497, GrGLGpu.h:524,
        // GrGLGpu.cpp:3015-3026,3062-3080. Includes the scratch/upload unit.
        int[] targets=external ? new int[]{GL_TEXTURE_2D,GL_TEXTURE_RECTANGLE,0x8D65} : new int[]{GL_TEXTURE_2D,GL_TEXTURE_RECTANGLE};
        int[] bindings=external ? new int[]{GL_TEXTURE_BINDING_2D,GL_TEXTURE_BINDING_RECTANGLE,0x8D67} : new int[]{GL_TEXTURE_BINDING_2D,GL_TEXTURE_BINDING_RECTANGLE};
        // GrGLCaps.cpp:495-497 pins kMaxSaneSamplers to 32 in this Skia revision.
        SkiaTextures fresh=new SkiaTextures(context,capabilities,Math.min(32,glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)),targets,bindings);
        cachedSkiaTextures=fresh;
        return fresh;
    }

    private static final class Limits {
        long context;
        GLCapabilities capabilities;
        int maxDrawBuffers, maxClipDistances, maxSampleMaskWords;
        int maxUniformBufferBindings, maxCombinedTextureImageUnits;
        boolean hasIndexedBlend, hasPipeline, hasSamplers;
    }

    /** Package-private probes for the A/B tests; not part of the public API. */
    static long limitQueryCount() { return LIMIT_QUERY_COUNT.get(); }
    static void invalidateLimits() { cachedLimits = null; }
    static boolean slimSnapshot() { return slimSnapshot; }
    static void setSlimSnapshot(boolean value) { slimSnapshot = value; }
    static void setVerifySlim(boolean value) { VERIFY_SLIM = value; }
    static boolean verifySlimEnabled() { return VERIFY_SLIM; }
    static void setLogCalls(boolean value) { logCalls = value; }
    static void setSlimTextureUnits(int value) { SLIM_TEXTURE_UNITS = value; }
    static int lastCaptureCalls() { return lastCaptureCalls; }
    static int lastRestoreCalls() { return lastRestoreCalls; }
    static long totalCaptureCalls() { return TOTAL_CAPTURE_CALLS.get(); }
    static long totalRestoreCalls() { return TOTAL_RESTORE_CALLS.get(); }
    static String lastSlimViolation() { return lastSlimViolation; }

    private static Limits cachedLimitsFor(long context, GLCapabilities capabilities) {
        Limits cached = cachedLimits;
        if (cached != null && cached.context == context && cached.capabilities == capabilities) return cached;
        Limits fresh = new Limits();
        fresh.context = context;
        fresh.capabilities = capabilities;
        fresh.maxDrawBuffers = glGetInteger(GL_MAX_DRAW_BUFFERS);
        fresh.maxClipDistances = glGetInteger(GL_MAX_CLIP_DISTANCES);
        fresh.maxSampleMaskWords = glGetInteger(GL_MAX_SAMPLE_MASK_WORDS);
        fresh.maxUniformBufferBindings = glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS);
        fresh.maxCombinedTextureImageUnits = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        fresh.hasIndexedBlend = capabilities.OpenGL40 || capabilities.GL_ARB_draw_buffers_blend;
        fresh.hasPipeline = capabilities.OpenGL41 || capabilities.GL_ARB_separate_shader_objects;
        fresh.hasSamplers = capabilities.OpenGL33 || capabilities.GL_ARB_sampler_objects;
        LIMIT_QUERY_COUNT.addAndGet(5);
        cachedLimits = fresh;
        return fresh;
    }

    private static final int[] ENABLES = {
        GL_BLEND, GL_CULL_FACE, GL_DEPTH_TEST, GL_SCISSOR_TEST, GL_STENCIL_TEST,
        GL_DITHER, GL_MULTISAMPLE, GL_FRAMEBUFFER_SRGB, GL_RASTERIZER_DISCARD,
        GL_POLYGON_OFFSET_FILL, GL_SAMPLE_ALPHA_TO_COVERAGE, GL_SAMPLE_ALPHA_TO_ONE,
        GL_SAMPLE_COVERAGE, GL_SAMPLE_MASK, GL_PRIMITIVE_RESTART, GL_DEPTH_CLAMP,
        GL_TEXTURE_CUBE_MAP_SEAMLESS, GL_COLOR_LOGIC_OP,
        // Ganesh's onResetContext(kMisc) enables this and never restores it; the
        // real-GL probe (GlScopePixelProbe) caught the leak. It is core in 3.2.
        GL_VERTEX_PROGRAM_POINT_SIZE
    };
    private static final int[] TEXTURE_TARGETS = {
        GL_TEXTURE_1D, GL_TEXTURE_2D, GL_TEXTURE_3D, GL_TEXTURE_1D_ARRAY, GL_TEXTURE_2D_ARRAY,
        GL_TEXTURE_CUBE_MAP, GL_TEXTURE_RECTANGLE, GL_TEXTURE_BUFFER,
        GL_TEXTURE_2D_MULTISAMPLE, GL_TEXTURE_2D_MULTISAMPLE_ARRAY
    };
    private static final int[] TEXTURE_BINDINGS = {
        GL_TEXTURE_BINDING_1D, GL_TEXTURE_BINDING_2D, GL_TEXTURE_BINDING_3D, GL_TEXTURE_BINDING_1D_ARRAY, GL_TEXTURE_BINDING_2D_ARRAY,
        GL_TEXTURE_BINDING_CUBE_MAP, GL_TEXTURE_BINDING_RECTANGLE, GL_TEXTURE_BINDING_BUFFER,
        GL_TEXTURE_BINDING_2D_MULTISAMPLE, GL_TEXTURE_BINDING_2D_MULTISAMPLE_ARRAY
    };
    private static final int[] PIXEL_STORES = {
        GL_PACK_ALIGNMENT, GL_PACK_ROW_LENGTH, GL_PACK_IMAGE_HEIGHT, GL_PACK_SKIP_PIXELS, GL_PACK_SKIP_ROWS, GL_PACK_SKIP_IMAGES, GL_PACK_SWAP_BYTES, GL_PACK_LSB_FIRST,
        GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_IMAGE_HEIGHT, GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_ROWS, GL_UNPACK_SKIP_IMAGES, GL_UNPACK_SWAP_BYTES, GL_UNPACK_LSB_FIRST
    };
    private static final int[] BUFFER_TARGETS = {
        GL_ARRAY_BUFFER, GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, GL_PIXEL_PACK_BUFFER,
        GL_PIXEL_UNPACK_BUFFER, GL_UNIFORM_BUFFER, GL_TRANSFORM_FEEDBACK_BUFFER
    };
    private static final int[] BUFFER_BINDINGS = {
        GL_ARRAY_BUFFER_BINDING, GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, GL_PIXEL_PACK_BUFFER_BINDING,
        GL_PIXEL_UNPACK_BUFFER_BINDING, GL_UNIFORM_BUFFER_BINDING, GL_TRANSFORM_FEEDBACK_BUFFER_BINDING
    };
    private final Thread owner = Thread.currentThread();
    private final long context = glfwGetCurrentContext();
    private final GLCapabilities capabilities = GL.getCapabilities();
    /** True: the Skia-only boundary that omits state Ganesh never writes. */
    private final boolean slim;
    /** True when the slim path also captured the omitted families to report leaks. */
    private final boolean verifySlim;
    private final int drawFbo, readFbo, readBuffer, renderbuffer, program, vao, elementBuffer, activeTexture;
    private final int[] viewport, scissor, colorMask, blend, stencilFront, stencilBack, drawBuffers;
    private final int[] buffers, pixelStores;
    /** Null when the slim boundary omitted the family. Otherwise the full snapshot. */
    private final int[] sampleMasks, uniformBuffers;
    private final long[] uniformOffsets, uniformSizes;
    private final boolean[] enables, clipDistances, drawBlendEnabled;
    private final int[][] drawColorMasks, drawBlendFunctions;
    /** True when every draw buffer carries the same values as the global state. */
    private final boolean indexedUniform;
    private final int[][] textures;
    private final int[] textureTargets;
    /** Binding enums matching {@link #textureTargets}, in the same order. */
    private final int[] textureBindingNames;
    /** Actual texture unit indices covered by this snapshot (order preserved). */
    private final int[] textureUnits;
    /** Units the slim tier skipped; only non-empty when verify mode reads them. */
    private final int[] unwatchedUnits;
    private final int[] samplers;
    private final boolean hasSamplers, hasPipeline, hasIndexedBlend;
    private final int pipeline;
    private final float[] blendColor, clearColor;
    private final double[] depthRange;
    private final boolean depthMask, sampleInvert;
    private final int depthFunc, cullFace, frontFace, polygonMode, clearStencil, primitiveRestart;
    private final float polygonOffsetFactor, polygonOffsetUnits, lineWidth, sampleCoverage;
    private final double clearDepth;
    /** Null on the legacy path: then every helper below performs the original glGet call. */
    private final Limits limits;
    private int calls, captureCalls, restoreCalls;
    private boolean closed;

    private int maxDrawBuffers() { if (limits != null) return limits.maxDrawBuffers; spend(1); return glGetInteger(GL_MAX_DRAW_BUFFERS); }
    private int maxClipDistances() { if (limits != null) return limits.maxClipDistances; spend(1); return glGetInteger(GL_MAX_CLIP_DISTANCES); }
    private int maxSampleMaskWords() { if (limits != null) return limits.maxSampleMaskWords; spend(1); return glGetInteger(GL_MAX_SAMPLE_MASK_WORDS); }
    private int maxUniformBufferBindings() { if (limits != null) return limits.maxUniformBufferBindings; spend(1); return glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS); }
    private int maxCombinedTextureImageUnits() { if (limits != null) return limits.maxCombinedTextureImageUnits; spend(1); return glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS); }

    /** Counts driver entries so a device run can prove the boundary really shrank. */
    private void spend(int n) { calls += n; }

    /**
     * Unit indices this snapshot touches. The full boundary scans every unit;
     * the optional slim tier scans a low window plus the last unit, because
     * Ganesh binds its scratch/upload texture to {@code numTextureUnits()-1}.
     */
    private static int[] scanUnits(int unitCount,boolean limited) {
        if(!limited || SLIM_TEXTURE_UNITS<=0 || SLIM_TEXTURE_UNITS>=unitCount-1) return allUnits(unitCount);
        int low=Math.min(SLIM_TEXTURE_UNITS,unitCount);
        int[] units=new int[low+1];
        for(int i=0;i<low;i++) units[i]=i;
        units[low]=unitCount-1;
        return units;
    }

    private static int[] complement(int unitCount,int[] covered) {
        boolean[] seen=new boolean[unitCount];
        for(int unit:covered) if(unit>=0 && unit<unitCount) seen[unit]=true;
        int missing=0;
        for(boolean s:seen) if(!s) missing++;
        int[] out=new int[missing];
        for(int i=0,at=0;i<unitCount;i++) if(!seen[i]) out[at++]=i;
        return out;
    }

    private static int[] allUnits(int unitCount) {
        int[] units=new int[unitCount];
        for(int i=0;i<unitCount;i++) units[i]=i;
        return units;
    }

    private GlStateScope(boolean skia) {
        if (context == 0 || !capabilities.OpenGL32) throw new IllegalStateException("A current OpenGL 3.2+ context is required");
        limits = SkijaRenderBridge.fastState() ? cachedLimitsFor(context, capabilities) : null;
        slim = skia && slimSnapshot;
        verifySlim = slim && VERIFY_SLIM;
        // The verify tripwire captures the omitted families so it can report a
        // leak instead of causing one; it is debug-only and off by default.
        boolean captureDropped = !slim || verifySlim;
        drawFbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);readFbo=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);spend(2);
        readBuffer=glGetInteger(GL_READ_BUFFER);renderbuffer=glGetInteger(GL_RENDERBUFFER_BINDING);spend(2);
        drawBuffers=new int[drawFbo == 0 ? 1 : maxDrawBuffers()];
        for(int i=0;i<drawBuffers.length;i++) drawBuffers[i]=glGetInteger(GL_DRAW_BUFFER0+i);
        spend(drawBuffers.length);
        viewport=ints(GL_VIEWPORT,4);scissor=ints(GL_SCISSOR_BOX,4);colorMask=ints(GL_COLOR_WRITEMASK,4);spend(3);
        blend=new int[]{glGetInteger(GL_BLEND_SRC_RGB),glGetInteger(GL_BLEND_DST_RGB),glGetInteger(GL_BLEND_SRC_ALPHA),glGetInteger(GL_BLEND_DST_ALPHA),glGetInteger(GL_BLEND_EQUATION_RGB),glGetInteger(GL_BLEND_EQUATION_ALPHA)};spend(6);
        blendColor=floats(org.lwjgl.opengl.ARBImaging.GL_BLEND_COLOR,4);clearColor=floats(GL_COLOR_CLEAR_VALUE,4);spend(2);
        int drawCount=maxDrawBuffers();
        drawBlendEnabled=new boolean[drawCount];drawColorMasks=new int[drawCount][4];
        hasIndexedBlend=limits != null ? limits.hasIndexedBlend : (capabilities.OpenGL40 || capabilities.GL_ARB_draw_buffers_blend);
        drawBlendFunctions=hasIndexedBlend ? new int[drawCount][6] : null;
        int[] blendNames={GL_BLEND_SRC_RGB,GL_BLEND_DST_RGB,GL_BLEND_SRC_ALPHA,GL_BLEND_DST_ALPHA,GL_BLEND_EQUATION_RGB,GL_BLEND_EQUATION_ALPHA};
        for(int i=0;i<drawCount;i++) {
            drawBlendEnabled[i]=glIsEnabledi(GL_BLEND,i);glGetIntegeri_v(GL_COLOR_WRITEMASK,i,drawColorMasks[i]);
            spend(2);
            if(hasIndexedBlend) { for(int j=0;j<blendNames.length;j++) drawBlendFunctions[i][j]=glGetIntegeri(blendNames[j],i);spend(blendNames.length); }
        }
        depthRange=new double[2];glGetDoublev(GL_DEPTH_RANGE,depthRange);spend(1);
        depthMask=glGetBoolean(GL_DEPTH_WRITEMASK);depthFunc=glGetInteger(GL_DEPTH_FUNC);clearDepth=glGetDouble(GL_DEPTH_CLEAR_VALUE);spend(3);
        cullFace=glGetInteger(GL_CULL_FACE_MODE);frontFace=glGetInteger(GL_FRONT_FACE);polygonMode=ints(GL_POLYGON_MODE,2)[0];spend(3);
        polygonOffsetFactor=glGetFloat(GL_POLYGON_OFFSET_FACTOR);polygonOffsetUnits=glGetFloat(GL_POLYGON_OFFSET_UNITS);lineWidth=glGetFloat(GL_LINE_WIDTH);spend(3);
        clearStencil=glGetInteger(GL_STENCIL_CLEAR_VALUE);primitiveRestart=glGetInteger(GL_PRIMITIVE_RESTART_INDEX);spend(2);
        stencilFront=stencil(false);stencilBack=stencil(true);spend(14);
        enables=new boolean[ENABLES.length];for(int i=0;i<ENABLES.length;i++) enables[i]=glIsEnabled(ENABLES[i]);spend(ENABLES.length);
        clipDistances=captureDropped ? new boolean[maxClipDistances()] : null;
        if(clipDistances!=null) { for(int i=0;i<clipDistances.length;i++) clipDistances[i]=glIsEnabled(GL_CLIP_DISTANCE0+i);spend(clipDistances.length); }
        sampleCoverage=glGetFloat(GL_SAMPLE_COVERAGE_VALUE);sampleInvert=glGetBoolean(GL_SAMPLE_COVERAGE_INVERT);spend(2);
        sampleMasks=captureDropped ? new int[maxSampleMaskWords()] : null;
        if(sampleMasks!=null) { for(int i=0;i<sampleMasks.length;i++) sampleMasks[i]=glGetIntegeri(GL_SAMPLE_MASK_VALUE,i);spend(sampleMasks.length); }
        program=glGetInteger(GL_CURRENT_PROGRAM);vao=glGetInteger(GL_VERTEX_ARRAY_BINDING);elementBuffer=glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);spend(3);
        hasPipeline=limits != null ? limits.hasPipeline : (capabilities.OpenGL41 || capabilities.GL_ARB_separate_shader_objects);
        pipeline=hasPipeline ? glGetInteger(org.lwjgl.opengl.GL41C.GL_PROGRAM_PIPELINE_BINDING) : 0;
        if(hasPipeline) spend(1);
        buffers=new int[BUFFER_BINDINGS.length];for(int i=0;i<buffers.length;i++) buffers[i]=glGetInteger(BUFFER_BINDINGS[i]);spend(buffers.length);
        pixelStores=new int[PIXEL_STORES.length];for(int i=0;i<pixelStores.length;i++) pixelStores[i]=glGetInteger(PIXEL_STORES[i]);spend(pixelStores.length);
        if(captureDropped) {
            uniformBuffers=new int[maxUniformBufferBindings()];uniformOffsets=new long[uniformBuffers.length];uniformSizes=new long[uniformBuffers.length];
            for(int i=0;i<uniformBuffers.length;i++) {
                uniformBuffers[i]=glGetIntegeri(GL_UNIFORM_BUFFER_BINDING,i);
                uniformOffsets[i]=glGetInteger64i(GL_UNIFORM_BUFFER_START,i);uniformSizes[i]=glGetInteger64i(GL_UNIFORM_BUFFER_SIZE,i);
            }
            spend(uniformBuffers.length*3);
        } else { uniformBuffers=null;uniformOffsets=null;uniformSizes=null; }
        activeTexture=glGetInteger(GL_ACTIVE_TEXTURE);spend(1);
        hasSamplers=limits != null ? limits.hasSamplers : (capabilities.OpenGL33 || capabilities.GL_ARB_sampler_objects);
        SkiaTextures profile=skia ? skiaTextures(context,capabilities) : null;
        textureTargets=profile!=null ? profile.targets : TEXTURE_TARGETS;
        textureBindingNames=profile!=null ? profile.bindings : TEXTURE_BINDINGS;
        int unitCount=profile!=null ? profile.units : maxCombinedTextureImageUnits();
        int[] tierUnits=scanUnits(unitCount,slim);
        // Verify mode captures every unit so a skipped unit cannot leak, and
        // then reports the ones the configured tier would have missed.
        textureUnits=verifySlim ? allUnits(unitCount) : tierUnits;
        unwatchedUnits=verifySlim ? complement(unitCount,tierUnits) : new int[0];
        textures=new int[textureUnits.length][textureBindingNames.length];samplers=new int[textureUnits.length];
        try {
            for(int pos=0;pos<textureUnits.length;pos++) {
                glActiveTexture(GL_TEXTURE0+textureUnits[pos]);
                for(int i=0;i<textureBindingNames.length;i++) textures[pos][i]=glGetInteger(textureBindingNames[i]);
                if(hasSamplers) samplers[pos]=glGetIntegeri(GL_SAMPLER_BINDING,textureUnits[pos]);
                spend(1+textureBindingNames.length+(hasSamplers?1:0));
            }
        } finally { glActiveTexture(activeTexture);spend(1); }
        indexedUniform=indexedStateIsUniform();
        captureCalls=calls;calls=0;
        lastCaptureCalls=captureCalls;TOTAL_CAPTURE_CALLS.addAndGet(captureCalls);
        if(logCalls) System.out.println("[AtomLib] GlStateScope capture slim="+slim+" calls="+captureCalls);
        SCOPES.get().push(this);
    }

    /**
     * True when restoring the global blend/color-mask state is equivalent to
     * restoring each draw buffer: the per-buffer values equal the global ones.
     * Only then may {@link #close()} skip the indexed writes.
     */
    private boolean indexedStateIsUniform() {
        int[] globalMask = colorMask;
        int[] globalBlend = blend;
        for(int i=0;i<drawBlendEnabled.length;i++) {
            if(drawBlendEnabled[i] != enables[0]) return false;
            int[] mask = drawColorMasks[i];
            for(int c=0;c<4;c++) if((mask[c]!=0?1:0) != globalMask[c]) return false;
            if(hasIndexedBlend) {
                int[] f = drawBlendFunctions[i];
                for(int c=0;c<6;c++) if(f[c] != globalBlend[c]) return false;
            }
        }
        return true;
    }

    public static GlStateScope capture() {
        return capture(false);
    }

    /** Only for the bridge's Skia-only drawing path; arbitrary host GL uses capture(). */
    static GlStateScope captureSkia() { return capture(true); }

    private static GlStateScope capture(boolean skia) {
        if(!GlScopeDiagnostics.enabled()) return new GlStateScope(skia);
        GlScopeDiagnostics event=new GlScopeDiagnostics();
        event.depth=SCOPES.get().size();
        event.startedAtNanos=System.nanoTime();
        try {
            GlStateScope scope=new GlStateScope(skia);
            event.textureUnits=scope.textures.length;
            event.textureTargets=scope.textureTargets.length;
            event.nativeCalls=scope.captureCalls;
            event.slim=scope.slim;
            event.success=true;
            return scope;
        } finally {
            event.captureNanos=System.nanoTime()-event.startedAtNanos;
            event.commit();
        }
    }

    private static int[] ints(int name,int size) { int[] a=new int[size];glGetIntegerv(name,a);return a; }
    private static float[] floats(int name,int size) { float[] a=new float[size];glGetFloatv(name,a);return a; }
    private static int[] stencil(boolean back) {
        return back ? new int[]{glGetInteger(GL_STENCIL_BACK_FUNC),glGetInteger(GL_STENCIL_BACK_REF),glGetInteger(GL_STENCIL_BACK_VALUE_MASK),glGetInteger(GL_STENCIL_BACK_WRITEMASK),glGetInteger(GL_STENCIL_BACK_FAIL),glGetInteger(GL_STENCIL_BACK_PASS_DEPTH_FAIL),glGetInteger(GL_STENCIL_BACK_PASS_DEPTH_PASS)}
            : new int[]{glGetInteger(GL_STENCIL_FUNC),glGetInteger(GL_STENCIL_REF),glGetInteger(GL_STENCIL_VALUE_MASK),glGetInteger(GL_STENCIL_WRITEMASK),glGetInteger(GL_STENCIL_FAIL),glGetInteger(GL_STENCIL_PASS_DEPTH_FAIL),glGetInteger(GL_STENCIL_PASS_DEPTH_PASS)};
    }
    private static void stencil(int face,int[] state) {
        glStencilFuncSeparate(face,state[0],state[1],state[2]);glStencilMaskSeparate(face,state[3]);glStencilOpSeparate(face,state[4],state[5],state[6]);
    }
    private static void enabled(int cap,boolean value) { if(value) glEnable(cap);else glDisable(cap); }

    @Override public void close() {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("GL scope belongs to another thread");
        if(closed) return;
        if(glfwGetCurrentContext()!=context || GL.getCapabilities()!=capabilities) throw new IllegalStateException("GL scope belongs to another context");
        Deque<GlStateScope> stack=SCOPES.get();
        if(stack.peek()!=this) throw new IllegalStateException("GL scopes must close in reverse order");
        if(verifySlim) verifyDroppedFamilies();
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,drawFbo);spend(1);
        if(drawFbo==0) glDrawBuffer(drawBuffers[0]);else glDrawBuffers(drawBuffers);spend(1);
        glBindFramebuffer(GL_READ_FRAMEBUFFER,readFbo);glReadBuffer(readBuffer);spend(2);
        glBindRenderbuffer(GL_RENDERBUFFER,renderbuffer);spend(1);
        glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);spend(2);
        glColorMask(colorMask[0]!=0,colorMask[1]!=0,colorMask[2]!=0,colorMask[3]!=0);spend(1);
        glBlendFuncSeparate(blend[0],blend[1],blend[2],blend[3]);glBlendEquationSeparate(blend[4],blend[5]);glBlendColor(blendColor[0],blendColor[1],blendColor[2],blendColor[3]);spend(3);
        glDepthMask(depthMask);glDepthFunc(depthFunc);glDepthRange(depthRange[0],depthRange[1]);spend(3);
        glCullFace(cullFace);glFrontFace(frontFace);glPolygonMode(GL_FRONT_AND_BACK,polygonMode);glPolygonOffset(polygonOffsetFactor,polygonOffsetUnits);glLineWidth(lineWidth);spend(5);
        glClearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);glClearDepth(clearDepth);glClearStencil(clearStencil);spend(3);
        stencil(GL_FRONT,stencilFront);stencil(GL_BACK,stencilBack);spend(6);
        glPrimitiveRestartIndex(primitiveRestart);spend(1);
        for(int i=0;i<ENABLES.length;i++) enabled(ENABLES[i],enables[i]);spend(ENABLES.length);
        // Global glEnable/glColorMask/glBlendFunc update every draw buffer.
        // Restore the independent indexed values after the global state — but
        // only when the host actually had per-buffer differences.
        if(!indexedUniform) {
            for(int i=0;i<drawBlendEnabled.length;i++) {
                if(drawBlendEnabled[i]) glEnablei(GL_BLEND,i);else glDisablei(GL_BLEND,i);
                int[] mask=drawColorMasks[i];glColorMaski(i,mask[0]!=0,mask[1]!=0,mask[2]!=0,mask[3]!=0);
                spend(2);
                if(hasIndexedBlend) {
                    int[] b=drawBlendFunctions[i];
                    if(capabilities.OpenGL40) {
                        org.lwjgl.opengl.GL40C.glBlendFuncSeparatei(i,b[0],b[1],b[2],b[3]);org.lwjgl.opengl.GL40C.glBlendEquationSeparatei(i,b[4],b[5]);
                    } else {
                        org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(i,b[0],b[1],b[2],b[3]);org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendEquationSeparateiARB(i,b[4],b[5]);
                    }
                    spend(2);
                }
            }
        }
        if(clipDistances!=null) { for(int i=0;i<clipDistances.length;i++) enabled(GL_CLIP_DISTANCE0+i,clipDistances[i]);spend(clipDistances.length); }
        glSampleCoverage(sampleCoverage,sampleInvert);spend(1);
        if(sampleMasks!=null) { for(int i=0;i<sampleMasks.length;i++) glSampleMaski(i,sampleMasks[i]);spend(sampleMasks.length); }
        if(hasPipeline) { org.lwjgl.opengl.GL41C.glBindProgramPipeline(pipeline);spend(1); }
        glUseProgram(program);glBindVertexArray(vao);spend(2);
        // The element binding is VAO state. VAO 0 cannot be modified in core GL.
        if(vao!=0) { glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,elementBuffer);spend(1); }
        if(uniformBuffers!=null) {
            for(int i=0;i<uniformBuffers.length;i++) {
                if(uniformBuffers[i]!=0 && uniformSizes[i]>0) glBindBufferRange(GL_UNIFORM_BUFFER,i,uniformBuffers[i],uniformOffsets[i],uniformSizes[i]);
                else glBindBufferBase(GL_UNIFORM_BUFFER,i,uniformBuffers[i]);
            }
            spend(uniformBuffers.length);
        }
        for(int i=0;i<buffers.length;i++) glBindBuffer(BUFFER_TARGETS[i],buffers[i]);spend(buffers.length);
        for(int i=0;i<pixelStores.length;i++) glPixelStorei(PIXEL_STORES[i],pixelStores[i]);spend(pixelStores.length);
        for(int pos=0;pos<textureUnits.length;pos++) {
            glActiveTexture(GL_TEXTURE0+textureUnits[pos]);
            for(int i=0;i<textureTargets.length;i++) glBindTexture(textureTargets[i],textures[pos][i]);
            if(hasSamplers) glBindSampler(textureUnits[pos],samplers[pos]);
            spend(1+textureTargets.length+(hasSamplers?1:0));
        }
        glActiveTexture(activeTexture);spend(1);
        closed=true;stack.pop();if(stack.isEmpty()) SCOPES.remove();
        restoreCalls=calls;calls=0;
        lastRestoreCalls=restoreCalls;TOTAL_RESTORE_CALLS.addAndGet(restoreCalls);
        if(logCalls) System.out.println("[AtomLib] GlStateScope restore slim="+slim+" calls="+restoreCalls);
    }

    /**
     * Debug-only: read the slim-excluded families and report any value the pass
     * changed. Runs before the (verify-mode) restore, so the report reflects the
     * real leak a production slim scope would have caused.
     */
    private void verifyDroppedFamilies() {
        StringBuilder bad=new StringBuilder();
        if(uniformBuffers!=null) {
            for(int i=0;i<uniformBuffers.length;i++) {
                int binding=glGetIntegeri(GL_UNIFORM_BUFFER_BINDING,i);
                if(binding!=uniformBuffers[i]) bad.append("uboBinding[").append(i).append("]=").append(binding).append(" was ").append(uniformBuffers[i]).append(' ');
            }
            spend(uniformBuffers.length);
        }
        if(clipDistances!=null) {
            for(int i=0;i<clipDistances.length;i++) {
                boolean value=glIsEnabled(GL_CLIP_DISTANCE0+i);
                if(value!=clipDistances[i]) bad.append("clipDistance[").append(i).append("]=").append(value).append(' ');
            }
            spend(clipDistances.length);
        }
        if(sampleMasks!=null) {
            for(int i=0;i<sampleMasks.length;i++) {
                int value=glGetIntegeri(GL_SAMPLE_MASK_VALUE,i);
                if(value!=sampleMasks[i]) bad.append("sampleMask[").append(i).append("]=").append(value).append(' ');
            }
            spend(sampleMasks.length);
        }
        if(verifySlim && unwatchedUnits.length>0) {
            // Verify mode captured these units too (textureUnits is the identity
            // order there), so compare against the tier's blind spots before the
            // restore puts them back — a real leak is reported, not hidden.
            for(int unit:unwatchedUnits) {
                glActiveTexture(GL_TEXTURE0+unit);
                for(int i=0;i<textureBindingNames.length;i++) {
                    int value=glGetInteger(textureBindingNames[i]);
                    if(value!=textures[unit][i]) bad.append("texture[").append(unit).append("][").append(i).append("]=").append(value).append(' ');
                }
                if(hasSamplers) {
                    int value=glGetIntegeri(GL_SAMPLER_BINDING,unit);
                    if(value!=samplers[unit]) bad.append("sampler[").append(unit).append("]=").append(value).append(' ');
                }
                spend(textureBindingNames.length+(hasSamplers?1:0));
            }
            glActiveTexture(activeTexture);spend(1);
        }
        lastSlimViolation=bad.isEmpty() ? null : "slim snapshot missed writes: "+bad;
        if(lastSlimViolation!=null) System.err.println("[AtomLib] "+lastSlimViolation);
    }
}
