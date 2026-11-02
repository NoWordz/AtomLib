package com.atom.lib.controls;
import com.atom.lib.controls.internal.ControlSlots;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import io.github.humbleui.skija.Canvas;
import java.util.*;
import java.util.function.Consumer;

public final class Toggle extends Control {
    private ToggleModel model;
    private final Consumer<Boolean> change;
    private boolean pointer;
    private KeyEvent.Key activation;
    public Toggle(NodeId id,ControlContext context,ToggleModel model,Consumer<Boolean> onChange){
        super(id,context);this.model=Objects.requireNonNull(model);this.change=Objects.requireNonNull(onChange);position=model.checked()?1:0;
    }
    public void update(ToggleModel next){
        Objects.requireNonNull(next);var old=model;model=next;
        if(!next.enabled())terminate(CancelReason.DISABLED);
        else if(next.checked()!=old.checked())terminate(CancelReason.HOST_OVERRIDE);
    }
    public LayoutNode node(LayoutStyle style){return context.skin().toggleNode(id(),model,style,context.text(),presentation());}
    Set<NodeId> slots(){return Set.of(id(),ControlSlots.slot(id(),"title"),ControlSlots.slot(id(),"body"),ControlSlots.slot(id(),"switch"));}
    boolean enabled(){return model.enabled();}
    boolean interacting(){return pointer||activation!=null;}
    boolean press(PointerEvent e,PresentationFrame frame){if(e.button()!=0)return false;pressed=true;pointer=true;return true;}
    void drag(PointerEvent e,PresentationFrame frame){pressed=inside(e,frame);}
    void release(PointerEvent e,PresentationFrame frame){boolean activate=pointer&&pressed&&inside(e,frame);cancel(CancelReason.FOCUS_LOST);if(activate)change.accept(!model.checked());}
    private boolean inside(PointerEvent e,PresentationFrame f){var b=f.box(id());return b.hit().contains(e.x(),e.y())&&b.clip().contains(e.x(),e.y());}
    void cancel(CancelReason reason){pressed=false;pointer=false;activation=null;}
    boolean key(KeyEvent e){
        if(e.key()!=KeyEvent.Key.ENTER&&e.key()!=KeyEvent.Key.SPACE)return false;
        if(e.pressed()){if(!pointer&&activation==null){activation=e.key();pressed=true;}return true;}
        if(e.key()!=activation)return false;
        cancel(CancelReason.FOCUS_LOST);change.accept(!model.checked());return true;
    }
    float positionTarget(){return model.checked()?1:0;}
    void paint(Canvas c,PresentationFrame f,ControlVisual v){context.skin().paintToggle(c,f,id(),model,v,context.text(),presentation());}
}
