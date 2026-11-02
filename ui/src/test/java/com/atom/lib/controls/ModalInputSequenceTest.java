package com.atom.lib.controls;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModalInputSequenceTest {
    ConfirmationDialog dialog(ControlFixture f){return new ConfirmationDialog(new NodeId("confirm"),f.context,new ConfirmationModel("确认重置？","只增加演示计数，不修改文件。","取消","确认"));}
    @Test void defaultEnterCancelsAndFreshTabEnterConfirmsExactlyOnce(){
        try(var f=new ControlFixture()){
            List<ConfirmationResult> result=new ArrayList<>();f.show(480);f.session.open(dialog(f),result::add);f.show(480);
            f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,true);assertTrue(result.isEmpty());f.key(KeyEvent.Key.ENTER,false);
            assertEquals(List.of(ConfirmationResult.CANCELLED),result);f.show(480);
            f.session.open(dialog(f),result::add);f.show(480);f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);
            f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);f.key(KeyEvent.Key.ESCAPE,true);
            assertEquals(List.of(ConfirmationResult.CANCELLED,ConfirmationResult.CONFIRMED),result);
        }
    }
    @Test void openingInsideReleaseCallbackHasNoCancelAndClosingOwnsAllEventTails(){
        try(var f=new ControlFixture()){
            int[] saves={0};List<ConfirmationResult> result=new ArrayList<>();
            var slider=new Slider(new NodeId("width"),f.context,f.model(480),v->""+v,new SliderEvents(){
                public void preview(float v){}
                public void commit(float a,float v){saves[0]++;f.session.open(dialog(f),result::add);}
                public void cancel(float v,CancelReason r){fail("completed release cancelled");}
            });f.session.setControls(List.of(slider));f.slider=slider;f.show(480);f.press();f.move(f.thumb()+20);f.release(1000);
            assertEquals(1,saves[0]);f.show(480);assertThrows(IllegalStateException.class,()->f.session.open(dialog(f),r->{}));
            f.key(KeyEvent.Key.ESCAPE,true);assertEquals(List.of(ConfirmationResult.CANCELLED),result);
            assertEquals(InputResult.CONSUMED,f.session.wheel(0,0,0,1));assertEquals(InputResult.CONSUMED,f.session.character('中'));
            assertEquals(InputResult.CONSUMED,f.pointer(PointerEvent.Type.PRESS,50,50,0));f.show(480);
            assertEquals(InputResult.CONSUMED,f.pointer(PointerEvent.Type.RELEASE,50,50,0));assertEquals(1,saves[0]);
        }
    }
    @Test void openingDuringHeldPointerCancelsBaseAndEatsInitiatingRelease(){
        try(var f=new ControlFixture()){
            List<ConfirmationResult> result=new ArrayList<>();f.show(480);f.press();f.move(f.thumb()+10);f.session.open(dialog(f),result::add);
            assertEquals(List.of(CancelReason.MODAL_OPENED),f.reasons);f.show(480);
            var confirm=f.frame.box(new NodeId("confirm/confirm")).border();
            assertEquals(InputResult.CONSUMED,f.pointer(PointerEvent.Type.RELEASE,confirm.x()+2,confirm.y()+2,0));assertTrue(result.isEmpty());
            f.session.close();assertEquals(List.of(ConfirmationResult.CANCELLED),result);f.session.close();assertEquals(1,result.size());
        }
    }
    @Test void escapeBeforeFirstPaintIsIsolatedAndNoCallbackRepeatsAfterException(){
        try(var f=new ControlFixture()){
            int[] calls={0};f.show(480);f.session.open(dialog(f),r->{calls[0]++;throw new IllegalStateException("business");});
            assertEquals(InputResult.CONSUMED,f.session.character('a'));
            assertThrows(IllegalStateException.class,()->f.key(KeyEvent.Key.ESCAPE,true));assertEquals(1,calls[0]);
            f.key(KeyEvent.Key.ESCAPE,true);f.show(480);f.session.close();assertEquals(1,calls[0]);
        }
    }
    @Test void heldEnterFromBeforeDisplayMustNotBecomeANewConfirmation(){
        try(var f=new ControlFixture()){
            List<ConfirmationResult> result=new ArrayList<>();f.show(480);f.session.open(dialog(f),result::add);
            f.key(KeyEvent.Key.ENTER,true);f.show(480);f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);
            f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertTrue(result.isEmpty(),"A repeat cannot start a fresh activation");
            f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertEquals(List.of(ConfirmationResult.CONFIRMED),result);
        }
    }
    @Test void openingFromSliderPressAndKeyPreviewCancelsAndConsumesItsTail(){
        for(boolean keyboard:new boolean[]{false,true})try(var f=new ControlFixture()){
            List<ConfirmationResult> result=new ArrayList<>();int[] cancellations={0};
            var slider=new Slider(new NodeId("width"),f.context,f.model(480),v->""+v,new SliderEvents(){
                public void preview(float v){f.session.open(dialog(f),result::add);}
                public void commit(float a,float v){fail("interrupted transaction committed");}
                public void cancel(float v,CancelReason r){assertEquals(CancelReason.MODAL_OPENED,r);cancellations[0]++;}
            });f.session.setControls(List.of(slider));f.slider=slider;f.show(480);
            if(keyboard){f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);f.key(KeyEvent.Key.RIGHT,true);}
            else {var r=f.track();f.pointer(PointerEvent.Type.PRESS,r.right()-1,r.y()+2,0);}
            assertEquals(1,cancellations[0]);f.show(480);
            if(keyboard)f.key(KeyEvent.Key.RIGHT,false);else{var r=f.frame.box(new NodeId("confirm/confirm")).border();f.pointer(PointerEvent.Type.RELEASE,r.x()+1,r.y()+1,0);}
            assertTrue(result.isEmpty());
        }
    }
}
