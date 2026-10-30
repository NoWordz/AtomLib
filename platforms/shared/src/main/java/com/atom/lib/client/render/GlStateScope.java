// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import java.util.ArrayDeque;
import java.util.Deque;
import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.opengl.GL33C.*;

/**
 * A paired snapshot of core GL driver state. Uses no Blaze3D state mutator or
 * cache invalidation. Host object contents/lifetimes must not change in scope.
 */
public final class GlStateScope implements AutoCloseable {
    private static final ThreadLocal<Deque<GlStateScope>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final int[] ENABLES = {
        GL_BLEND, GL_CULL_FACE, GL_DEPTH_TEST, GL_SCISSOR_TEST, GL_STENCIL_TEST,
        GL_DITHER, GL_MULTISAMPLE, GL_FRAMEBUFFER_SRGB, GL_RASTERIZER_DISCARD,
        GL_POLYGON_OFFSET_FILL, GL_SAMPLE_ALPHA_TO_COVERAGE, GL_SAMPLE_ALPHA_TO_ONE,
        GL_SAMPLE_COVERAGE, GL_SAMPLE_MASK, GL_PRIMITIVE_RESTART, GL_DEPTH_CLAMP,
        GL_TEXTURE_CUBE_MAP_SEAMLESS
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
    private final int drawFbo, readFbo, readBuffer, renderbuffer, program, vao, elementBuffer, activeTexture;
    private final int[] viewport, scissor, colorMask, blend, stencilFront, stencilBack, drawBuffers;
    private final int[] buffers, pixelStores, sampleMasks, uniformBuffers;
    private final long[] uniformOffsets, uniformSizes;
    private final boolean[] enables, clipDistances, drawBlendEnabled;
    private final int[][] drawColorMasks, drawBlendFunctions;
    private final int[][] textures;
    private final int[] samplers;
    private final boolean hasSamplers, hasPipeline, hasIndexedBlend;
    private final int pipeline;
    private final float[] blendColor, clearColor;
    private final double[] depthRange;
    private final boolean depthMask, sampleInvert;
    private final int depthFunc, cullFace, frontFace, polygonMode, clearStencil, primitiveRestart;
    private final float polygonOffsetFactor, polygonOffsetUnits, lineWidth, sampleCoverage;
    private final double clearDepth;
    private boolean closed;

    private GlStateScope() {
        if (context == 0 || !capabilities.OpenGL32) throw new IllegalStateException("A current OpenGL 3.2+ context is required");
        drawFbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);readFbo=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        readBuffer=glGetInteger(GL_READ_BUFFER);renderbuffer=glGetInteger(GL_RENDERBUFFER_BINDING);
        drawBuffers=new int[drawFbo == 0 ? 1 : glGetInteger(GL_MAX_DRAW_BUFFERS)];
        for(int i=0;i<drawBuffers.length;i++) drawBuffers[i]=glGetInteger(GL_DRAW_BUFFER0+i);
        viewport=ints(GL_VIEWPORT,4);scissor=ints(GL_SCISSOR_BOX,4);colorMask=ints(GL_COLOR_WRITEMASK,4);
        blend=new int[]{glGetInteger(GL_BLEND_SRC_RGB),glGetInteger(GL_BLEND_DST_RGB),glGetInteger(GL_BLEND_SRC_ALPHA),glGetInteger(GL_BLEND_DST_ALPHA),glGetInteger(GL_BLEND_EQUATION_RGB),glGetInteger(GL_BLEND_EQUATION_ALPHA)};
        blendColor=floats(org.lwjgl.opengl.ARBImaging.GL_BLEND_COLOR,4);clearColor=floats(GL_COLOR_CLEAR_VALUE,4);
        int drawCount=glGetInteger(GL_MAX_DRAW_BUFFERS);
        drawBlendEnabled=new boolean[drawCount];drawColorMasks=new int[drawCount][4];
        hasIndexedBlend=capabilities.OpenGL40 || capabilities.GL_ARB_draw_buffers_blend;
        drawBlendFunctions=hasIndexedBlend ? new int[drawCount][6] : null;
        int[] blendNames={GL_BLEND_SRC_RGB,GL_BLEND_DST_RGB,GL_BLEND_SRC_ALPHA,GL_BLEND_DST_ALPHA,GL_BLEND_EQUATION_RGB,GL_BLEND_EQUATION_ALPHA};
        for(int i=0;i<drawCount;i++) {
            drawBlendEnabled[i]=glIsEnabledi(GL_BLEND,i);glGetIntegeri_v(GL_COLOR_WRITEMASK,i,drawColorMasks[i]);
            if(hasIndexedBlend) for(int j=0;j<blendNames.length;j++) drawBlendFunctions[i][j]=glGetIntegeri(blendNames[j],i);
        }
        depthRange=new double[2];glGetDoublev(GL_DEPTH_RANGE,depthRange);
        depthMask=glGetBoolean(GL_DEPTH_WRITEMASK);depthFunc=glGetInteger(GL_DEPTH_FUNC);clearDepth=glGetDouble(GL_DEPTH_CLEAR_VALUE);
        cullFace=glGetInteger(GL_CULL_FACE_MODE);frontFace=glGetInteger(GL_FRONT_FACE);polygonMode=ints(GL_POLYGON_MODE,2)[0];
        polygonOffsetFactor=glGetFloat(GL_POLYGON_OFFSET_FACTOR);polygonOffsetUnits=glGetFloat(GL_POLYGON_OFFSET_UNITS);lineWidth=glGetFloat(GL_LINE_WIDTH);
        clearStencil=glGetInteger(GL_STENCIL_CLEAR_VALUE);primitiveRestart=glGetInteger(GL_PRIMITIVE_RESTART_INDEX);
        stencilFront=stencil(false);stencilBack=stencil(true);
        enables=new boolean[ENABLES.length];for(int i=0;i<ENABLES.length;i++) enables[i]=glIsEnabled(ENABLES[i]);
        clipDistances=new boolean[glGetInteger(GL_MAX_CLIP_DISTANCES)];for(int i=0;i<clipDistances.length;i++) clipDistances[i]=glIsEnabled(GL_CLIP_DISTANCE0+i);
        sampleCoverage=glGetFloat(GL_SAMPLE_COVERAGE_VALUE);sampleInvert=glGetBoolean(GL_SAMPLE_COVERAGE_INVERT);
        sampleMasks=new int[glGetInteger(GL_MAX_SAMPLE_MASK_WORDS)];for(int i=0;i<sampleMasks.length;i++) sampleMasks[i]=glGetIntegeri(GL_SAMPLE_MASK_VALUE,i);
        program=glGetInteger(GL_CURRENT_PROGRAM);vao=glGetInteger(GL_VERTEX_ARRAY_BINDING);elementBuffer=glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        hasPipeline=capabilities.OpenGL41 || capabilities.GL_ARB_separate_shader_objects;
        pipeline=hasPipeline ? glGetInteger(org.lwjgl.opengl.GL41C.GL_PROGRAM_PIPELINE_BINDING) : 0;
        buffers=new int[BUFFER_BINDINGS.length];for(int i=0;i<buffers.length;i++) buffers[i]=glGetInteger(BUFFER_BINDINGS[i]);
        pixelStores=new int[PIXEL_STORES.length];for(int i=0;i<pixelStores.length;i++) pixelStores[i]=glGetInteger(PIXEL_STORES[i]);
        uniformBuffers=new int[glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS)];uniformOffsets=new long[uniformBuffers.length];uniformSizes=new long[uniformBuffers.length];
        for(int i=0;i<uniformBuffers.length;i++) {
            uniformBuffers[i]=glGetIntegeri(GL_UNIFORM_BUFFER_BINDING,i);
            uniformOffsets[i]=glGetInteger64i(GL_UNIFORM_BUFFER_START,i);uniformSizes[i]=glGetInteger64i(GL_UNIFORM_BUFFER_SIZE,i);
        }
        activeTexture=glGetInteger(GL_ACTIVE_TEXTURE);
        hasSamplers=capabilities.OpenGL33 || capabilities.GL_ARB_sampler_objects;
        textures=new int[glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)][TEXTURE_BINDINGS.length];samplers=new int[textures.length];
        try {
            for(int unit=0;unit<textures.length;unit++) {
                glActiveTexture(GL_TEXTURE0+unit);
                for(int i=0;i<TEXTURE_BINDINGS.length;i++) textures[unit][i]=glGetInteger(TEXTURE_BINDINGS[i]);
                if(hasSamplers) samplers[unit]=glGetIntegeri(GL_SAMPLER_BINDING,unit);
            }
        } finally { glActiveTexture(activeTexture); }
        SCOPES.get().push(this);
    }

    public static GlStateScope capture() { return new GlStateScope(); }

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
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,drawFbo);
        if(drawFbo==0) glDrawBuffer(drawBuffers[0]);else glDrawBuffers(drawBuffers);
        glBindFramebuffer(GL_READ_FRAMEBUFFER,readFbo);glReadBuffer(readBuffer);
        glBindRenderbuffer(GL_RENDERBUFFER,renderbuffer);
        glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);
        glColorMask(colorMask[0]!=0,colorMask[1]!=0,colorMask[2]!=0,colorMask[3]!=0);
        glBlendFuncSeparate(blend[0],blend[1],blend[2],blend[3]);glBlendEquationSeparate(blend[4],blend[5]);glBlendColor(blendColor[0],blendColor[1],blendColor[2],blendColor[3]);
        glDepthMask(depthMask);glDepthFunc(depthFunc);glDepthRange(depthRange[0],depthRange[1]);
        glCullFace(cullFace);glFrontFace(frontFace);glPolygonMode(GL_FRONT_AND_BACK,polygonMode);glPolygonOffset(polygonOffsetFactor,polygonOffsetUnits);glLineWidth(lineWidth);
        glClearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);glClearDepth(clearDepth);glClearStencil(clearStencil);
        stencil(GL_FRONT,stencilFront);stencil(GL_BACK,stencilBack);glPrimitiveRestartIndex(primitiveRestart);
        for(int i=0;i<ENABLES.length;i++) enabled(ENABLES[i],enables[i]);
        // Global glEnable/glColorMask/glBlendFunc update every draw buffer.
        // Restore the independent indexed values after the global state.
        for(int i=0;i<drawBlendEnabled.length;i++) {
            if(drawBlendEnabled[i]) glEnablei(GL_BLEND,i);else glDisablei(GL_BLEND,i);
            int[] mask=drawColorMasks[i];glColorMaski(i,mask[0]!=0,mask[1]!=0,mask[2]!=0,mask[3]!=0);
            if(hasIndexedBlend) {
                int[] b=drawBlendFunctions[i];
                if(capabilities.OpenGL40) {
                    org.lwjgl.opengl.GL40C.glBlendFuncSeparatei(i,b[0],b[1],b[2],b[3]);org.lwjgl.opengl.GL40C.glBlendEquationSeparatei(i,b[4],b[5]);
                } else {
                    org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(i,b[0],b[1],b[2],b[3]);org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendEquationSeparateiARB(i,b[4],b[5]);
                }
            }
        }
        for(int i=0;i<clipDistances.length;i++) enabled(GL_CLIP_DISTANCE0+i,clipDistances[i]);
        glSampleCoverage(sampleCoverage,sampleInvert);for(int i=0;i<sampleMasks.length;i++) glSampleMaski(i,sampleMasks[i]);
        if(hasPipeline) org.lwjgl.opengl.GL41C.glBindProgramPipeline(pipeline);
        glUseProgram(program);glBindVertexArray(vao);
        // The element binding is VAO state. VAO 0 cannot be modified in core GL.
        if(vao!=0) glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,elementBuffer);
        for(int i=0;i<uniformBuffers.length;i++) {
            if(uniformBuffers[i]!=0 && uniformSizes[i]>0) glBindBufferRange(GL_UNIFORM_BUFFER,i,uniformBuffers[i],uniformOffsets[i],uniformSizes[i]);
            else glBindBufferBase(GL_UNIFORM_BUFFER,i,uniformBuffers[i]);
        }
        for(int i=0;i<buffers.length;i++) glBindBuffer(BUFFER_TARGETS[i],buffers[i]);
        for(int i=0;i<pixelStores.length;i++) glPixelStorei(PIXEL_STORES[i],pixelStores[i]);
        for(int unit=0;unit<textures.length;unit++) {
            glActiveTexture(GL_TEXTURE0+unit);
            for(int i=0;i<TEXTURE_TARGETS.length;i++) glBindTexture(TEXTURE_TARGETS[i],textures[unit][i]);
            if(hasSamplers) glBindSampler(unit,samplers[unit]);
        }
        glActiveTexture(activeTexture);
        closed=true;stack.pop();if(stack.isEmpty()) SCOPES.remove();
    }
}
