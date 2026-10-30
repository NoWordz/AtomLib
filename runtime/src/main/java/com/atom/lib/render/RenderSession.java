// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.render;

import com.atom.lib.RuntimeHandle;
import io.github.humbleui.skija.Canvas;
import java.util.Objects;

/** Thread-confined CPU/host-supplied Canvas scope; does not implement an MC GL bridge. */
public final class RenderSession implements AutoCloseable {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private final RuntimeHandle runtime;
    private final Canvas canvas;
    private final Thread thread = Thread.currentThread();
    private volatile boolean closed;
    public RenderSession(RuntimeHandle runtime, Canvas canvas) {
        this.runtime = Objects.requireNonNull(runtime); this.canvas = Objects.requireNonNull(canvas);
        runtime.requireOpen();
    }
    public void paint(CustomPainter painter) {
        Objects.requireNonNull(painter); runtime.requireOpen();
        if (closed) throw new IllegalStateException("Session closed");
        if (Thread.currentThread() != thread) throw new IllegalStateException("Render session belongs to another thread");
        if (ACTIVE.get()) throw new IllegalStateException("Recursive Skija pass");
        int saved = canvas.getSaveCount();
        ACTIVE.set(true);
        try {
            canvas.save();
            painter.paint(canvas);
            if (canvas.getSaveCount() <= saved) throw new IllegalStateException("Painter over-restored Canvas");
        } finally {
            try { canvas.restoreToCount(saved); }
            finally { ACTIVE.remove(); }
        }
    }
    @Override public void close() { closed = true; }
}
