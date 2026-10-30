package com.atom.lib.component;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;
import java.util.*;
import java.util.function.Supplier;

/**
 * Compact settings row: a leading title with an optional trailing value and an
 * optional control on the same line ({@link Placement#INLINE}) or the control
 * beneath it ({@link Placement#BELOW}), inside an optional card.
 *
 * <p>{@link SettingRow} stacks title, description and control vertically, which
 * reads as a form. This row keeps the inline title/value line a host can build
 * its own settings language with. Every metric comes from the theme spacing and
 * radius tokens, so it is not tied to one host.</p>
 */
public final class Row implements UiElement {
    public enum Placement { INLINE, BELOW }
    private final NodeId id;
    private final Label title;
    private final Label value;
    private final UiElement control;
    private final Placement placement;
    private final boolean card,divider;

    public Row(String id,String title,UiElement control){this(id,title,null,control,Placement.INLINE,true,false);}
    public Row(String id,String title,Supplier<String> value,UiElement control){this(id,title,value,control,Placement.BELOW,true,false);}
    public Row(String id,String title,Supplier<String> value,UiElement control,Placement placement,boolean card){
        this(id,title,value,control,placement,card,!card);
    }
    public Row(String id,String title,Supplier<String> value,UiElement control,Placement placement,boolean card,boolean divider){
        this.id=new NodeId(id);
        this.title=new Label(id+".title",title,Label.Tone.PRIMARY);
        this.value=value==null?null:new Label(id+".value",value,Label.Tone.SECONDARY);
        this.control=Objects.requireNonNull(control);
        this.placement=Objects.requireNonNull(placement);
        this.card=card;
        this.divider=divider;
    }

    @Override public NodeId id(){return id;}
    @Override public List<UiElement> children(){
        var elements=new ArrayList<UiElement>();elements.add(title);
        if(value!=null)elements.add(value);
        elements.add(control);
        return List.copyOf(elements);
    }
    @Override public LayoutNode layout(HostContext host){
        var spacing=host.theme().spacing();
        var line=new ArrayList<LayoutNode>();line.add(flexible(title.layout(host),1));
        if(value!=null)line.add(value.layout(host));
        if(placement==Placement.INLINE)line.add(control.layout(host));
        var header=new LayoutNode(new NodeId(id.value()+".header"),
                LayoutStyle.row().spacing(0,spacing.small()).behavior(false,false,false,false),line,null,0);
        var body=placement==Placement.BELOW
                ?List.of(header,control.layout(host))
                :List.of(header);
        float padding=card?spacing.large():0;
        return new LayoutNode(id,
                LayoutStyle.column().spacing(padding,spacing.small()).behavior(false,false,card,false),
                body,null,0);
    }
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        var theme=host.theme();
        if(card)UiPaint.rounded(canvas,box.border(),theme.radius().card(),theme.palette().card());
        if(divider){
            // Hairline between rows of one card: polarity-picked, same strength as AC edges.
            var border=box.border();int base=theme.palette().card();
            float luma=0.2126f*((base>>>16)&0xff)+0.7152f*((base>>>8)&0xff)+0.0722f*(base&0xff);
            int color=luma>140?io.github.humbleui.skija.Color.makeARGB(42,0,0,0):io.github.humbleui.skija.Color.makeARGB(42,255,255,255);
            try(var paint=new io.github.humbleui.skija.Paint().setColor(color)){canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(border.x(),border.y(),border.width(),1),paint);}
        }
    }
    private static LayoutNode flexible(LayoutNode node,float grow){
        return new LayoutNode(node.id(),node.style().flex(grow,1,Length.auto()),node.children(),node.measure(),node.measureRevision());
    }
}
