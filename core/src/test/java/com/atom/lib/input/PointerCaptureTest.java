package com.atom.lib.input;

import com.atom.lib.layout.NodeId;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PointerCaptureTest {
    @Test void draggingOutsideStillReleasesOnceToOriginalOwner() {
        var id=new NodeId("slider");var dispatcher=new InputDispatcher((x,y)->x<100?id:null);
        var target=new RecordingTarget();dispatcher.setTargets(Map.of(id,target));
        assertEquals(InputResult.CONSUMED,dispatcher.dispatch(new PointerEvent(PointerEvent.Type.PRESS,20,10,0)));
        dispatcher.dispatch(new PointerEvent(PointerEvent.Type.MOVE,300,10,-1));
        dispatcher.dispatch(new PointerEvent(PointerEvent.Type.RELEASE,300,10,1));
        assertEquals(id,dispatcher.captureOwner());
        dispatcher.dispatch(new PointerEvent(PointerEvent.Type.RELEASE,300,10,0));
        dispatcher.dispatch(new PointerEvent(PointerEvent.Type.RELEASE,300,10,0));
        assertEquals(1,target.drags);assertEquals(1,target.releases);assertNull(dispatcher.captureOwner());
    }
    @Test void removalDisableAndModalInterruptionCancelWithoutCommit() {
        var id=new NodeId("slider");var dispatcher=new InputDispatcher((x,y)->id);
        var target=new RecordingTarget();dispatcher.setTargets(Map.of(id,target));
        dispatcher.dispatch(new PointerEvent(PointerEvent.Type.PRESS,20,10,0));
        dispatcher.setTargets(Map.of());dispatcher.cancel();
        assertEquals(1,target.cancels);assertEquals(0,target.releases);
        dispatcher.setTargets(Map.of(id,target));dispatcher.dispatch(new PointerEvent(PointerEvent.Type.PRESS,20,10,0));
        target.enabled=false;dispatcher.dispatch(new PointerEvent(PointerEvent.Type.MOVE,30,10,-1));
        assertEquals(2,target.cancels);assertNull(dispatcher.captureOwner());
    }
    @Test void emptySpacePassesAndAModalOpenedInsidePressCancelsTheGesture() {
        var id=new NodeId("button");var dispatcher=new InputDispatcher((x,y)->x<100?id:null);
        assertEquals(InputResult.PASS,dispatcher.dispatch(new PointerEvent(PointerEvent.Type.PRESS,300,10,0)));
        int[] cancellations={0};
        dispatcher.setTargets(Map.of(id,new PointerTarget(){
            public boolean press(PointerEvent e){dispatcher.cancel();return true;}
            public void drag(PointerEvent e){fail("cancelled gesture dragged");}
            public void release(PointerEvent e){fail("cancelled gesture committed");}
            public void cancel(){cancellations[0]++;}
        }));
        dispatcher.dispatch(new PointerEvent(PointerEvent.Type.PRESS,20,10,0));
        assertNull(dispatcher.captureOwner());assertEquals(1,cancellations[0]);
    }
    private static final class RecordingTarget implements PointerTarget {
        int drags,releases,cancels;boolean enabled=true;
        public boolean enabled(){return enabled;}
        public boolean press(PointerEvent event){return true;}
        public void drag(PointerEvent event){drags++;}
        public void release(PointerEvent event){releases++;}
        public void cancel(){cancels++;}
    }
}
