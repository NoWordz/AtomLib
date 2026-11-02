package com.atom.lib.controls.internal;
import com.atom.lib.input.*;
import java.util.Objects;
/** Stable pointer identity. Geometry and callbacks are supplied by its session. */
public final class ControlTarget implements PointerTarget {
    private final PointerTarget delegate;
    public ControlTarget(PointerTarget delegate){this.delegate=Objects.requireNonNull(delegate);}
    public boolean enabled(){return delegate.enabled();}
    public boolean press(PointerEvent e){return delegate.press(e);}
    public void drag(PointerEvent e){delegate.drag(e);}
    public void release(PointerEvent e){delegate.release(e);}
    public void cancel(){delegate.cancel();}
}
