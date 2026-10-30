// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.RuntimeHandle;
import com.atom.lib.render.CustomPainter;
import com.atom.lib.render.RenderSession;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.ContentChangeMode;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;
import io.github.humbleui.skija.Image;
import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Owns one Skija DirectContext on one render thread and one GLFW context. The
 * framebuffer and its Canvas are borrowed; the host retains attachment ownership.
 */
public final class SkijaRenderBridge implements AutoCloseable {
    private static final Map<Long,SkijaRenderBridge> OWNERS = new HashMap<>();
    private static final ThreadLocal<Boolean> PAINTING = ThreadLocal.withInitial(() -> false);
    private final RuntimeHandle runtime;
    private final Thread thread = Thread.currentThread();
    private DirectContext context;
    private GLCapabilities capabilities;
    private long window;
    private BackendRenderTarget backend;
    private Surface surface;
    private RenderSession session;
    private FramebufferTarget current;
    private boolean invalidated, closed;

    public SkijaRenderBridge(RuntimeHandle runtime) {
        this.runtime=Objects.requireNonNull(runtime);
        runtime.requireOpen();
    }

    /** Borrow one frame synchronously, with the same save/flush/state guard as drawing. */
    public <T> T withFrameAccess(FramebufferTarget target,Function<BorrowedFrame,T> action) {
        Objects.requireNonNull(action);
        AtomicReference<T> result=new AtomicReference<>();
        drawFrame(target,(canvas,frame) -> result.set(action.apply(frame)));
        return result.get();
    }
    /**
     * Run a host-side operation that needs the bridge-owned DirectContext while
     * no Skija pass is active. The reference is valid only for this callback:
     * hosts may synchronously prepare or borrow GL resources here, but must
     * not retain a second renderer owner or cross GL contexts.
     */
    public <T> T withContext(Function<DirectContext,T> action) {
        Objects.requireNonNull(action);
        requireThreadAndOpen();runtime.requireOpen();requireOutsidePass();requireContext();
        try(GlStateScope ignored=GlStateScope.capture()) {
            normalizePixelTransfer();
            ensureContext();
            return action.apply(context);
        }
    }
    /** Ensure target identity and size are current; does not transfer any native handle. */
    public void prepare(FramebufferTarget target) { withFrameAccess(target,frame -> null); }
    /** Returned Image is owned by the caller, who must close it on the render thread. */
    public Image captureFrame(FramebufferTarget target) {
        return withFrameAccess(target,frame -> frame.surface().makeImageSnapshot());
    }

    public void draw(FramebufferTarget target, CustomPainter painter) {
        Objects.requireNonNull(painter);
        drawFrame(target,(canvas,frame) -> painter.paint(canvas));
    }

    public void drawFrame(FramebufferTarget target,BorrowedFramePainter painter) {
        Objects.requireNonNull(target);Objects.requireNonNull(painter);
        requireThreadAndOpen();runtime.requireOpen();requireOutsidePass();
        requireContext();
        PAINTING.set(true);
        try(GlStateScope ignored=GlStateScope.capture()) {
            normalizePixelTransfer();
            ensureContext();
            context.resetGLAll();
            if(invalidated || !sameTarget(target,current)) rebuild(target);
            surface.notifyContentWillChange(ContentChangeMode.RETAIN);
            Throwable failure=null;
            try {
                session.paint(canvas -> {
                    BorrowedFrame frame=new BorrowedFrame(context,backend,surface);
                    try { painter.paint(canvas,frame); }
                    finally { frame.invalidate(); }
                });
            } catch(RuntimeException | Error e) {
                failure=e;throw e;
            } finally {
                try { context.flushAndSubmit(surface); }
                catch(RuntimeException | Error flushFailure) {
                    if(failure!=null) failure.addSuppressed(flushFailure);else throw flushFailure;
                }
            }
        } finally { PAINTING.remove(); }
    }

    /** Run host drawing only between fully flushed Skija passes. */
    public void vanillaPass(Runnable action) {
        Objects.requireNonNull(action);requireThreadAndOpen();requireOutsidePass();
        if(context!=null) requireContext();
        action.run();
    }

    /** Host calls this after reusing GL object names for a new attachment generation. */
    public void invalidate() {
        requireThreadAndOpen();requireOutsidePass();invalidated=true;
    }

