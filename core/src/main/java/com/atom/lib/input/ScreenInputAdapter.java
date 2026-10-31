package com.atom.lib.input;

import java.util.Objects;

/**
 * Turns screen-style pointer callbacks (double coordinates, one call per
 * event) into {@link InputDispatcher} calls, so a host screen forwards one
 * line per callback instead of mapping events itself. Deliberately free of
 * Minecraft types: the same adapter serves every loader, and the loaders
 * only differ in the arity of their scroll callback (three arguments on
 * 1.20.1, horizontal and vertical on 1.21.1).
 *
 * <p>Responsibilities stay where they live in the library: the host's
 * {@link HitTester} owns hit testing on press, the dispatcher owns capture
 * and gesture ownership, and the {@link PointerTarget}s own press/drag/
 * release reactions. Scroll is not a {@link PointerEvent} type, so wheel
 * events go to a host-supplied {@link ScrollHandler} instead of the
 * dispatcher; the handler can consult {@link InputDispatcher#captureOwner()}
 * if it wants to respect an active drag.</p>
 */
public final class ScreenInputAdapter {
    /** Receives wheel scroll in the adapter's coordinate space; false passes the scroll to the host's other handlers. */
    public interface ScrollHandler { boolean scroll(float x,float y,float dx,float dy); }
    private final InputDispatcher dispatcher;
    private double scale=1.0;
    private ScrollHandler scrollHandler=(x,y,dx,dy)->false;
    public ScreenInputAdapter(InputDispatcher dispatcher){this.dispatcher=Objects.requireNonNull(dispatcher);}
    private ScreenInputAdapter(InputDispatcher dispatcher,double scale,ScrollHandler scrollHandler){
        this.dispatcher=dispatcher;this.scale=scale;this.scrollHandler=scrollHandler;
    }
    /** Multiplier applied to incoming coordinates; default 1, which fits Minecraft's GUI-scaled screen coordinates when the viewport shares that space. */
    public ScreenInputAdapter scale(double multiplier){
        if(!Double.isFinite(multiplier)||multiplier<=0.0)throw new IllegalArgumentException("Invalid coordinate scale");
        return new ScreenInputAdapter(dispatcher,multiplier,scrollHandler);
    }
    /** Installs the wheel receiver; the default handler passes every scroll. */
    public ScreenInputAdapter onScroll(ScrollHandler handler){
        return new ScreenInputAdapter(dispatcher,scale,Objects.requireNonNull(handler));
    }
    /** Forwards a click; true means the dispatcher consumed it and the screen should stop handling the event. */
    public boolean press(double x,double y,int button){return dispatch(x,y,button,PointerEvent.Type.PRESS);}
    /** Forwards a drag while a button is held; true only when a captured gesture consumed the move. */
    public boolean drag(double x,double y,int button,double dx,double dy){return dispatch(x,y,button,PointerEvent.Type.MOVE);}
    /** Forwards a button release; true only when a captured gesture consumed it. */
    public boolean release(double x,double y,int button){return dispatch(x,y,button,PointerEvent.Type.RELEASE);}
    /** Forwards a wheel scroll; returns whatever the ScrollHandler decided. */
    public boolean scroll(double x,double y,double dx,double dy){return scrollHandler.scroll(unit(x),unit(y),(float)(dx*scale),(float)(dy*scale));}
    /** The screen is going away; ends any active gesture with a cancel, never a release, exactly like an interrupted gesture. */
    public void removed(){dispatcher.cancel();}
    /** Ends the active gesture without removing anything; safe to call with no gesture in flight. */
    public void cancel(){dispatcher.cancel();}
    private boolean dispatch(double x,double y,int button,PointerEvent.Type type){
        return dispatcher.dispatch(new PointerEvent(type,unit(x),unit(y),button))==InputResult.CONSUMED;
    }
    private float unit(double value){return (float)(value*scale);}
}
