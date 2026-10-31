// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.font;

import com.atom.lib.RuntimeHandle;
import io.github.humbleui.skija.*;
import java.io.IOException;
import java.util.*;

/** Owns typefaces only. Each openFont returns a fresh caller-owned Font. */
public final class FontRegistry implements AutoCloseable {
    /** Reserved ids of the bundled default faces; see {@link #registerDefaults()}. */
    public static final FontId DEFAULT_REGULAR = new FontId("atomlib/default");
    public static final FontId DEFAULT_BOLD = new FontId("atomlib/default-bold");
    private static final String DEFAULT_REGULAR_RESOURCE = "/com/atom/lib/fonts/bundled-default.otf";
    private static final String DEFAULT_BOLD_RESOURCE = "/com/atom/lib/fonts/bundled-default-bold.otf";
    private static final System.Logger LOG = System.getLogger(FontRegistry.class.getName());

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
    /**
     * Registers the library-bundled Noto Sans SC subset as the default faces
     * and appends both to the fallback chain. Host fonts win: resolve() keeps
     * the primary unless it misses a grapheme, and these appended fallbacks
     * sit behind any the host already set. The charset contract and the
     * regeneration workflow live in {@code tools/subset_default_font.py}.
     * Parity with AtomChat's FontManager: a face that fails to load is
     * skipped with a warning instead of taking text rendering down; when both
     * fail the registry keeps whatever the host registered.
     *
     * @return the ids that were actually registered.
     */
    public synchronized List<FontId> registerDefaults() {
        requireOpen();
        List<FontId> registered = new ArrayList<>();
        registerDefault(DEFAULT_REGULAR, DEFAULT_REGULAR_RESOURCE, registered);
        registerDefault(DEFAULT_BOLD, DEFAULT_BOLD_RESOURCE, registered);
        if (!registered.isEmpty()) {
            List<FontId> merged = new ArrayList<>(fallbacks);
            for (FontId id : registered) if (!merged.contains(id)) merged.add(id);
            fallbacks = List.copyOf(merged);
            generation++;
        }
        return List.copyOf(registered);
    }
    private void registerDefault(FontId id, String resource, List<FontId> into) {
        try {
            register(id, FontSource.resource(resource));
            into.add(id);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING,
                    "Bundled default font " + resource + " unavailable, text falls back to host fonts", failure);
        }
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
        Font font = new Font(faces.get(resolve(primary, grapheme)), size);
        // Subpixel shaping keeps fractional glyph advances. With it off, the
        // shaper rounds every advance to whole pixels while measureTextWidth
        // stays fractional, so shaped line widths drift from measured widths
        // (measured +0.25 units per CJK glyph against AtomChat's unrounded
        // path). Every Font this registry hands out shapes and measures
        // through the same unrounded advances.
        font.setSubpixel(true);
        return font;
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
