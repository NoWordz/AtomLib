package com.atom.lib.controls;
import com.atom.lib.input.*;
import java.util.Objects;
/** Receives GUI units, no second pixel-ratio conversion. */
public final class ControlScreenAdapter {
    private final UiSession session;
    private boolean focused=true;
    public ControlScreenAdapter(UiSession session){this.session=Objects.requireNonNull(session);}
    public boolean press(double x,double y,int button){return pointer(PointerEvent.Type.PRESS,x,y,button);}
    public boolean drag(double x,double y,int button,double dx,double dy){return pointer(PointerEvent.Type.MOVE,x,y,button);}
    public boolean release(double x,double y,int button){return pointer(PointerEvent.Type.RELEASE,x,y,button);}
    private boolean pointer(PointerEvent.Type type,double x,double y,int button){return session.pointer(new PointerEvent(type,(float)x,(float)y,button))==InputResult.CONSUMED;}
    public boolean scroll(double x,double y,double dx,double dy){return session.wheel((float)x,(float)y,(float)dx,(float)dy)==InputResult.CONSUMED;}
    public boolean key(KeyEvent event){return session.key(event)==InputResult.CONSUMED;}
    public boolean character(int codePoint){return session.character(codePoint)==InputResult.CONSUMED;}
    public void hover(double x,double y){session.hover((float)x,(float)y);}
    public void windowFocused(boolean value){
        boolean lost=focused&&!value;focused=value;
        if(lost)try{session.cancel(CancelReason.FOCUS_LOST);}finally{session.resetPhysicalInputTracking();}
    }
    public void removed(){session.cancel(CancelReason.REMOVED);}
}
