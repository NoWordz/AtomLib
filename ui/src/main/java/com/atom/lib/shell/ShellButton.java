// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.shell;

import com.atom.lib.component.UiElement;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.*;
import com.atom.lib.text.*;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Shell-local action: its geometry is unchanged by the decorative press highlight. */
final class ShellButton implements UiElement,PointerTarget {
    private final NodeId id;
    private final String label;
    private final Runnable action;
    private final BooleanSupplier selected;
    private final SpringAnim press=new SpringAnim(400,1,.0001f,.001f);
    private Rect hit=new Rect(0,0,0,0),clip=hit;
    private TextRequest request;
    private TextLayout text;
    private HostContext host;
    private boolean pressed;
    ShellButton(String id,String label,Runnable action,BooleanSupplier selected){this.id=new NodeId(id);this.label=Objects.requireNonNull(label);this.action=Objects.requireNonNull(action);this.selected=Objects.requireNonNull(selected);}
    @Override public NodeId id(){return id;}
    @Override public PointerTarget pointerTarget(){return this;}
    @Override public LayoutNode layout(HostContext host){
        this.host=host;request=new TextRequest(label,host.bodyFont(),host.theme().type().body(),selected.getAsBoolean()?host.theme().palette().onAccent():host.theme().palette().text());
        float padding=host.theme().spacing().medium();
        return LayoutNode.leaf(id.value(),LayoutStyle.column().spacing(padding,0).limits(0,host.theme().spacing().controlHeight(),Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY).flex(0,1,Length.auto()),
                constraints->{var measured=host.text().layout(request,constraints).size();return new MeasuredSize(constraints.width().constrain(measured.width()),constraints.height().constrain(measured.height()),measured.baseline());},0);
    }
    @Override public void geometry(LayoutBox box,HostContext host){
        hit=box.hit();clip=box.clip();text=host.text().layout(request,new Constraints(Constraints.Axis.atMost(box.content().width()),Constraints.Axis.atMost(box.content().height())));
    }
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        press.setTarget(pressed?1:0);press.update(time,!host.theme().motion().reducedMotion());
        int color=selected.getAsBoolean()?host.theme().palette().accent():host.theme().palette().control();
        try(Paint paint=new Paint().setColor(color).setAlphaf(selected.getAsBoolean()?1:.25f+.5f*press.value())){
            var r=box.border();canvas.drawRRect(RRect.makeXYWH(r.x(),r.y(),r.width(),r.height(),host.theme().radius().control()),paint);
        }
        if(text!=null){var r=box.content();host.text().draw(canvas,text,r.x()+(r.width()-text.size().width())/2,r.y()+(r.height()-text.size().height())/2);}
    }
    @Override public boolean press(PointerEvent event){if(event.button()!=0)return false;pressed=true;return true;}
    @Override public void drag(PointerEvent event){}
    @Override public void release(PointerEvent event){if(!pressed)return;pressed=false;if(hit.contains(event.x(),event.y())&&clip.contains(event.x(),event.y())){action.run();if(host!=null)host.feedback().run();}}
    @Override public void cancel(){pressed=false;}
    @Override public InputResult key(KeyEvent event){if(event.pressed()&&(event.key()==KeyEvent.Key.ENTER||event.key()==KeyEvent.Key.SPACE)){action.run();return InputResult.CONSUMED;}return InputResult.PASS;}
}
