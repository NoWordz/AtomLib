// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.overlay;

import com.atom.lib.component.UiElement;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.*;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import java.util.Objects;

/** Noninteractive content. The host decides anchor, delay, placement and lifetime. */
public final class Tooltip implements UiElement {
    private final NodeId id;
    private String text;
    private boolean visible=true;
    private HostContext host;
    private TextLayout displayed;
    private long revision;
    private long fontGeneration=-1;
    private com.atom.lib.style.ThemeSnapshot measuredTheme;
    private Object textService;
    private final LeafMeasure measure=this::measure;
    public Tooltip(String id,String text){this.id=new NodeId(id);this.text=Objects.requireNonNull(text);}
    public void show(String text){this.text=Objects.requireNonNull(text);visible=true;revision++;}
    public void hide(){visible=false;}
    public void overlayClosed(){hide();}
    public boolean isVisible(){return visible;}
    @Override public NodeId id(){return id;}
    @Override public LayoutNode layout(HostContext host){this.host=host;long generation=host.fonts().generation();if(generation!=fontGeneration||!host.theme().equals(measuredTheme)||textService!=host.text()){revision++;fontGeneration=generation;measuredTheme=host.theme();textService=host.text();}return LayoutNode.leaf(id.value(),LayoutStyle.column().flex(0,0,Length.auto()).behavior(false,!visible,true,false),measure,revision);}
    private TextLayout text(HostContext host,float width,float height){return host.text().layout(new TextRequest(text,host.bodyFont(),host.theme().type().caption(),host.theme().palette().text()),new Constraints(Constraints.Axis.atMost(Math.max(0,width)),Float.isInfinite(height)?Constraints.Axis.undefined():Constraints.Axis.atMost(Math.max(0,height))));}
    private MeasuredSize measure(Constraints c){float pad=host.theme().spacing().small();float width=c.width().mode()==Constraints.Mode.UNDEFINED?host.theme().spacing().controlHeight()*6:c.width().value();var value=text(host,width-pad*2,Float.POSITIVE_INFINITY);return new MeasuredSize(c.width().constrain(value.size().width()+pad*2),c.height().constrain(value.size().height()+pad*2),0);}
    @Override public void geometry(LayoutBox box,HostContext host){this.host=host;float pad=host.theme().spacing().small();displayed=text(host,box.content().width()-pad*2,box.content().height()-pad*2);}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){if(!visible)return;if(displayed==null)throw new IllegalStateException("Tooltip geometry not prepared");var border=box.border();try(var paint=new Paint().setAntiAlias(true).setColor(host.theme().palette().card())){canvas.drawRRect(RRect.makeXYWH(border.x(),border.y(),border.width(),border.height(),host.theme().radius().control()),paint);}float pad=host.theme().spacing().small();host.text().draw(canvas,displayed,box.content().x()+pad,box.content().y()+pad);}
}
