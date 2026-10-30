// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.text;

import com.atom.lib.font.FontId;
import com.atom.lib.geometry.Rect;
import com.atom.lib.layout.MeasuredSize;
import java.util.*;

/** Immutable geometry. Native objects are never exposed or retained here. Offsets are UTF-16 grapheme boundaries. */
public final class TextLayout {
    public record Run(String text, FontId font, float x, float width) {}
    public record Caret(int offset, float x) {}
    public record Line(int start, int end, float top, float baseline, float height, float width, List<Run> runs, List<Caret> carets) {
        public Line { runs = List.copyOf(runs); carets = List.copyOf(carets); }
    }
    private final TextRequest request;
    private final MeasuredSize size;
    private final List<Line> lines;
    private final boolean truncated;
    final Object owner;
    final long generation;
    TextLayout(TextRequest request, MeasuredSize size, List<Line> lines, boolean truncated, Object owner, long generation) {
        this.request = request; this.size = size; this.lines = List.copyOf(lines);
        this.truncated = truncated; this.owner = owner; this.generation = generation;
    }
    public TextRequest request() { return request; }
    public MeasuredSize size() { return size; }
    public List<Line> lines() { return lines; }
    public boolean truncated() { return truncated; }
    public int hitTest(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Non-finite hit");
        if (lines.isEmpty()) return 0;
        Line line = lines.get(lines.size()-1);
        for (Line candidate : lines) if (y < candidate.top() + candidate.height()) { line = candidate; break; }
        Caret best = line.carets().get(0);
        for (Caret caret : line.carets()) if (Math.abs(x - caret.x()) < Math.abs(x - best.x())) best = caret;
        return best.offset();
    }
    /** Endpoints inside a grapheme expand to cover the whole grapheme. */
    public List<Rect> selection(int from, int to) {
        if (from < 0 || to < 0 || from > request.text().length() || to > request.text().length())
            throw new IndexOutOfBoundsException("Invalid selection");
        int low = Math.min(from, to), high = Math.max(from, to);
        if (low == high) return List.of();
        List<Rect> result = new ArrayList<>();
        for (Line line : lines) {
            if (high <= line.start() || low >= line.end()) continue;
            float left = 0, right = line.width();
            for (Caret caret : line.carets()) {
                if (caret.offset() <= low) left = caret.x();
                if (caret.offset() >= high) { right = caret.x(); break; }
            }
            left = Math.max(0, Math.min(size.width(), left)); right = Math.max(left, Math.min(size.width(), right));
            float bottom = Math.min(size.height(), line.top() + line.height());
            if (right > left && bottom > line.top()) result.add(new Rect(left, line.top(), right-left, bottom-line.top()));
        }
        return List.copyOf(result);
    }
}
