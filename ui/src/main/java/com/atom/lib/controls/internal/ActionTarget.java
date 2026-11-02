package com.atom.lib.controls.internal;
import com.atom.lib.input.*;
import java.util.function.*;
/** Internal complete-gesture activation for the two dialog actions and backdrop. */
public final class ActionTarget implements PointerTarget {
    private final BooleanSupplier enabled;
    private final Predicate<PointerEvent> inside;
    private final Runnable action;
    private boolean pressed;
    public ActionTarget(BooleanSupplier enabled,Predicate<PointerEvent> inside,Runnable action){this.enabled=enabled;this.inside=inside;this.action=action;}
    public boolean enabled(){return enabled.getAsBoolean();}
    public boolean pressed(){return pressed;}
    public boolean press(PointerEvent e){pressed=e.button()==0&&inside.test(e);return pressed;}
    public void drag(PointerEvent e){}
    public void release(PointerEvent e){boolean activate=pressed&&inside.test(e);pressed=false;if(activate)action.run();}
    public void cancel(){pressed=false;}
}
