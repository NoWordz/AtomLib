package com.atom.lib.controls;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ToggleTest {
    @Test void completeMouseAndKeyboardGesturesActivateOnce(){
        try(var f=new ControlFixture()){
            List<Boolean> values=new ArrayList<>();var t=new Toggle(new NodeId("half"),f.context,new ToggleModel("半屏模式","分别记忆宽度",false,true),values::add);
            f.session.setControls(List.of(t));f.show(480);var r=f.frame.box(t.id()).content();float x=r.x()+2,y=r.y()+2;
            f.pointer(PointerEvent.Type.PRESS,x,y,0);assertTrue(values.isEmpty());f.pointer(PointerEvent.Type.RELEASE,x,y,0);f.pointer(PointerEvent.Type.RELEASE,x,y,0);
            assertEquals(List.of(true),values);
            t.update(new ToggleModel("半屏模式","",true,true));f.show(480);
            f.key(KeyEvent.Key.SPACE,true);f.key(KeyEvent.Key.SPACE,true);assertEquals(1,values.size());f.key(KeyEvent.Key.SPACE,false);f.key(KeyEvent.Key.SPACE,false);
            assertEquals(List.of(true,false),values);
        }
    }
    @Test void draggedOutsideDisabledAndFocusLostNeverActivate(){
        try(var f=new ControlFixture()){
            List<Boolean> values=new ArrayList<>();var t=new Toggle(new NodeId("half"),f.context,new ToggleModel("半屏模式","",false,true),values::add);
            f.session.setControls(List.of(t));f.show(480);var r=f.frame.box(t.id()).content();float x=r.x()+2,y=r.y()+2;
            f.pointer(PointerEvent.Type.PRESS,x,y,0);f.pointer(PointerEvent.Type.MOVE,1000,1000,0);f.pointer(PointerEvent.Type.RELEASE,1000,1000,0);assertTrue(values.isEmpty());
            f.key(KeyEvent.Key.ENTER,true);f.session.cancel(CancelReason.FOCUS_LOST);f.key(KeyEvent.Key.ENTER,false);assertTrue(values.isEmpty());
            t.update(new ToggleModel("半屏模式","",false,false));f.show(480);f.pointer(PointerEvent.Type.PRESS,x,y,0);f.pointer(PointerEvent.Type.RELEASE,x,y,0);assertTrue(values.isEmpty());
        }
    }
    @Test void movingFocusWhileEnterIsHeldRequiresANewKeyGesture(){
        try(var f=new ControlFixture()){
            List<Boolean> values=new ArrayList<>();var a=new Toggle(new NodeId("a"),f.context,new ToggleModel("A","",false,true),values::add);var b=new Toggle(new NodeId("b"),f.context,new ToggleModel("B","",false,true),values::add);
            f.session.setControls(List.of(a,b));f.show(480);f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);f.key(KeyEvent.Key.ENTER,true);
            f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertTrue(values.isEmpty());
            f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertEquals(List.of(true),values);
        }
    }
}
