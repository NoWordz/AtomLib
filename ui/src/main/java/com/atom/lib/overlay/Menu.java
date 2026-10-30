// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.overlay;

import com.atom.lib.component.UiElement;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.*;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import java.util.*;

/** A focusable menu content root; the host owns overlay placement and interception. */
public final class Menu implements UiElement,PointerTarget {
    public record Item(String id,String label,boolean enabled,Runnable action) {
        public Item { if(Objects.requireNonNull(id).isBlank())throw new IllegalArgumentException("Empty item id");Objects.requireNonNull(label);Objects.requireNonNull(action); }
    }
    private final NodeId id;
    private List<Item> items=List.of();
    private List<Rect> rows=List.of();
    private boolean open=true;
    private int selected=-1,pressed=-1;
    private HostContext host;
    private Rect clip=new Rect(0,0,0,0);
    private long revision;
    private long fontGeneration=-1;
    private com.atom.lib.style.ThemeSnapshot measuredTheme;
    private Object textService;
    private final LeafMeasure measure=this::measure;
    public Menu(String id,List<Item> items){this.id=new NodeId(id);setItems(items);}
    public void setItems(List<Item> items){var copy=List.copyOf(items);var seen=new HashSet<String>();for(var item:copy)if(!seen.add(item.id()))throw new IllegalArgumentException("Duplicate menu item: "+item.id());this.items=copy;selected=first();pressed=-1;revision++;}
    public boolean isOpen(){return open;}
    public void open(){open=true;pressed=-1;selected=first();}
    public void close(){open=false;pressed=-1;}
    public void overlayClosed(){close();}
    public String selectedItem(){return selected<0?null:items.get(selected).id();}
    @Override public NodeId id(){return id;}
    @Override public PointerTarget pointerTarget(){return this;}
    @Override public boolean enabled(){return open;}
    @Override public LayoutNode layout(HostContext host){
        this.host=host;long generation=host.fonts().generation();
        if(generation!=fontGeneration||!host.theme().equals(measuredTheme)||textService!=host.text()){revision++;fontGeneration=generation;measuredTheme=host.theme();textService=host.text();}
        float height=host.theme().spacing().controlHeight()*items.size()+host.theme().spacing().small()*2;
        return LayoutNode.leaf(id.value(),LayoutStyle.column().size(Length.auto(),Length.points(height)).flex(0,0,Length.auto()).behavior(false,!open,true,false),measure,revision);
    }
    private MeasuredSize measure(Constraints c){float pad=host.theme().spacing().small(),width=host.theme().spacing().controlHeight()*4;for(var item:items)width=Math.max(width,text(item.label(),host,Float.POSITIVE_INFINITY).size().width()+pad*2);return new MeasuredSize(c.width().constrain(width),c.height().constrain(host.theme().spacing().controlHeight()*items.size()+pad*2),0);}
    private static TextLayout text(String value,HostContext host,float width){return host.text().layout(new TextRequest(value,host.bodyFont(),host.theme().type().body(),host.theme().palette().text(),1,true),new Constraints(Float.isInfinite(width)?Constraints.Axis.undefined():Constraints.Axis.atMost(Math.max(0,width)),Constraints.Axis.undefined()));}
    @Override public void geometry(LayoutBox box,HostContext host){this.host=host;clip=box.clip().intersect(box.border());float pad=Math.min(host.theme().spacing().small(),box.content().width()/2);float h=host.theme().spacing().controlHeight();var rects=new ArrayList<Rect>();for(int i=0;i<items.size();i++)rects.add(new Rect(box.content().x()+pad,box.content().y()+pad+i*h,Math.max(0,box.content().width()-pad*2),h));rows=List.copyOf(rects);}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        if(!open)return;var palette=host.theme().palette();var border=box.border();
        try(var paint=new Paint().setAntiAlias(true)){
            canvas.drawRRect(RRect.makeXYWH(border.x(),border.y(),border.width(),border.height(),host.theme().radius().card()),paint.setColor(palette.card()));
            for(int i=0;i<rows.size();i++){
                var row=rows.get(i);var item=items.get(i);if(i==selected&&item.enabled())canvas.drawRRect(RRect.makeXYWH(row.x(),row.y(),row.width(),row.height(),host.theme().radius().control()),paint.setColor((palette.accent()&0xffffff)|0x44000000));
                float pad=host.theme().spacing().small();var layout=host.text().layout(new TextRequest(item.label(),host.bodyFont(),host.theme().type().body(),item.enabled()?palette.text():palette.muted(),1,true),new Constraints(Constraints.Axis.atMost(Math.max(0,row.width()-pad*2)),Constraints.Axis.atMost(row.height())));
                host.text().draw(canvas,layout,row.x()+pad,row.y()+Math.max(0,(row.height()-layout.size().height())/2));
            }
        }
    }
    private int hit(float x,float y){if(!clip.contains(x,y))return -1;for(int i=0;i<rows.size();i++)if(rows.get(i).contains(x,y)&&items.get(i).enabled())return i;return -1;}
    @Override public boolean press(PointerEvent event){if(!open||event.button()!=0)return false;pressed=hit(event.x(),event.y());if(pressed>=0)selected=pressed;return pressed>=0;}
    @Override public void drag(PointerEvent event){if(open){int at=hit(event.x(),event.y());if(at>=0)selected=at;}}
    @Override public void release(PointerEvent event){int at=pressed;pressed=-1;if(open&&at>=0&&hit(event.x(),event.y())==at)activate(at);}
    @Override public void cancel(){close();}
    @Override public InputResult key(KeyEvent event){
        if(!open||!event.pressed())return InputResult.PASS;
        switch(event.key()){
            case ESCAPE->close();case ENTER,SPACE->{if(selected>=0)activate(selected);}
            case HOME->selected=first();case END->{selected=-1;for(int i=items.size()-1;i>=0;i--)if(items.get(i).enabled()){selected=i;break;}}
            case UP,DOWN->{int delta=event.key()==KeyEvent.Key.UP?-1:1;for(int n=1;n<=items.size();n++){int at=Math.floorMod((selected<0?0:selected)+n*delta,items.size());if(items.get(at).enabled()){selected=at;break;}}}
            default->{return InputResult.PASS;}
        }return InputResult.CONSUMED;
    }
    private int first(){for(int i=0;i<items.size();i++)if(items.get(i).enabled())return i;return -1;}
    private void activate(int at){var item=items.get(at);if(!open||!item.enabled())return;close();item.action().run();}
}
