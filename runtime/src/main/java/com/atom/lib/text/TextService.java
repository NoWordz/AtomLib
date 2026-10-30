// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.text;

import com.atom.lib.font.*;
import com.atom.lib.layout.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.shaper.Shaper;
import java.util.*;
import java.util.regex.Pattern;

/** Host-local plain-text layout/draw service. Uses Skia shaping and Java 17 extended grapheme boundaries. */
public final class TextService implements AutoCloseable {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private record Key(TextRequest request, Constraints constraints, long generation) {}
    private record Piece(String text, int start, int end, boolean newline) {}
    private final FontRegistry fonts;
    private final Object identity = new Object();
    private final LinkedHashMap<Key, TextLayout> cache = new LinkedHashMap<>(16, .75f, true);
    private final int capacity, characterBudget;
    private int cachedCharacters;
    private boolean closed;

    public TextService(FontRegistry fonts) { this(fonts, 256, 262144); }
    public TextService(FontRegistry fonts, int capacity, int characterBudget) {
        this.fonts = Objects.requireNonNull(fonts);
        if (capacity < 1 || characterBudget < 1) throw new IllegalArgumentException("Invalid cache limits");
        this.capacity = capacity; this.characterBudget = characterBudget;
    }
    public synchronized int cachedLayoutCount() { return cache.size(); }
    public synchronized int cachedCharacterCount() { return cachedCharacters; }
    public synchronized TextLayout layout(TextRequest request, Constraints constraints) {
        requireOpen(); Objects.requireNonNull(request); Objects.requireNonNull(constraints);
        synchronized (fonts) {
            long generation = fonts.generation();
            // Old generations retain no native state, but should not waste the cache budget.
            var iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.getKey().generation != generation) { cachedCharacters -= entry.getKey().request.text().length(); iterator.remove(); }
            }
            Key key = new Key(request, constraints, generation);
            TextLayout present = cache.get(key);
            if (present != null) return present;
            TextLayout result = build(request, constraints, generation);
            if (request.text().length() <= characterBudget) {
                cache.put(key, result); cachedCharacters += request.text().length();
                while (cache.size() > capacity || cachedCharacters > characterBudget) {
                    var first = cache.entrySet().iterator();
                    var old = first.next(); cachedCharacters -= old.getKey().request.text().length(); first.remove();
                }
            }
            return result;
        }
    }
    /** Borrows Canvas. Restores its save stack; never closes Canvas. */
    public synchronized void draw(Canvas canvas, TextLayout layout, float x, float y) {
        requireOpen(); Objects.requireNonNull(canvas); Objects.requireNonNull(layout);
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Invalid origin");
        synchronized (fonts) {
            if (layout.owner != identity || layout.generation != fonts.generation()) throw new IllegalStateException("Foreign or stale text layout");
            int saved = canvas.getSaveCount(); canvas.save();
            try (Paint paint = new Paint().setColor(layout.request().color()); Shaper shaper = Shaper.makeShapeDontWrapOrReorder()) {
                canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(x, y, layout.size().width(), layout.size().height()));
                for (var line : layout.lines()) for (var run : line.runs()) {
                    try (Font font = fonts.openFont(run.font(), layout.request().size(), ""); TextLine shaped = shaper.shapeLine(run.text(), font)) {
                        canvas.drawTextLine(shaped, x + run.x(), y + line.baseline(), paint);
                    }
                }
            } finally { canvas.restoreToCount(saved); }
        }
    }
    private TextLayout build(TextRequest request, Constraints constraints, long generation) {
        List<Piece> pieces = new ArrayList<>();
        var matcher = GRAPHEME.matcher(request.text());
        while (matcher.find()) {
            String value = matcher.group();
            pieces.add(new Piece(value, matcher.start(), matcher.end(), value.equals("\n") || value.equals("\r") || value.equals("\r\n")));
        }
        if (!pieces.isEmpty() && pieces.get(pieces.size()-1).newline)
            pieces.add(new Piece("", request.text().length(), request.text().length(), true));
        float limit = constraints.width().mode() == Constraints.Mode.UNDEFINED ? Float.POSITIVE_INFINITY : constraints.width().value();
        float heightLimit = constraints.height().mode() == Constraints.Mode.UNDEFINED ? Float.POSITIVE_INFINITY : constraints.height().value();
        List<TextLayout.Line> lines = new ArrayList<>();
        float top = 0, naturalWidth = 0;
        int cursor = 0;
        boolean truncated = false;
        try (Shaper shaper = Shaper.makeShapeDontWrapOrReorder()) {
            while (cursor < pieces.size()) {
                int start = cursor;
                int paragraphEnd = start;
                while (paragraphEnd < pieces.size() && !pieces.get(paragraphEnd).newline) paragraphEnd++;
                int end = start;
                // Binary search only grapheme boundaries; force one whole cluster if it exceeds the viewport.
                int low = start + 1, high = paragraphEnd;
                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    if (shape(request, pieces.subList(start, mid), "", top, shaper).width() <= limit) { end = mid; low = mid + 1; }
                    else high = mid - 1;
                }
                if (start < paragraphEnd && end == start) end++;
                boolean consumesNewline = end == paragraphEnd && paragraphEnd < pieces.size();
                int next = end + (consumesNewline ? 1 : 0);
                TextLayout.Line line = shape(request, pieces.subList(start, end), "", top, shaper);
                if (start == end) line = emptyLine(request, pieces.get(start).start, top);
                if (lines.size() >= request.maxLines() || top + line.height() > heightLimit) { truncated = true; break; }
                if (lines.size() + 1 == request.maxLines() && next < pieces.size()) {
                    truncated = true;
                    if (request.ellipsis()) line = ellipsize(request, pieces.subList(start, end), top, limit, shaper, pieces.get(start).start);
                    lines.add(line); top += line.height(); naturalWidth = Math.max(naturalWidth, line.width()); cursor = next; break;
                }
                lines.add(line); top += line.height(); naturalWidth = Math.max(naturalWidth, line.width()); cursor = next;
            }
            if (truncated && request.ellipsis() && !lines.isEmpty() && cursor < pieces.size()
                    && !lines.get(lines.size()-1).runs().stream().anyMatch(r -> r.text().endsWith("…"))) {
                TextLayout.Line last = lines.remove(lines.size()-1);
                List<Piece> previous = pieces.stream().filter(p -> p.start >= last.start() && p.end <= last.end() && !p.newline).toList();
                TextLayout.Line replacement = ellipsize(request, previous, last.top(), limit, shaper, last.start());
                lines.add(replacement);
                top = replacement.top() + replacement.height();
                naturalWidth = 0;
                for (var item : lines) naturalWidth = Math.max(naturalWidth, item.width());
            }
        }
        float baseline = lines.isEmpty() ? 0 : lines.get(0).baseline();
        return new TextLayout(request, new MeasuredSize(constraints.width().constrain(naturalWidth), constraints.height().constrain(top), baseline), lines, truncated, identity, generation);
    }
    private TextLayout.Line emptyLine(TextRequest request, int offset, float top) {
        try (Font font = fonts.openFont(request.font(), request.size(), "")) {
            var metrics = font.getMetrics(); float ascent = -metrics.getAscent();
            float height = Math.max(1, font.getSpacing());
            return new TextLayout.Line(offset, offset, top, top + ascent, height, 0, List.of(), List.of(new TextLayout.Caret(offset, 0)));
        }
    }
    private TextLayout.Line ellipsize(TextRequest request, List<Piece> pieces, float top, float limit, Shaper shaper, int offset) {
        int count = pieces.size();
        TextLayout.Line line;
        do {
            line = shape(request, pieces.subList(0, count), "…", top, shaper, offset);
            if (line.width() <= limit || count == 0) return line;
            count--;
        } while (true);
    }
    private TextLayout.Line shape(TextRequest request, List<Piece> pieces, String suffix, float top, Shaper shaper) {
        return shape(request, pieces, suffix, top, shaper, 0);
    }
    private TextLayout.Line shape(TextRequest request, List<Piece> pieces, String suffix, float top, Shaper shaper, int emptyOffset) {
        List<TextLayout.Run> runs = new ArrayList<>();
        List<TextLayout.Caret> carets = new ArrayList<>();
        int start = pieces.isEmpty() ? emptyOffset : pieces.get(0).start;
        int end = pieces.isEmpty() ? start : pieces.get(pieces.size()-1).end;
        List<Piece> display = new ArrayList<>(pieces);
        if (!suffix.isEmpty()) display.add(new Piece(suffix, end, end, false));
        float x = 0, ascent = 0, descent = 0, leading = 0;
        int cursor = 0;
        while (cursor < display.size()) {
            FontId id = fonts.resolve(request.font(), display.get(cursor).text);
            int runEnd = cursor + 1;
            while (runEnd < display.size() && fonts.resolve(request.font(), display.get(runEnd).text).equals(id)) runEnd++;
            StringBuilder builder = new StringBuilder();
            for (int i = cursor; i < runEnd; i++) builder.append(display.get(i).text);
            String value = builder.toString();
            try (Font font = fonts.openFont(id, request.size(), ""); TextLine line = shaper.shapeLine(value, font)) {
                ascent = Math.max(ascent, -line.getAscent()); descent = Math.max(descent, line.getDescent()); leading = Math.max(leading, line.getLeading());
                runs.add(new TextLayout.Run(value, id, x, line.getWidth()));
                int local = 0;
                for (int i = cursor; i < runEnd; i++) {
                    Piece piece = display.get(i);
                    float position = x + line.getCoordAtOffset(local);
                    if (carets.isEmpty() || carets.get(carets.size()-1).offset() != piece.start)
                        carets.add(new TextLayout.Caret(piece.start, position));
                    local += piece.text.length();
                    if (piece.end != piece.start) carets.add(new TextLayout.Caret(piece.end, x + line.getCoordAtOffset(local)));
                }
                x += line.getWidth();
            }
            cursor = runEnd;
        }
        if (carets.isEmpty()) carets.add(new TextLayout.Caret(start, 0));
        return new TextLayout.Line(start, end, top, top + ascent, Math.max(1, ascent + descent + leading), x, runs, carets);
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Text service closed"); }
    @Override public synchronized void close() { closed = true; cache.clear(); cachedCharacters = 0; }
}
