// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.text;

import com.atom.lib.font.FontRegistry;
import com.atom.lib.geometry.Rect;
import com.atom.lib.layout.Constraints;
import com.atom.lib.layout.MeasuredSize;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.paragraph.*;
import java.util.*;

/** Caller-owned Paragraph. All geometry and paint use the same native layout.
 * Offsets are UTF-16; partial grapheme selections expand to whole clusters.
 * Calls are serialized by the owning service; reload invalidates the result. */
public final class ParagraphTextLayout implements AutoCloseable {
    public record Line(int start, int end, float top, float baseline, float height, float width) {}
    private final ParagraphTextService owner;
    private final FontRegistry fonts;
    private final Paragraph paragraph;
    private final TextRequest request;
    private final long generation;
    private final MeasuredSize size;
    private final List<Line> lines;
    private final int[] boundaries;
    private final boolean truncated;
    private boolean closed;

    ParagraphTextLayout(ParagraphTextService owner, FontRegistry fonts, Paragraph paragraph, TextRequest request, Constraints constraints, long generation, List<Integer> offsets) {
        this.owner=owner; this.fonts=fonts; this.paragraph=paragraph; this.request=request; this.generation=generation;
        boundaries = offsets.stream().mapToInt(Integer::intValue).toArray();
        float naturalHeight = request.text().isEmpty() ? 0 : paragraph.getHeight();
        float naturalWidth = request.text().isEmpty() ? 0 : Math.max(0, paragraph.getLongestLine());
        if (!request.text().isEmpty()) for (var box : paragraph.getRectsForRange(0, request.text().length(), RectHeightMode.TIGHT, RectWidthMode.TIGHT))
            naturalWidth = Math.max(naturalWidth, box.getRect().getRight());
        var metrics = paragraph.getLineMetrics();
        if (!request.text().isEmpty()) for (var metric : metrics)
            naturalWidth = Math.max(naturalWidth, (float)(metric.getLeft() + metric.getWidth()));
        float width = constraints.width().constrain(naturalWidth);
        float height = constraints.height().constrain(naturalHeight);
        size = new MeasuredSize(width, height, request.text().isEmpty() ? 0 : Math.max(0, paragraph.getAlphabeticBaseline()));
        var visible = new ArrayList<Line>();
        if (!request.text().isEmpty()) for (var metric : metrics) {
            float lineHeight = (float)metric.getHeight();
            float top = (float)(metric.getBaseline() - metric.getAscent());
            if (top >= height) break;
            visible.add(new Line((int)metric.getStartIndex(), (int)metric.getEndIndex(), top,
                    (float)metric.getBaseline(), lineHeight, (float)metric.getWidth()));
        }
        lines=List.copyOf(visible);
        truncated = paragraph.didExceedMaxLines() || naturalHeight > height || naturalWidth > width;
    }
    public TextRequest request() { return request; }
    public MeasuredSize size() { synchronized(owner) { check(); return size; } }
    public List<Line> lines() { synchronized(owner) { check(); return lines; } }
    public boolean truncated() { synchronized(owner) { check(); return truncated; } }

    /** Borrow canvas; paint and clip at the supplied origin, restoring its save stack. */
    public void draw(Canvas canvas, float x, float y) {
        Objects.requireNonNull(canvas);
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Invalid origin");
        synchronized(owner) { synchronized(fonts) {
            check();
            int saved = canvas.getSaveCount(); canvas.save();
            try {
                canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(x,y,size.width(),size.height()));
                paragraph.paint(canvas,x,y);
            } finally { canvas.restoreToCount(saved); }
        }}
    }
    public int hitTest(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Non-finite hit");
        synchronized(owner) { synchronized(fonts) {
            check();
            if (lines.isEmpty() || size.width() == 0 || size.height() == 0) return 0;
            x = Math.max(0, Math.min(x, size.width()));
            float py = Math.max(0, Math.min(y, Math.nextDown(size.height())));
            int index = Math.max(0, Math.min(request.text().length(), paragraph.getGlyphPositionAtCoordinate(x,py).getPosition()));
            int pos = Arrays.binarySearch(boundaries,index);
            if (pos >= 0) return index;
            int insertion = -pos-1, start=boundaries[insertion-1], end=boundaries[insertion];
            var boxes=paragraph.getRectsForRange(start,end,RectHeightMode.TIGHT,RectWidthMode.TIGHT);
            if (boxes.length == 0) return start;
            var box=boxes[0].getRect();
            return x < (box.getLeft()+box.getRight())/2 ? start : end;
        }}
    }
    public List<Rect> selection(int from, int to) {
        synchronized(owner) { synchronized(fonts) {
            check();
            int length=request.text().length();
            if (from<0 || to<0 || from>length || to>length) throw new IndexOutOfBoundsException("Invalid selection");
            int low=Math.min(from,to), high=Math.max(from,to);
            if (low==high || size.width()==0 || size.height()==0) return List.of();
            int lo=Arrays.binarySearch(boundaries,low), hi=Arrays.binarySearch(boundaries,high);
            low=boundaries[lo>=0 ? lo : -lo-2]; high=boundaries[hi>=0 ? hi : -hi-1];
            var result=new ArrayList<Rect>();
            for (var box : paragraph.getRectsForRange(low,high,RectHeightMode.TIGHT,RectWidthMode.TIGHT)) {
                var r=box.getRect();
                float left=Math.max(0,r.getLeft()), top=Math.max(0,r.getTop());
                float right=Math.min(size.width(),r.getRight()), bottom=Math.min(size.height(),r.getBottom());
                if (right>left && bottom>top) result.add(new Rect(left,top,right-left,bottom-top));
            }
            return List.copyOf(result);
        }}
    }
    private void check() {
        owner.requireOpen();
        owner.synchronizeGeneration();
        if (closed) throw new IllegalStateException("Paragraph layout closed");
    }
    boolean hasGeneration(long current) { return generation == current; }
    @Override public void close() {
        synchronized(owner) {
            if (closed) return;
            closed=true;
            try { paragraph.close(); } finally { owner.remove(this); }
        }
    }
}
