// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.image;

import com.atom.lib.RuntimeHandle;
import com.atom.lib.geometry.Rect;
import com.atom.lib.resource.ResourceScope;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.svg.SVGDOM;
import java.util.*;

/** Host-local SVG DOMs. Draw is serialized because SVG container size is mutable. */
public final class SvgStore implements AutoCloseable {
    private final RuntimeHandle runtime;
    private final ResourceScope scope;
    private final LinkedHashMap<String, SVGDOM> entries = new LinkedHashMap<>(16, .75f, true);
    private final int capacity;
    public SvgStore(RuntimeHandle runtime) { this(runtime, 128); }
    public SvgStore(RuntimeHandle runtime, int capacity) {
        this.runtime = Objects.requireNonNull(runtime);
        if (capacity < 1) throw new IllegalArgumentException("Invalid capacity");
        runtime.requireOpen(); this.capacity = capacity;
        scope = new ResourceScope(runtime.namespace() + "/svg"); runtime.own(this);
    }
    public synchronized long generation() { return scope.generation(); }
    public synchronized int size() { return entries.size(); }
    public void put(String id, byte[] encoded) {
        if (!complete(id, generation(), encoded)) throw new IllegalStateException("SVG load became stale");
    }
    public boolean complete(String id, long ticket, byte[] encoded) {
        Objects.requireNonNull(id); Objects.requireNonNull(encoded);
        if (id.isBlank()) throw new IllegalArgumentException("Empty asset id");
        synchronized (this) { if (stale(ticket)) return false; }
        SVGDOM decoded;
        try (Data data = Data.makeFromBytes(encoded)) { decoded = new SVGDOM(data); }
        return acceptDecoded(id, ticket, decoded);
    }
    /** Transfers ownership after argument validation, even if stale/closed. Caller must stop using decoded. */
    public synchronized boolean acceptDecoded(String id, long ticket, SVGDOM decoded) {
        Objects.requireNonNull(id); Objects.requireNonNull(decoded);
        if (id.isBlank()) throw new IllegalArgumentException("Empty asset id");
        if (decoded.isClosed()) throw new IllegalArgumentException("Decoded resource is closed");
        synchronized (this) {
            if (runtime.isClosed()) { decoded.close(); return false; }
            if (!scope.accept(ticket, decoded)) return false;
            SVGDOM previous = entries.put(id, decoded);
            if (previous != null) scope.release(previous);
            if (entries.size() > capacity) {
                var first = entries.entrySet().iterator(); SVGDOM old = first.next().getValue(); first.remove(); scope.release(old);
            }
            return true;
        }
    }
    /** Null tint keeps source colors; ARGB tint colors the rendered alpha mask. */
    public synchronized void draw(String id, Canvas canvas, Rect bounds, Integer tint) {
        requireOpen(); Objects.requireNonNull(canvas); Objects.requireNonNull(bounds);
        SVGDOM dom = entries.get(id);
        if (dom == null) throw new IllegalArgumentException("Unknown SVG " + id);
        if (bounds.width() == 0 || bounds.height() == 0) return;
        int saved = canvas.getSaveCount(); canvas.save();
        try {
            var clip = io.github.humbleui.types.Rect.makeXYWH(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            canvas.clipRect(clip);
            if (tint == null) render(dom, canvas, bounds);
            else {
                try (ColorFilter filter = ColorFilter.makeBlend(tint, BlendMode.SRC_IN); Paint paint = new Paint().setColorFilter(filter)) {
                    canvas.saveLayer(clip, paint);
                    render(dom, canvas, bounds);
                    canvas.restore();
                }
            }
        } finally { canvas.restoreToCount(saved); }
    }
    private void render(SVGDOM dom, Canvas canvas, Rect bounds) {
        canvas.translate(bounds.x(), bounds.y());
        dom.setContainerSize(bounds.width(), bounds.height()); dom.render(canvas);
    }
    public synchronized void reload() { requireOpen(); entries.clear(); scope.reload(); }
    @Override public synchronized void close() { entries.clear(); scope.close(); }
    private boolean stale(long ticket) { return runtime.isClosed() || scope.isClosed() || ticket != scope.generation(); }
    private void requireOpen() { runtime.requireOpen(); if (scope.isClosed()) throw new IllegalStateException("SVG store closed"); }
}
