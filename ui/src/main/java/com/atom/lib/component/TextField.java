// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.component;

import com.atom.lib.font.FontId;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.*;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import java.util.*;
import java.util.regex.Pattern;

/** Single-line LTR editor. The host supplies committed Unicode input and clipboard access. */
public final class TextField implements UiElement, PointerTarget {
    public interface Clipboard { String read(); void write(String text); }
    public enum Command { COPY, CUT, PASTE, SELECT_ALL }
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final FontId VALIDATION_FONT = new FontId("text-field-validation");
    private final NodeId id;
    private final ValueBinding<String> binding;
    private final Clipboard clipboard;
    private String draft, original;
    private int anchor, caret;
    private boolean editing, selecting, enabled = true;
    private float height = 40, scrollX;
    private long revision;
    private HostContext host;
    private Rect hit = new Rect(0,0,0,0), clip = hit, inner = hit;
    private TextLayout displayed;
    private final LeafMeasure measure = c -> new MeasuredSize(c.width().constrain(height * 6),c.height().constrain(height),0);

    public TextField(String id, ValueBinding<String> binding, Clipboard clipboard) {
        this.id = new NodeId(id); this.binding = Objects.requireNonNull(binding); this.clipboard = Objects.requireNonNull(clipboard);
        draft = valid(binding.read()); original = draft; anchor = caret = draft.length();
    }
    @Override public NodeId id() { return id; }
    @Override public PointerTarget pointerTarget() { return this; }
    @Override public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; if (!value) cancel(); }
    public boolean isEditing() { return editing; }
    public String text() { return draft; }
    public int caret() { return caret; }
    public int anchor() { return anchor; }
    public float horizontalOffset() { return scrollX; }
    public void focus() {
        if (!enabled || editing) return;
        draft = valid(binding.read()); original = draft; anchor = caret = draft.length(); editing = true;
        prepare();
    }
    public void setFocused(boolean value) { if (value) focus(); else commit(); }
    @Override public void focusChanged(boolean value) { setFocused(value); }
    @Override public void textInput(String text) { if (!enabled) return; if (!editing) focus(); insertText(text); }
    public void select(int anchor, int caret) {
        requireEditing();
        if (!boundaries().contains(anchor) || !boundaries().contains(caret)) throw new IllegalArgumentException("Selection splits a grapheme or is outside the text");
        this.anchor = anchor; this.caret = caret; revealCaret();
    }
    public void insertText(String text) {
        requireEditing(); Objects.requireNonNull(text);
        int low = Math.min(anchor,caret), high = Math.max(anchor,caret);
        String next = valid(draft.substring(0,low) + text + draft.substring(high));
        draft = next; anchor = caret = low + text.length();
        // A preceding combining mark may join the insertion: snap to a legal boundary.
        caret = ceilingBoundary(caret); anchor = caret;
        try { binding.preview(draft); }
        catch (RuntimeException | Error failure) { rollback(failure); throw failure; }
        prepare();
    }
    public void command(Command command) {
        requireEditing(); Objects.requireNonNull(command);
        int low = Math.min(anchor,caret), high = Math.max(anchor,caret);
        switch (command) {
            case SELECT_ALL -> select(0,draft.length());
            case COPY -> { if (low != high) clipboard.write(draft.substring(low,high)); }
            case CUT -> { if (low != high) { clipboard.write(draft.substring(low,high)); insertText(""); } }
            case PASTE -> insertText(Objects.requireNonNull(clipboard.read()).replace("\r\n"," ").replace('\r',' ').replace('\n',' ').replace('\t',' '));
        }
    }
    public void commit() {
        if (!editing) return;
        String before = original, next = draft; editing = selecting = false;
        try { binding.commit(next); original = next; }
        catch (RuntimeException | Error failure) {
            draft = original = before; anchor = caret = draft.length();
            try { binding.cancel(before); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @Override public void cancel() {
        if (!editing) return;
        editing = selecting = false; draft = original; anchor = caret = draft.length();
        displayed = null; binding.cancel(original);
    }
    private void rollback(Throwable primary) {
        try { cancel(); } catch (RuntimeException | Error cleanup) { if (cleanup != primary) primary.addSuppressed(cleanup); }
    }
    @Override public InputResult key(KeyEvent event) {
        if (!enabled || !event.pressed()) return InputResult.PASS;
        if (!editing) {
            boolean shortcut=event.control()&&Set.of("A","C","V","X").contains(event.key().name());
            if (!shortcut && !Set.of(KeyEvent.Key.LEFT,KeyEvent.Key.RIGHT,KeyEvent.Key.HOME,KeyEvent.Key.END,KeyEvent.Key.BACKSPACE,KeyEvent.Key.DELETE).contains(event.key())) return InputResult.PASS;
            focus();
        }
        // name-based shortcuts remain source-compatible while the shared enum is extended.
        if (event.control()) {
            Command command = switch (event.key().name()) { case "A" -> Command.SELECT_ALL; case "C" -> Command.COPY; case "V" -> Command.PASTE; case "X" -> Command.CUT; default -> null; };
            if (command != null) { command(command); return InputResult.CONSUMED; }
        }
        switch (event.key()) {
            case ESCAPE -> cancel();
            case ENTER -> commit();
            case LEFT, RIGHT, HOME, END -> {
                int next;
                if (event.key() == KeyEvent.Key.HOME) next = 0;
                else if (event.key() == KeyEvent.Key.END) next = draft.length();
                else if (!event.shift() && anchor != caret) next = event.key() == KeyEvent.Key.LEFT ? Math.min(anchor,caret) : Math.max(anchor,caret);
                else { var points = boundaries(); int at = points.indexOf(caret); next = points.get(Math.max(0,Math.min(points.size()-1,at + (event.key() == KeyEvent.Key.LEFT ? -1 : 1)))); }
                caret = next; if (!event.shift()) anchor = caret; revealCaret();
            }
            case BACKSPACE, DELETE -> {
                if (anchor == caret) {
                    var points = boundaries(); int at = points.indexOf(caret);
                    if (event.key() == KeyEvent.Key.BACKSPACE && at > 0) anchor = points.get(at-1);
                    if (event.key() == KeyEvent.Key.DELETE && at+1 < points.size()) anchor = points.get(at+1);
                }
                if (anchor != caret) insertText("");
            }
            default -> { return InputResult.PASS; }
        }
        return InputResult.CONSUMED;
    }
    @Override public LayoutNode layout(HostContext host) {
        this.host = Objects.requireNonNull(host);
        if (!editing) { String current = valid(binding.read()); if (!current.equals(draft)) { draft = original = current; anchor = caret = draft.length(); } }
        if (height != host.theme().spacing().controlHeight()) { height = host.theme().spacing().controlHeight(); revision++; }
        return LayoutNode.leaf(id.value(),LayoutStyle.column().flex(0,0,Length.auto()),measure,revision);
    }
    @Override public void geometry(LayoutBox box, HostContext host) {
        this.host = host; hit = box.hit(); clip = box.clip();
        float pad = Math.min(host.theme().spacing().small(),Math.min(box.content().width(),box.content().height())/2);
        inner = box.content().inset(pad); prepare();
    }
    private void prepare() {
        if (host == null) return;
        displayed = host.text().layout(new TextRequest(draft,host.bodyFont(),host.theme().type().body(),host.theme().palette().text()),new Constraints(Constraints.Axis.undefined(),Constraints.Axis.undefined()));
        revealCaret();
    }
    private float xAt(int offset) {
        if (displayed == null || displayed.lines().isEmpty()) return 0;
        for (var position : displayed.lines().get(0).carets()) if (position.offset() == offset) return position.x();
        return displayed.size().width();
    }
    private void revealCaret() {
        if (displayed == null) return;
        float x = xAt(caret), available = Math.max(0,inner.width()-1);
        if (x < scrollX) scrollX = x;
        if (x > scrollX+available) scrollX = x-available;
        scrollX = Math.max(0,Math.min(scrollX,Math.max(0,displayed.size().width()-available)));
    }
    @Override public boolean press(PointerEvent event) {
        if (!enabled || event.button()!=0 || !hit.contains(event.x(),event.y()) || !clip.contains(event.x(),event.y())) return false;
        focus(); selecting = true; caret = anchor = offsetAt(event.x()); revealCaret(); return true;
    }
    private int offsetAt(float x) { return displayed == null ? 0 : displayed.hitTest(x-inner.x()+scrollX,0); }
    @Override public void drag(PointerEvent event) { if (selecting && editing) { caret = offsetAt(event.x()); revealCaret(); } }
    @Override public void release(PointerEvent event) { if (selecting && editing) drag(event); selecting = false; }
    @Override public void paint(Canvas canvas, LayoutBox box, HostContext host, FrameTime time) {
        if (displayed == null) prepare();
        int saved = canvas.getSaveCount(); canvas.save();
        try (Paint paint = new Paint().setAntiAlias(true)) {
            var p = host.theme().palette(); var border = box.border();
            canvas.drawRRect(RRect.makeXYWH(border.x(),border.y(),border.width(),border.height(),host.theme().radius().control()),paint.setColor(editing?p.accent():p.control()));
            canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(inner.x(),inner.y(),inner.width(),inner.height()));
            float top = inner.y()+Math.max(0,(inner.height()-displayed.size().height())/2);
            if (anchor != caret) for (var selected : displayed.selection(Math.min(anchor,caret),Math.max(anchor,caret)))
                canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(inner.x()-scrollX+selected.x(),top+selected.y(),selected.width(),selected.height()),paint.setColor((p.accent()&0x00ffffff)|0x66000000));
            host.text().draw(canvas,displayed,inner.x()-scrollX,top);
            if (editing && (host.theme().motion().reducedMotion() || (time.nowNanos()/500_000_000L)%2==0)) {
                float x = inner.x()+xAt(caret)-scrollX;
                canvas.drawLine(x,inner.y(),x,inner.bottom(),paint.setColor(p.text()).setStrokeWidth(1));
            }
        } finally { canvas.restoreToCount(saved); }
    }
    private List<Integer> boundaries() {
        var result = new ArrayList<Integer>(); result.add(0); var matcher = GRAPHEME.matcher(draft);
        while (matcher.find()) result.add(matcher.end()); return result;
    }
    private int ceilingBoundary(int offset) { for (int point : boundaries()) if (point >= offset) return point; return draft.length(); }
    private void requireEditing() { if (!editing || !enabled) throw new IllegalStateException("Text field is not editing"); }
    private static String valid(String value) {
        Objects.requireNonNull(value);
        if (value.indexOf('\r')>=0 || value.indexOf('\n')>=0) throw new IllegalArgumentException("TextField is single-line");
        new TextRequest(value,VALIDATION_FONT,1,0); return value;
    }
}
