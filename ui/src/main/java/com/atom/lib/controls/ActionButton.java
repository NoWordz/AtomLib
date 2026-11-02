// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import io.github.humbleui.skija.Canvas;
import java.util.*;
/** Shared activation transaction for buttons, segments and disclosure headers. */
public final class ActionButton extends Control {
    private ControlIcon icon;private float iconSize;
    private ActionModel model;private final Runnable action;private boolean pointer;private KeyEvent.Key activation;
    public ActionButton(NodeId id,ControlContext context,ActionModel model,Runnable action){super(id,context);this.model=Objects.requireNonNull(model);this.action=Objects.requireNonNull(action);}
    public void update(ActionModel next){Objects.requireNonNull(next);if(iconSize>0&&(!next.icon().isEmpty()||next.role()==ActionModel.Role.DISCLOSURE))throw new IllegalArgumentException("Image slot conflicts with legacy icon");model=next;if(!next.enabled())terminate(CancelReason.DISABLED);}
    public void icon(ControlIcon value,float size){
        if(!Float.isFinite(size)||size<0||(size==0&&value!=null))throw new IllegalArgumentException("Invalid image slot size");
        if(size>0&&(!model.icon().isEmpty()||model.role()==ActionModel.Role.DISCLOSURE))throw new IllegalArgumentException("Image slot conflicts with legacy icon");
        icon=value;iconSize=size;
    }
    public LayoutNode node(LayoutStyle style){return iconSize==0?context.skin().actionNode(id(),model,style,context.text()):context.skin().actionNode(id(),model,style,context.text(),iconSize);}
    Set<NodeId> slots(){return iconSize==0?Set.of(id(),new NodeId(id().value()+"/text")):Set.of(id(),new NodeId(id().value()+"/text"),new NodeId(id().value()+"/icon"));}
    boolean enabled(){return model.enabled();}
    boolean interacting(){return pointer||activation!=null;}
    boolean press(PointerEvent e,PresentationFrame f){if(e.button()!=0)return false;pointer=true;pressed=true;return true;}
    private boolean inside(PointerEvent e,PresentationFrame f){var b=f.box(id());return b.hit().contains(e.x(),e.y())&&b.clip().contains(e.x(),e.y());}
    void drag(PointerEvent e,PresentationFrame f){pressed=inside(e,f);}
    void release(PointerEvent e,PresentationFrame f){boolean invoke=pointer&&pressed&&inside(e,f);cancel(CancelReason.FOCUS_LOST);if(invoke)action.run();}
    void cancel(CancelReason reason){pointer=false;pressed=false;activation=null;}
    boolean key(KeyEvent e){if(e.key()!=KeyEvent.Key.ENTER&&e.key()!=KeyEvent.Key.SPACE)return false;if(e.pressed()){if(!pointer&&activation==null){activation=e.key();pressed=true;}return true;}if(activation!=e.key())return false;cancel(CancelReason.FOCUS_LOST);action.run();return true;}
    void paint(Canvas c,PresentationFrame f,ControlVisual v){if(iconSize==0)context.skin().paintAction(c,f,id(),model,v,context.text());else context.skin().paintAction(c,f,id(),model,v,context.text(),icon,iconSize);}
}
