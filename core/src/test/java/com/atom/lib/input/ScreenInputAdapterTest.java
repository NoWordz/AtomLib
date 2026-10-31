package com.atom.lib.input;

import com.atom.lib.layout.NodeId;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ScreenInputAdapterTest {
    @Test void pressDragReleaseFlowReachesTheCapturedTargetOnce() {
        var id=new NodeId("button");var target=new RecordingTarget();
        var dispatcher=new InputDispatcher((x,y)->x<100?id:null);
        dispatcher.setTargets(Map.of(id,target));
        var input=new ScreenInputAdapter(dispatcher);
        assertTrue(input.press(20,10,0));
        assertTrue(input.drag(40,12,0,20,2));
        assertTrue(input.drag(300,12,0,260,0));
        assertTrue(input.release(300,12,0));
        assertEquals(300.0f,target.lastDragX);
        assertEquals(2,target.drags);
        assertEquals(1,target.releases);assertEquals(0,target.cancels);assertNull(dispatcher.captureOwner());
    }
    @Test void emptySpacePassesEveryCallbackAndStartsNoCapture() {
        var id=new NodeId("button");var target=new RecordingTarget();
        var dispatcher=new InputDispatcher((x,y)->x<100?id:null);
        dispatcher.setTargets(Map.of(id,target));
        var input=new ScreenInputAdapter(dispatcher);
        assertFalse(input.press(500,10,0));
        assertFalse(input.drag(510,10,0,10,0));
        assertFalse(input.release(510,10,0));
        assertEquals(0,target.drags);assertNull(dispatcher.captureOwner());
    }
    @Test void scaleMultipliesPointerCoordinatesBeforeTheDispatcherSeesThem() {
        var id=new NodeId("half");var target=new RecordingTarget();
        var dispatcher=new InputDispatcher((x,y)->x<50?id:null);
        dispatcher.setTargets(Map.of(id,target));
        var input=new ScreenInputAdapter(dispatcher).scale(0.5);
        assertTrue(input.press(80,10,0));
        assertEquals(40.0f,target.lastPressX);
        input=input.onScroll((x,y,dx,dy)->{target.lastScrollX=x;target.lastScrollDy=dy;return true;});
        assertTrue(input.scroll(80,10,0,4));
        assertEquals(40.0f,target.lastScrollX);assertEquals(2.0f,target.lastScrollDy);
    }
    @Test void scrollGoesToTheHandlerAndNeverTouchesTheGesture() {
        var id=new NodeId("button");var target=new RecordingTarget();
        var dispatcher=new InputDispatcher((x,y)->id);
        dispatcher.setTargets(Map.of(id,target));
        var input=new ScreenInputAdapter(dispatcher);
        assertFalse(input.scroll(20,10,0,1)); // default handler passes
        assertTrue(input.press(20,10,0));
        var seen=new boolean[1];
        input=input.onScroll((x,y,dx,dy)->{seen[0]=dispatcher.captureOwner()==id;return true;});
        assertTrue(input.scroll(20,10,-2,3));
        assertTrue(seen[0]);
        assertEquals(0,target.cancels); // scroll never touches the gesture
        assertTrue(input.release(20,10,0));
        assertEquals(1,target.releases);
    }
    @Test void removedEndsTheActiveGestureWithCancelNotRelease() {
        var id=new NodeId("slider");var target=new RecordingTarget();
        var dispatcher=new InputDispatcher((x,y)->id);
        dispatcher.setTargets(Map.of(id,target));
        var input=new ScreenInputAdapter(dispatcher);
        assertTrue(input.press(20,10,0));
        input.removed();
        assertEquals(1,target.cancels);assertEquals(0,target.releases);assertNull(dispatcher.captureOwner());
        input.removed(); // idempotent, like cancel with no gesture
        assertEquals(1,target.cancels);
    }
    @Test void wrongButtonReleaseDuringCapturePassesThroughLikeTheDispatcher() {
        var id=new NodeId("slider");var target=new RecordingTarget();
        var dispatcher=new InputDispatcher((x,y)->id);
        dispatcher.setTargets(Map.of(id,target));
        var input=new ScreenInputAdapter(dispatcher);
        assertTrue(input.press(20,10,0));
        assertFalse(input.release(20,10,1));
        assertTrue(input.release(20,10,0));
        assertEquals(1,target.releases);
    }
    @Test void scaleAndScrollHandlerValidateTheirArguments() {
        var input=new ScreenInputAdapter(new InputDispatcher((x,y)->null));
        assertThrows(IllegalArgumentException.class,()->input.scale(0));
        assertThrows(IllegalArgumentException.class,()->input.scale(Double.NaN));
        assertThrows(NullPointerException.class,()->input.onScroll(null));
        assertThrows(NullPointerException.class,()->new ScreenInputAdapter(null));
    }
    private static final class RecordingTarget implements PointerTarget {
        float lastPressX,lastDragX,lastScrollX,lastScrollDy;
        int drags,releases,cancels;
        public boolean press(PointerEvent event){lastPressX=event.x();return true;}
        public void drag(PointerEvent event){drags++;lastDragX=event.x();}
        public void release(PointerEvent event){releases++;}
        public void cancel(){cancels++;}
    }
}
