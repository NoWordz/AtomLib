package com.atom.lib.controls;

import com.atom.lib.input.KeyEvent;
import com.atom.lib.layout.NodeId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowFocusBoundaryTest {
    private void key(ControlScreenAdapter adapter,KeyEvent.Key key,boolean down){
        adapter.key(new KeyEvent(key,down,false,false,false));
    }
    @Test void lostEnterKeyupDoesNotPoisonANewToggleGestureAfterFocusReturns(){
        try(var f=new ControlFixture()){
            var changes=new ArrayList<Boolean>();var toggle=new Toggle(new NodeId("mode"),f.context,new ToggleModel("模式","",false,true),changes::add);
            f.session.setControls(List.of(toggle));f.show(480);var adapter=new ControlScreenAdapter(f.session);
            key(adapter,KeyEvent.Key.TAB,true);key(adapter,KeyEvent.Key.TAB,false);key(adapter,KeyEvent.Key.ENTER,true);
            adapter.windowFocused(false);adapter.windowFocused(false);assertTrue(changes.isEmpty());
            // Windows may never deliver keyup for the old physical gesture.
            adapter.windowFocused(true);key(adapter,KeyEvent.Key.ENTER,true);key(adapter,KeyEvent.Key.ENTER,false);
            assertEquals(List.of(true),changes);
        }
    }
    @Test void lostArrowKeyupDoesNotPoisonANewAdjustmentAfterFocusReturns(){
        try(var f=new ControlFixture()){
            f.show(480);var adapter=new ControlScreenAdapter(f.session);
            key(adapter,KeyEvent.Key.TAB,true);key(adapter,KeyEvent.Key.TAB,false);key(adapter,KeyEvent.Key.RIGHT,true);
            adapter.windowFocused(false);adapter.windowFocused(false);assertEquals(List.of(480f),f.cancels);assertTrue(f.commits.isEmpty());
            adapter.windowFocused(true);key(adapter,KeyEvent.Key.RIGHT,true);key(adapter,KeyEvent.Key.RIGHT,false);
            assertEquals(List.of(490f),f.commits);assertEquals(List.of(CancelReason.FOCUS_LOST),f.reasons);
        }
    }
}
