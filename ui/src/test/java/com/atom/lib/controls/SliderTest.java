package com.atom.lib.controls;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SliderTest {
    @Test void continuousDragUsesNewDisplayedTravelWithoutRelayoutJump(){
        try(var f=new ControlFixture()){
            f.show(480);f.press();float x=f.thumb();f.move(x+10);assertEquals(1,f.previews.size());float previous=f.slider.previewValue();
            f.slider.update(f.model(previous));f.show(420);float width=f.track().width();
            f.move(x+10);assertEquals(previous,f.slider.previewValue());
            f.move(x+11);assertEquals(previous+200/width,f.slider.previewValue(),.001);
            f.release(1000);f.release(1000);assertEquals(1,f.commits.size());assertTrue(f.cancels.isEmpty());
        }
    }
    @Test void unchangedReleaseDoesNotSaveAndEscapeRollsBackOnce(){
        try(var f=new ControlFixture()){
            f.show(480);f.press();f.release(f.thumb());assertTrue(f.commits.isEmpty());
            f.press();f.move(f.thumb()+15);f.key(KeyEvent.Key.ESCAPE,true);f.release(1000);
            assertEquals(List.of(480f),f.cancels);assertEquals(List.of(CancelReason.ESCAPE),f.reasons);assertTrue(f.commits.isEmpty());
        }
    }
    @Test void keyboardRepeatsCommitOnceAfterAllAdjustmentKeysUp(){
        try(var f=new ControlFixture()){
            f.show(480);f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);
            f.key(KeyEvent.Key.RIGHT,true);f.key(KeyEvent.Key.RIGHT,true);f.key(KeyEvent.Key.UP,true);
            f.key(KeyEvent.Key.RIGHT,false);assertTrue(f.commits.isEmpty());f.key(KeyEvent.Key.UP,false);
            assertEquals(List.of(510f),f.commits);f.key(KeyEvent.Key.UP,false);assertEquals(1,f.commits.size());
        }
    }
    @Test void externalOverrideNeverWritesOldStartAndDensityChangeCancels(){
        try(var f=new ControlFixture()){
            f.show(480);f.press();f.move(f.thumb()+20);f.slider.update(f.model(550));
            assertEquals(List.of(550f),f.cancels);assertEquals(List.of(CancelReason.HOST_OVERRIDE),f.reasons);
            f.show(480);f.press();f.move(f.thumb()+20);f.show(480,1.5f);
            assertEquals(CancelReason.COORDINATE_SPACE_CHANGED,f.reasons.get(1));assertEquals(550f,f.cancels.get(1));
        }
    }
    @Test void identityReplacementAndDisableCancelExactlyOnce(){
        try(var f=new ControlFixture()){
            f.show(480);f.press();f.slider.update(new SliderModel("width","",480,400,600,10,false));
            assertEquals(List.of(CancelReason.DISABLED),f.reasons);f.release(500);assertTrue(f.commits.isEmpty());
            f.slider.update(f.model(480));f.show(480);f.press();f.session.setControls(List.of());assertEquals(CancelReason.REMOVED,f.reasons.get(1));
        }
    }
    @Test void disablingAPreviewEchoKeepsRollbackAndIdleEscapePassesToHost(){
        try(var f=new ControlFixture()){
            f.show(480);assertEquals(InputResult.PASS,f.key(KeyEvent.Key.ESCAPE,true));f.key(KeyEvent.Key.ESCAPE,false);
            f.press();f.move(f.thumb()+15);float preview=f.slider.previewValue();
            f.slider.update(new SliderModel("width","",preview,400,600,10,false));assertEquals(480,f.slider.previewValue());assertEquals(List.of(480f),f.cancels);
        }
    }
    @Test void smallFiniteBusinessRangesDoNotUsePixelSizedTolerance(){
        try(var f=new ControlFixture()){
            var slider=new Slider(new NodeId("width"),f.context,new SliderModel("精细值","",5e-8f,0,1e-7f,1e-8f,true),v->Float.toString((float)v),new SliderEvents(){public void preview(float v){f.previews.add(v);}public void commit(float a,float v){f.commits.add(v);}public void cancel(float v,CancelReason r){}});
            f.session.setControls(List.of(slider));f.slider=slider;f.show(480);f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);f.key(KeyEvent.Key.RIGHT,true);f.key(KeyEvent.Key.RIGHT,false);
            assertEquals(6e-8f,slider.previewValue(),1e-14);assertEquals(1,f.commits.size());
        }
    }
    @Test void coordinateChangeRollsBackBeforeTheHostRebuildsItsModelAndLayout(){
        try(var f=new ControlFixture()){
            f.show(480);f.press();f.move(f.thumb()+20);assertTrue(f.slider.previewValue()>480);
            f.session.prepare(new com.atom.lib.geometry.Viewport(480,860,1.5f));
            assertEquals(480,f.slider.previewValue());assertEquals(List.of(CancelReason.COORDINATE_SPACE_CHANGED),f.reasons);
            f.slider.update(f.model(f.slider.previewValue()));f.show(480,1.5f);assertEquals(1,f.cancels.size());assertTrue(f.commits.isEmpty());
        }
    }
}
