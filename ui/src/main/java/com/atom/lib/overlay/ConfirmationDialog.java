// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.overlay;

import com.atom.lib.component.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import java.util.*;

/** Composable modal content. Overlay ownership, focus trap and dimming belong to UiView. */
public final class ConfirmationDialog implements UiElement {
    private final NodeId id;
    private final List<UiElement> children;
    private final Runnable confirm,cancel;
    private boolean open=true;
    public ConfirmationDialog(String id,String title,String message,Runnable confirm,Runnable cancel){
        this.id=new NodeId(id);this.confirm=Objects.requireNonNull(confirm);this.cancel=Objects.requireNonNull(cancel);
        children=List.of(new Label(id+".title",title,Label.Tone.TITLE),new Label(id+".message",message,Label.Tone.PRIMARY),new Button(id+".cancel","Cancel",this::dismiss),new Button(id+".confirm","Confirm",this::confirm));
    }
    @Override public NodeId id(){return id;}
    @Override public List<UiElement> children(){return children;}
    public boolean isOpen(){return open;}
    public void open(){open=true;}
    public void confirm(){if(!open)return;open=false;confirm.run();}
    public void dismiss(){if(!open)return;open=false;cancel.run();}
    public void overlayClosed(){dismiss();}
    @Override public InputResult key(KeyEvent event){if(!open||!event.pressed())return InputResult.PASS;if(event.key()==KeyEvent.Key.ESCAPE){dismiss();return InputResult.CONSUMED;}if(event.key()==KeyEvent.Key.ENTER){confirm();return InputResult.CONSUMED;}return InputResult.PASS;}
    @Override public LayoutNode layout(HostContext host){float pad=host.theme().spacing().medium();return new LayoutNode(id,LayoutStyle.column().spacing(pad,pad).flex(0,0,Length.auto()).behavior(false,!open,true,false),children.stream().map(c->c.layout(host)).toList(),null,0);}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){if(!open)return;var b=box.border();try(var paint=new Paint().setAntiAlias(true).setColor(host.theme().palette().card())){canvas.drawRRect(RRect.makeXYWH(b.x(),b.y(),b.width(),b.height(),host.theme().radius().card()),paint);}}
}
