package com.atom.lib.component;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;
import java.util.*;
public final class Section implements UiElement {
    public enum Kind { ROOT, GROUP, CARD, SCROLL }
    private final NodeId id;
    private final Kind kind;
    private final List<UiElement> children;
    public Section(String id,Kind kind,List<? extends UiElement> children){this.id=new NodeId(id);this.kind=Objects.requireNonNull(kind);this.children=List.copyOf(children);}
    @Override public NodeId id(){return id;}
    @Override public List<UiElement> children(){return children;}
    @Override public LayoutNode layout(HostContext host){
        var spacing=host.theme().spacing();float padding=kind==Kind.ROOT?spacing.large():kind==Kind.CARD?spacing.medium():0;
        var style=LayoutStyle.column().spacing(padding,spacing.medium()).flex(kind==Kind.SCROLL?1:0,kind==Kind.SCROLL?1:0,kind==Kind.SCROLL?Length.points(0):Length.auto()).behavior(false,false,kind==Kind.CARD||kind==Kind.SCROLL,kind==Kind.SCROLL);
        return new LayoutNode(id,style,children.stream().map(c->c.layout(host)).toList(),null,0);
    }
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        if(kind==Kind.ROOT)UiPaint.rounded(canvas,box.border(),0,host.theme().palette().surface());
        if(kind==Kind.CARD)UiPaint.rounded(canvas,box.border(),host.theme().radius().card(),host.theme().palette().card());
    }
}
