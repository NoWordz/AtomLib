package com.atom.lib.controls;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import java.util.*;
import io.github.humbleui.skija.Canvas;
/** Stable instance; node() produces data without recreating interaction state. */
public abstract sealed class Control permits Slider, Toggle, ConfirmationDialog, ActionButton {
    private final NodeId id;
    final ControlContext context;
    UiSession owner;
    boolean hovered,pressed;
    float hover,press,position;
    private ControlPresentation presentation=ControlPresentation.CARD;
    private boolean suspended;
    final boolean available(){return !suspended&&enabled();}
    final boolean suspended(){return suspended;}
    final void suspend(boolean value){if(suspended==value)return;suspended=value;if(value)terminate(CancelReason.REMOVED);}
    public final void presentation(ControlPresentation value){presentation=Objects.requireNonNull(value);}
    public final ControlPresentation presentation(){return presentation;}
    protected Control(NodeId id,ControlContext context){this.id=Objects.requireNonNull(id);this.context=Objects.requireNonNull(context);}
    public final NodeId id(){return id;}
    public abstract LayoutNode node(LayoutStyle style);
    abstract Set<NodeId> slots();
    abstract boolean enabled();
    abstract boolean interacting();
    abstract boolean press(PointerEvent e,PresentationFrame frame);
    abstract void drag(PointerEvent e,PresentationFrame frame);
    abstract void release(PointerEvent e,PresentationFrame frame);
    abstract void cancel(CancelReason reason);
    abstract boolean key(KeyEvent event);
    abstract void paint(Canvas c,PresentationFrame f,ControlVisual v);
    float positionTarget(){return 0;}
    void terminate(CancelReason reason){if(owner!=null)owner.cancelControl(this,reason);else cancel(reason);}
    void abort(Throwable original){try{terminate(CancelReason.CLOSED);}catch(RuntimeException|Error cleanup){if(original!=cleanup)original.addSuppressed(cleanup);}}
    void advance(double delta,boolean motion){
        var timing=context.skin().motion();
        hover=approach(hover,hovered?1:0,delta,timing.hover(),motion);
        press=approach(press,pressed?1:0,delta,timing.hover(),motion);
        position=approach(position,positionTarget(),delta,timing.toggle(),motion);
    }
    static float approach(float value,float target,double dt,double duration,boolean motion){
        if(!motion)return target;
        float next=value+Math.copySign(Math.min(Math.abs(target-value),(float)(dt/duration)),target-value);
        return Math.abs(next-target)<.001f?target:next;
    }
}
