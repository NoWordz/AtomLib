package com.atom.lib.controls;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UiSessionTest {
    @Test void noDisplayedSnapshotCannotActivateAndWindowLossCancelsOnlyOnce(){
        try(var f=new ControlFixture()){
            assertEquals(InputResult.PASS,f.pointer(PointerEvent.Type.PRESS,100,100,0));f.show(480);f.press();
            var adapter=new ControlScreenAdapter(f.session);adapter.windowFocused(false);adapter.windowFocused(false);
            assertEquals(List.of(CancelReason.FOCUS_LOST),f.reasons);assertFalse(adapter.release(1000,1000,0));
        }
    }
    @Test void commitReentrantCloseNeverCancelsCompletedGesture(){
        try(var f=new ControlFixture()){
            int[] commits={0},cancels={0};
            var slider=new Slider(new NodeId("width"),f.context,f.model(480),v->""+v,new SliderEvents(){
                public void preview(float v){}
                public void commit(float a,float v){commits[0]++;f.session.close();}
                public void cancel(float v,CancelReason r){cancels[0]++;}
            });
            f.session.setControls(List.of(slider));f.slider=slider;f.show(480);f.press();f.move(f.thumb()+20);f.release(1000);
            assertEquals(1,commits[0]);assertEquals(0,cancels[0]);
        }
    }
    @Test void previewFailureRollsBackOnceAndSuppressesCleanupFailure(){
        try(var f=new ControlFixture()){
            var original=new IllegalStateException("preview");var cleanup=new IllegalStateException("rollback");int[] cancels={0};
            var slider=new Slider(new NodeId("width"),f.context,f.model(480),v->""+v,new SliderEvents(){
                public void preview(float v){throw original;}
                public void commit(float a,float v){fail("commit");}
                public void cancel(float v,CancelReason r){cancels[0]++;throw cleanup;}
            });f.session.setControls(List.of(slider));f.slider=slider;f.show(480);f.press();
            assertSame(original,assertThrows(IllegalStateException.class,()->f.move(f.thumb()+20)));
            assertArrayEquals(new Throwable[]{cleanup},original.getSuppressed());assertEquals(1,cancels[0]);
            f.session.cancel(CancelReason.CLOSED);assertEquals(1,cancels[0]);
        }
    }
    @Test void throwingRemovalCallbackStillInstallsTheReplacementAndClearsOldCapture(){
        try(var f=new ControlFixture()){
            var failure=new IllegalStateException("cancel");int[] cancels={0};
            var slider=new Slider(new NodeId("width"),f.context,f.model(480),v->""+v,new SliderEvents(){public void preview(float v){}public void commit(float a,float v){}public void cancel(float v,CancelReason r){cancels[0]++;throw failure;}});
            f.session.setControls(List.of(slider));f.slider=slider;f.show(480);f.press();
            var toggle=new Toggle(new NodeId("half"),f.context,new ToggleModel("模式","",false,true),v->{});
            assertSame(failure,assertThrows(IllegalStateException.class,()->f.session.setControls(List.of(toggle))));
            assertDoesNotThrow(()->f.show(480));assertEquals(List.of(toggle),f.session.controls());assertEquals(1,cancels[0]);
        }
    }
}
