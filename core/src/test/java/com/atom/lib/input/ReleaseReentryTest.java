package com.atom.lib.input;

import com.atom.lib.layout.NodeId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseReentryTest {
    @Test void releaseEndsCaptureBeforeReentrantRemovalOrCancel() {
        var id=new NodeId("slider");var dispatcher=new InputDispatcher((x,y)->id);
        int[] cancelled={0},released={0};
        dispatcher.setTargets(Map.of(id,new PointerTarget(){
            public boolean press(PointerEvent e){return true;}
            public void drag(PointerEvent e){}
            public void cancel(){cancelled[0]++;}
            public void release(PointerEvent e){
                assertNull(dispatcher.captureOwner());
                dispatcher.cancel();dispatcher.setTargets(Map.of());released[0]++;
            }
        }));
        dispatcher.dispatch(new PointerEvent(PointerEvent.Type.PRESS,0,0,0));
        dispatcher.dispatch(new PointerEvent(PointerEvent.Type.RELEASE,0,0,0));
        assertEquals(1,released[0]);assertEquals(0,cancelled[0]);
    }
}
