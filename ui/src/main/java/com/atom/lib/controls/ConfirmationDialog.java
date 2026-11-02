package com.atom.lib.controls;
import com.atom.lib.controls.internal.ControlSlots;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import io.github.humbleui.skija.Canvas;
import java.util.*;
/** Dialog data and skin; its two actions are owned by the session modal channel. */
public final class ConfirmationDialog extends Control {
    private ConfirmationModel model;
    public ConfirmationDialog(NodeId id,ControlContext context,ConfirmationModel model){super(id,context);this.model=Objects.requireNonNull(model);}
    public void update(ConfirmationModel model){this.model=Objects.requireNonNull(model);}
    public LayoutNode node(LayoutStyle outer){return context.skin().dialogNode(id(),model,outer,context.text());}
    Set<NodeId> slots(){var ids=new HashSet<NodeId>();ids.add(id());for(String role:List.of("title","body","icon","actions","cancel","confirm","cancel/text","confirm/text","overlay"))ids.add(ControlSlots.slot(id(),role));return Set.copyOf(ids);}
    boolean enabled(){return true;}
    boolean interacting(){return false;}
    boolean press(PointerEvent e,PresentationFrame f){return false;}
    void drag(PointerEvent e,PresentationFrame f){}
    void release(PointerEvent e,PresentationFrame f){}
    void cancel(CancelReason reason){}
    boolean key(KeyEvent e){return false;}
    void paint(Canvas c,PresentationFrame f,ControlVisual v){context.skin().paintDialog(c,f,id(),model,null,v,context.text());}
    void paint(Canvas c,PresentationFrame f,NodeId focused,ControlVisual v){context.skin().paintDialog(c,f,id(),model,focused,v,context.text());}
}
