// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.font;

import com.atom.lib.RuntimeHandle;
import io.github.humbleui.skija.*;
import java.io.IOException;
import java.util.*;

/** Owns typefaces only. Each openFont returns a fresh caller-owned Font. */
public final class FontRegistry implements AutoCloseable {
    private final RuntimeHandle runtime;
    private final Map<FontId, FontSource> sources = new LinkedHashMap<>();
    private Map<FontId, Typeface> faces = new LinkedHashMap<>();
    private List<FontId> fallbacks = List.of();
    private long generation;
    private boolean closed;

    public FontRegistry(RuntimeHandle runtime) {
        this.runtime = Objects.requireNonNull(runtime);
        runtime.requireOpen();
        runtime.own(this);
    }
    public synchronized long generation() { requireOpen(); return generation; }
    public synchronized void register(FontId id, FontSource source) {
        requireOpen(); Objects.requireNonNull(id); Objects.requireNonNull(source);
        Typeface replacement = load(source);
        Typeface old = faces.put(id, replacement);
        sources.put(id, source);
        generation++;
        if (old != null) old.close();
    }
    public synchronized void setFallbacks(List<FontId> ids) {
        requireOpen();
        List<FontId> copy = List.copyOf(ids);
        for (FontId id : copy) if (!faces.containsKey(id)) throw new IllegalArgumentException("Unknown font " + id);
        fallbacks = copy; generation++;
    }
    /** Select one face for a whole grapheme; never split combining/ZWJ sequences. */
    public synchronized FontId resolve(FontId primary, String grapheme) {
        requireOpen(); Objects.requireNonNull(grapheme);
        Typeface face = faces.get(primary);
        if (face == null) throw new IllegalArgumentException("Unknown font " + primary);
        if (covers(face, grapheme)) return primary;
        for (FontId id : fallbacks) if (covers(faces.get(id), grapheme)) return id;
        return primary;
    }
    public synchronized Font openFont(FontId primary, float size, String grapheme) {
        requireOpen();
        if (!Float.isFinite(size) || size <= 0) throw new IllegalArgumentException("Invalid font size");
        return new Font(faces.get(resolve(primary, grapheme)), size);
    }
    public synchronized void reload() {
        requireOpen();
        Map<FontId, Typeface> replacements = new LinkedHashMap<>();
        try { for (var entry : sources.entrySet()) replacements.put(entry.getKey(), load(entry.getValue())); }
        catch (RuntimeException | Error failure) {
            replacements.values().forEach(Typeface::close);
            throw failure;
        }
        Map<FontId, Typeface> old = faces; faces = replacements; generation++;
        old.values().forEach(Typeface::close);
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; generation++;
        faces.values().forEach(Typeface::close);
        faces.clear(); sources.clear(); fallbacks = List.of();
    }
    private void requireOpen() {
        if (closed) throw new IllegalStateException("Font registry closed");
        runtime.requireOpen();
    }
    private static boolean covers(Typeface face, String text) {
        return text.codePoints().filter(c -> c != 0x200d && c != 0xfe0f && c != 0xfe0e)
                .allMatch(c -> face.getUTF32Glyph(c) != 0);
    }
    private static Typeface load(FontSource source) {
        try (Data data = Data.makeFromBytes(Objects.requireNonNull(source.read()))) {
            Typeface face = FontMgr.getDefault().makeFromData(data);
            if (face == null) throw new IllegalArgumentException("Invalid font data");
            return face;
        } catch (IOException e) { throw new IllegalArgumentException("Cannot read host font", e); }
    }
}