    private void requireThreadAndOpen() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("Bridge belongs to the render thread that created it");
        if(closed) throw new IllegalStateException("Bridge is closed");
    }
    private static void requireOutsidePass() {
        if(PAINTING.get()) throw new IllegalStateException("Recursive Skija pass, vanilla drawing or lifecycle change inside a Skija pass");
    }
    private void requireContext() {
        long now=glfwGetCurrentContext();
        if(now==0) throw new IllegalStateException("Bridge requires a current GLFW OpenGL context");
        if(window!=0 && (now!=window || GL.getCapabilities()!=capabilities)) throw new IllegalStateException("Bridge belongs to another GL context; close it before destroying that context");
    }
    private void ensureContext() {
        if(context!=null) return;
        long now=glfwGetCurrentContext();
        synchronized(OWNERS) {
            SkijaRenderBridge other=OWNERS.get(now);
            if(other!=null && other!=this) throw new IllegalStateException("This GL context already has an AtomLib bridge");
            OWNERS.put(now,this);
        }
        try {
            context=DirectContext.makeGL();window=now;capabilities=GL.getCapabilities();
        } catch(RuntimeException | Error e) {
            synchronized(OWNERS) { OWNERS.remove(now,this); }
            throw e;
        }
    }
    private static boolean sameTarget(FramebufferTarget a,FramebufferTarget b) {
        return b!=null && a.identity()==b.identity() && a.framebuffer()==b.framebuffer() && a.colorTexture()==b.colorTexture() && a.width()==b.width() && a.height()==b.height();
    }
    private void rebuild(FramebufferTarget target) {
        glBindFramebuffer(GL_FRAMEBUFFER,target.framebuffer());
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new IllegalArgumentException("Host framebuffer is incomplete");
        if(glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)!=GL_TEXTURE ||
            glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)!=target.colorTexture()) {
            throw new IllegalArgumentException("Framebuffer color attachment identity changed");
        }
        glBindTexture(GL_TEXTURE_2D,target.colorTexture());
        if(glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_INTERNAL_FORMAT)!=GL_RGBA8 ||
            glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH)!=target.width() ||
            glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT)!=target.height()) {
            throw new IllegalArgumentException("Initial bridge requires an RGBA8 2D color attachment with the reported physical size");
        }
        int samples=glGetInteger(GL_SAMPLES);
        int stencil=glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,GL_STENCIL_ATTACHMENT,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL_NONE ? 0
            : glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,GL_STENCIL_ATTACHMENT,GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE);
        dropSurface();
        BackendRenderTarget nextBackend=BackendRenderTarget.makeGL(target.width(),target.height(),samples,stencil,target.framebuffer(),GL_RGBA8);
        try {
            Surface next=Surface.wrapBackendRenderTarget(context,nextBackend,SurfaceOrigin.BOTTOM_LEFT,ColorType.RGBA_8888,ColorSpace.getSRGB());
            backend=nextBackend;surface=next;session=new RenderSession(runtime,next.getCanvas());current=target;invalidated=false;
        } catch(RuntimeException | Error e) { nextBackend.close();throw e; }
    }
    private static void normalizePixelTransfer() {
        // A host PBO changes JNI pixel pointers into offsets. Isolate both
        // directions before Skija initializes/uploads/reads any resources.
        glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
        for(int p:new int[]{GL_PACK_ROW_LENGTH,GL_PACK_IMAGE_HEIGHT,GL_PACK_SKIP_PIXELS,GL_PACK_SKIP_ROWS,GL_PACK_SKIP_IMAGES,GL_PACK_SWAP_BYTES,GL_PACK_LSB_FIRST,
            GL_UNPACK_ROW_LENGTH,GL_UNPACK_IMAGE_HEIGHT,GL_UNPACK_SKIP_PIXELS,GL_UNPACK_SKIP_ROWS,GL_UNPACK_SKIP_IMAGES,GL_UNPACK_SWAP_BYTES,GL_UNPACK_LSB_FIRST}) glPixelStorei(p,0);
        glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_UNPACK_ALIGNMENT,1);
    }
    private void dropSurface() {
        try { if(session!=null) session.close(); }
        finally {
            session=null;
            try { if(surface!=null) surface.close(); }
            finally {
                surface=null;
                try { if(backend!=null) backend.close(); }
                finally { backend=null;current=null; }
            }
        }
    }
    /** Must run before the host destroys its current GL context. Never closes the runtime. */
    @Override public void close() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("Bridge close must run on its render thread");
        if(closed) return;
        requireOutsidePass();
        if(context==null) { closed=true;return; }
        requireContext();
        try(GlStateScope ignored=GlStateScope.capture()) {
            normalizePixelTransfer();
            try { context.flushAndSubmit(false); }
            finally {
                try { dropSurface(); }
                finally {
                    try { context.close(); }
                    finally {
                        context=null;closed=true;
                        synchronized(OWNERS) { OWNERS.remove(window,this); }
                    }
                }
            }
        }
    }
}
