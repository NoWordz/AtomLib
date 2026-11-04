// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.text;

import com.atom.lib.font.FontId;
import com.atom.lib.font.FontRegistry;
import com.atom.lib.layout.Constraints;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.skija.paragraph.*;
import java.util.*;

/**
 * Explicit, uncached LTR Paragraph path; this does not change {@link TextService}.
 * Each layout owns native state until it or this service is closed. Callers must
 * close short-lived layouts promptly. Font registry changes invalidate existing
 * layouts. The next layout creation or live-layout query/draw releases all stale
 * native state; if no further operation occurs, close the layouts or service.
 * Operations on layouts from this service and close are serialized on this service.
 * ICU4J supplies grapheme boundaries; Paragraph itself decides line breaks.
 */
public final class ParagraphTextService implements AutoCloseable {
    private final FontRegistry fonts;
    private final Set<ParagraphTextLayout> layouts = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;
    private boolean generationKnown;
    private long knownGeneration;

    public ParagraphTextService(FontRegistry fonts) { this.fonts = Objects.requireNonNull(fonts); }

    /**
     * Layouts own their native Paragraph and are also closed when this service closes.
     * A finite height clips the result; only maxLines controls Paragraph ellipsis.
     * Throws IllegalStateException when ICU4J is missing or incompatible.
     */
    public synchronized ParagraphTextLayout layout(TextRequest request, Constraints constraints) {
        requireOpen(); Objects.requireNonNull(request); Objects.requireNonNull(constraints);
        synchronized (fonts) {
            long generation = synchronizeGeneration();
            // Resolve an entire grapheme at a time, using precisely the registry's fallback order.
            var runs = new ArrayList<Run>();
            var boundaries = ParagraphGraphemes.of(request.text());
            for (int i = 1; i < boundaries.size(); i++) {
                String cluster = request.text().substring(boundaries.get(i-1), boundaries.get(i));
                FontId face = fonts.resolve(request.font(), cluster);
                if (!runs.isEmpty() && runs.get(runs.size()-1).font.equals(face))
                    runs.get(runs.size()-1).text.append(cluster);
                else runs.add(new Run(new StringBuilder(cluster), face));
            }
            // Validate the primary even for an empty request.
            fonts.resolve(request.font(), "");
            Paragraph paragraph = null;
            try (var provider = new TypefaceFontProvider(); var collection = new FontCollection();
                 var defaultStyle = new TextStyle(); var paragraphStyle = new ParagraphStyle()) {
                Map<FontId, String> aliases = new LinkedHashMap<>();
                aliases.put(request.font(), "atom-0");
                for (Run run : runs) aliases.computeIfAbsent(run.font, ignored -> "atom-" + aliases.size());
                for (var entry : aliases.entrySet()) {
                    try (Font font = fonts.openFont(entry.getKey(), request.size(), ""); Typeface face = font.getTypeface()) {
                        provider.registerTypeface(face, entry.getValue());
                    }
                }
                collection.setAssetFontManager(provider).setEnableFallback(false);
                defaultStyle.setFontFamily(aliases.get(request.font())).setFontSize(request.size()).setColor(request.color());
                paragraphStyle.setTextStyle(defaultStyle).setDirection(Direction.LTR).setMaxLinesCount(request.maxLines());
                if (request.ellipsis()) paragraphStyle.setEllipsis("…");
                try (var builder = new ParagraphBuilder(paragraphStyle, collection)) {
                    try {
                        for (Run run : runs) {
                            try (var style = new TextStyle()) {
                                style.setFontFamily(aliases.get(run.font)).setFontSize(request.size()).setColor(request.color());
                                // Skia recognizes LF and CRLF, but a lone CR is not a hard break.
                                // Replacing only lone CR preserves all UTF-16 offsets.
                                builder.pushStyle(style).addText(run.text.toString().replaceAll("\\r(?!\\n)", "\n")).popStyle();
                            }
                        }
                        // A ManagedString is required even when the input is empty.
                        if (runs.isEmpty()) builder.addText("");
                        paragraph = builder.build();
                    } finally {
                        // Skija transfers this string on build but does not close it on a failed build.
                        if (builder._text != null) { builder._text.close(); builder._text = null; }
                    }
                }
                float width = constraints.width().mode() == Constraints.Mode.UNDEFINED ? 1000000f : constraints.width().value();
                paragraph.layout(width);
                if (constraints.width().mode() == Constraints.Mode.UNDEFINED) {
                    float intrinsic = paragraph.getMaxIntrinsicWidth();
                    if (!Float.isFinite(intrinsic)) throw new IllegalArgumentException("Paragraph intrinsic width is not finite");
                    if (intrinsic > width) {
                        // Intrinsic width can be rounded below the line's actual advance.
                        float expanded = Math.nextUp(intrinsic * 1.01f + request.size());
                        if (!Float.isFinite(expanded)) throw new IllegalArgumentException("Paragraph intrinsic width is too large");
                        paragraph.layout(expanded);
                    }
                }
                var result = new ParagraphTextLayout(this, fonts, paragraph, request, constraints, generation, boundaries);
                layouts.add(result);
                paragraph = null;
                return result;
            } finally {
                if (paragraph != null) paragraph.close();
            }
        }
    }
    private record Run(StringBuilder text, FontId font) {}
    void requireOpen() { if (closed) throw new IllegalStateException("Paragraph service closed"); }
    void remove(ParagraphTextLayout layout) { layouts.remove(layout); }
    // Caller holds the service monitor. FontRegistry never calls back into this service.
    long synchronizeGeneration() {
        synchronized (fonts) {
            long current;
            try { current = fonts.generation(); }
            catch (IllegalStateException unavailable) {
                for (var layout : List.copyOf(layouts)) layout.close();
                generationKnown = false;
                throw unavailable;
            }
            if (!generationKnown || knownGeneration != current) {
                for (var layout : List.copyOf(layouts))
                    if (!layout.hasGeneration(current)) layout.close();
                knownGeneration = current;
                generationKnown = true;
            }
            return current;
        }
    }
    @Override public synchronized void close() {
        if (closed) return;
        for (var layout : List.copyOf(layouts)) layout.close();
        closed = true;
    }
}
