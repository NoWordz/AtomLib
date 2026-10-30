package com.atom.lib.component;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.*;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;
import java.util.Objects;
import java.util.function.Supplier;
public final class Label implements UiElement {
    public enum Tone { PRIMARY, SECONDARY, TITLE, ON_ACCENT, DISABLED }
    private final NodeId id;
    private final Supplier<String> content;
    private Tone tone;
    private HostContext host;
    private TextRequest request;
    private Object service;
    private long fontGeneration=-1,revision;
    private TextLayout displayed;
    private final LeafMeasure measure=c->host.text().layout(request,c).size();
    public Label(String id,String content,Tone tone){this(id,()->content,tone);}
    public Label(String id,Supplier<String> content,Tone tone){this.id=new NodeId(id);this.content=Objects.requireNonNull(content);this.tone=Objects.requireNonNull(tone);}
    @Override public NodeId id(){return id;}
    public void setTone(Tone tone){this.tone=Objects.requireNonNull(tone);}
    @Override public LayoutNode layout(HostContext next){
        var palette=next.theme().palette();var type=next.theme().type();
        float size=tone==Tone.TITLE?type.title():tone==Tone.SECONDARY?type.caption():type.body();
        int color=tone==Tone.SECONDARY||tone==Tone.DISABLED?palette.muted():tone==Tone.ON_ACCENT?palette.onAccent():palette.text();
        var candidate=new TextRequest(content.get(),next.bodyFont(),size,color);
        long generation=next.fonts().generation();
        if(request==null||!candidate.text().equals(request.text())||!candidate.font().equals(request.font())||candidate.size()!=request.size()||service!=next.text()||generation!=fontGeneration)revision++;
        host=next;request=candidate;service=next.text();fontGeneration=generation;
        return LayoutNode.leaf(id.value(),LayoutStyle.column().flex(0,0,Length.auto()),measure,revision);
    }
    @Override public void geometry(LayoutBox box,HostContext host){displayed=host.text().layout(request,new Constraints(Constraints.Axis.atMost(box.content().width()),Constraints.Axis.atMost(box.content().height())));}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){if(displayed==null)throw new IllegalStateException("Label geometry not prepared");host.text().draw(canvas,displayed,box.content().x(),box.content().y());}
    public TextLayout textLayout(){if(displayed==null)throw new IllegalStateException("Label geometry not prepared");return displayed;}
}
