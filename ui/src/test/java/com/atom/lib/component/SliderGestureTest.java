package com.atom.lib.component;

import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.NodeId;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SliderGestureTest {
    @Test void releasePreviewCanCancelOrDisableWithoutCommitting() {
        for(boolean disable:new boolean[]{false,true}){
            var binding=new Binding();var slider=new Slider(new NodeId("s"),new SliderSpec(0,100,1),binding,false);slider.setTrack(new Rect(0,0,100,20));
            var d=new InputDispatcher((x,y)->slider.id());d.setTargets(Map.of(slider.id(),slider));
            d.dispatch(new PointerEvent(PointerEvent.Type.PRESS,30,10,0));
            binding.onPreview=()->{if(disable)slider.setEnabled(false);else d.cancel();};
            d.dispatch(new PointerEvent(PointerEvent.Type.RELEASE,80,10,0));
            assertEquals(0,binding.commits);assertEquals(1,binding.cancels);assertEquals(20,binding.value);assertFalse(slider.active());assertNull(d.captureOwner());
        }
    }
    @Test void previewAndCommitExceptionsTerminateAndPreserveTheOriginalFailure() {
        for(boolean commitFailure:new boolean[]{false,true}){
            var binding=new Binding();var slider=new Slider(new NodeId("s"),new SliderSpec(0,100,1),binding,false);slider.setTrack(new Rect(0,0,100,20));
            var d=new InputDispatcher((x,y)->slider.id());d.setTargets(Map.of(slider.id(),slider));
            d.dispatch(new PointerEvent(PointerEvent.Type.PRESS,30,10,0));
            var primary=new IllegalStateException("host failed");
            if(commitFailure)binding.onCommit=()->{throw primary;};else binding.onPreview=()->{throw primary;};
            assertSame(primary,assertThrows(IllegalStateException.class,()->d.dispatch(new PointerEvent(PointerEvent.Type.RELEASE,80,10,0))));
            assertFalse(slider.active());assertNull(d.captureOwner());assertEquals(1,binding.cancels);assertEquals(20,binding.value);
            binding.onPreview=()->{};binding.onCommit=()->{};
            assertEquals(InputResult.CONSUMED,d.dispatch(new PointerEvent(PointerEvent.Type.PRESS,40,10,0)));d.cancel();
        }
    }
    @Test void resizeDuringDragUsesLatestTrackAndCommitsOnlyOnce() {
        var binding=new Binding();var slider=new Slider(new NodeId("width"),new SliderSpec(0,100,1),binding,false);
        slider.setTrack(new Rect(0,0,100,20));
        var dispatch=new InputDispatcher((x,y)->slider.id());dispatch.setTargets(Map.of(slider.id(),slider));
        dispatch.dispatch(new PointerEvent(PointerEvent.Type.PRESS,25,10,0));
        assertEquals(25,binding.value);
        slider.setTrack(new Rect(0,0,200,20));
        dispatch.dispatch(new PointerEvent(PointerEvent.Type.MOVE,100,10,-1));
        assertEquals(50,binding.value);assertEquals(0,binding.commits);
        dispatch.dispatch(new PointerEvent(PointerEvent.Type.RELEASE,100,10,0));
        dispatch.dispatch(new PointerEvent(PointerEvent.Type.RELEASE,100,10,0));
        assertEquals(50,binding.value);assertEquals(1,binding.commits);
    }
    @Test void modalCancellationRestoresOriginalValueWithoutCommit() {
        var binding=new Binding();var slider=new Slider(new NodeId("width"),new SliderSpec(0,100,1),binding,false);
        slider.setTrack(new Rect(0,0,100,20));
        var d=new InputDispatcher((x,y)->slider.id());d.setTargets(Map.of(slider.id(),slider));
        d.dispatch(new PointerEvent(PointerEvent.Type.PRESS,75,10,0));d.cancel();
        assertEquals(20,binding.value);assertEquals(1,binding.cancels);assertEquals(0,binding.commits);
        slider.setTrack(new Rect(0,0,0,20));
        d.dispatch(new PointerEvent(PointerEvent.Type.PRESS,1,1,0));
        assertEquals(20,binding.value);assertNull(d.captureOwner());
    }
    @Test void keyboardStepsQuantizeRelativeToTheMinimumAndClamp() {
        var binding=new Binding();binding.value=5;
        var slider=new Slider(new NodeId("s"),new SliderSpec(5,15,3),binding,true);
        slider.key(new KeyEvent(KeyEvent.Key.RIGHT,true,false,false,false));assertEquals(8,binding.value);
        slider.key(new KeyEvent(KeyEvent.Key.END,true,false,false,false));assertEquals(15,binding.value);
        assertEquals(2,binding.commits);
        assertThrows(IllegalArgumentException.class,()->new SliderSpec(1,1,1));
    }
    static final class Binding implements ValueBinding<Float> {
        float value=20;int commits,cancels;
        Runnable onPreview=()->{},onCommit=()->{};
        public Float read(){return value;}
        public void preview(Float next){value=next;onPreview.run();}
        public void commit(Float next){value=next;commits++;onCommit.run();}
        public void cancel(Float original){value=original;cancels++;}
    }
}
