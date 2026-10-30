// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.image;

import com.atom.lib.RuntimeHandle;
import com.atom.lib.geometry.Rect;
import com.atom.lib.resource.ResourceScope;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import java.util.*;

/** Host-local decoded images, bounded by entry count. No native image escapes the store. */
public final class ImageStore implements AutoCloseable {
    private final RuntimeHandle runtime;
    private final ResourceScope scope;
    private final LinkedHashMap<String, Image> entries = new LinkedHashMap<>(16, .75f, true);
    private final int capacity;
    public ImageStore(RuntimeHandle runtime) { this(runtime, 128); }
    public ImageStore(RuntimeHandle runtime, int capacity) {
        this.runtime = Objects.requireNonNull(runtime);
        if (capacity < 1) throw new IllegalArgumentException("Invalid capacity");
        runtime.requireOpen(); this.capacity = capacity;
        scope = new ResourceScope(runtime.namespace() + "/images"); runtime.own(this);
    }
    public synchronized long generation() { return scope.generation(); }
    public synchronized int size() { return entries.size(); }
    public void put(String id, byte[] encoded) {
        if (!complete(id, generation(), encoded)) throw new IllegalStateException("Image load became stale");
    }
    /** Decode on the calling CPU thread. Capture generation before starting asynchronous work. */
    public boolean complete(String id, long ticket, byte[] encoded) {
        Objects.requireNonNull(id); Objects.requireNonNull(encoded);
        if (id.isBlank()) throw new IllegalArgumentException("Empty asset id");
        synchronized (this) { if (stale(ticket)) return false; }
        Image decoded = Image.makeFromEncoded(encoded);
        if (decoded == null) throw new IllegalArgumentException("Invalid image");
        return acceptDecoded(id, ticket, decoded);
    }
    /** Transfers ownership after argument validation, even if stale/closed. Caller must stop using decoded. */
    public synchronized boolean acceptDecoded(String id, long ticket, Image decoded) {
        Objects.requireNonNull(id); Objects.requireNonNull(decoded);
        if (id.isBlank()) throw new IllegalArgumentException("Empty asset id");
        if (decoded.isClosed()) throw new IllegalArgumentException("Decoded resource is closed");
        synchronized (this) {
            if (runtime.isClosed()) { decoded.close(); return false; }
            if (!scope.accept(ticket, decoded)) return false;
            Image previous = entries.put(id, decoded);
            if (previous != null) scope.release(previous);
            if (entries.size() > capacity) {
                var first = entries.entrySet().iterator(); Image old = first.next().getValue(); first.remove(); scope.release(old);
            }
            return true;
        }
    }
    public synchronized void draw(String id, Canvas canvas, Rect bounds, ImageFit fit, float radius) {
        requireOpen(); Objects.requireNonNull(canvas); Objects.requireNonNull(bounds); Objects.requireNonNull(fit);
        if (!Float.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid radius");
        Image image = entries.get(id);
        if (image == null) throw new IllegalArgumentException("Unknown image " + id);
        if (bounds.width() == 0 || bounds.height() == 0) return;
        float imageWidth = image.getWidth(), imageHeight = image.getHeight();
        float scale = fit == ImageFit.CONTAIN ? Math.min(bounds.width()/imageWidth, bounds.height()/imageHeight)
                                             : Math.max(bounds.width()/imageWidth, bounds.height()/imageHeight);
        float width = imageWidth * scale, height = imageHeight * scale;
        var destination = io.github.humbleui.types.Rect.makeXYWH(bounds.x() + (bounds.width()-width)/2, bounds.y() + (bounds.height()-height)/2, width, height);
        int saved = canvas.getSaveCount(); canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius));
            canvas.drawImageRect(image, destination);
        } finally { canvas.restoreToCount(saved); }
    }
    public synchronized void reload() { requireOpen(); entries.clear(); scope.reload(); }
    @Override public synchronized void close() { entries.clear(); scope.close(); }
    private boolean stale(long ticket) { return runtime.isClosed() || scope.isClosed() || ticket != scope.generation(); }
    private void requireOpen() {
        runtime.requireOpen(); if (scope.isClosed()) throw new IllegalStateException("Image store closed");
    }
}
