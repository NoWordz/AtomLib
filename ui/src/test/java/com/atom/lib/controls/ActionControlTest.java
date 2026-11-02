// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;
import com.atom.lib.layout.*;
import com.atom.lib.input.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActionControlTest {
    @Test void completeMouseOrKeyboardGestureActivatesOnceAndRepeatDoesNot(){
        try(var f=new ControlFixture()){
            int[] count={0};var a=new ActionButton(new NodeId("action"),f.context,new ActionModel("重置",true,ActionModel.Role.NORMAL),()->count[0]++);
            f.session.setControls(List.of(a));f.show(480);var r=f.frame.box(a.id()).border();
            f.pointer(PointerEvent.Type.PRESS,r.x()+2,r.y()+2,0);assertEquals(0,count[0]);f.pointer(PointerEvent.Type.RELEASE,r.x()+2,r.y()+2,0);assertEquals(1,count[0]);
            f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,true);assertEquals(1,count[0]);f.key(KeyEvent.Key.ENTER,false);assertEquals(2,count[0]);
        }
    }
    @Test void dragOutsideOrDisableCancelsActivation(){
        try(var f=new ControlFixture()){
            int[] count={0};var a=new ActionButton(new NodeId("action"),f.context,new ActionModel("重置",true,ActionModel.Role.NORMAL),()->count[0]++);
            f.session.setControls(List.of(a));f.show(480);var r=f.frame.box(a.id()).border();
            f.pointer(PointerEvent.Type.PRESS,r.x()+2,r.y()+2,0);f.pointer(PointerEvent.Type.MOVE,0,800,0);f.pointer(PointerEvent.Type.RELEASE,0,800,0);assertEquals(0,count[0]);
            f.pointer(PointerEvent.Type.PRESS,r.x()+2,r.y()+2,0);a.update(new ActionModel("重置",false,ActionModel.Role.NORMAL));f.pointer(PointerEvent.Type.RELEASE,r.x()+2,r.y()+2,0);assertEquals(0,count[0]);
        }
    }
}
