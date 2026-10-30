// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Surface;

/**
 * Synchronous borrowed native handles, valid only in the bridge callback.
 * Never close, retain, abandon or reconfigure these objects. The bridge owns
 * their lifecycle. Images explicitly made from the Surface are caller-owned.
 */
public final class BorrowedFrame {
    private final Thread thread=Thread.currentThread();
    private final long window=org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
    private final org.lwjgl.opengl.GLCapabilities capabilities=org.lwjgl.opengl.GL.getCapabilities();
    private final DirectContext context;
    private final BackendRenderTarget backend;
    private final Surface surface;
    private boolean valid=true;

    BorrowedFrame(DirectContext context,BackendRenderTarget backend,Surface surface) {
        this.context=context;this.backend=backend;this.surface=surface;
    }
    public DirectContext context() { requireActive();return context; }
    public BackendRenderTarget backendRenderTarget() { requireActive();return backend; }
    public Surface surface() { requireActive();return surface; }
    private void requireActive() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("Borrowed frame belongs to its render thread");
        if(!valid) throw new IllegalStateException("Borrowed frame callback has ended");
        if(org.lwjgl.glfw.GLFW.glfwGetCurrentContext()!=window || org.lwjgl.opengl.GL.getCapabilities()!=capabilities) {
            throw new IllegalStateException("Borrowed frame belongs to another GL context");
        }
    }
    void invalidate() { valid=false; }
}
