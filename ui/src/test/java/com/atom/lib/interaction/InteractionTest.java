// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.interaction;
import com.atom.lib.component.ValueBinding;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.PointerEvent;
import com.atom.lib.layout.LayoutBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InteractionTest {
    static final class Binding<T> implements ValueBinding<T> {
        T value;int previews,commits,cancels;
        Binding(T value){this.value=value;}
        public T read(){return value;}
        public void preview(T value){this.value=value;previews++;}
        public void commit(T value){this.value=value;commits++;}
        public void cancel(T value){this.value=value;cancels++;}
    }
    static LayoutBox box(){var r=new Rect(0,0,300,180);return new LayoutBox(r,r,r,r,null,null);}
    @Test void nineAnchorsDragAndScaleCommitExactlyOnceAndCancelRestores() {
        var initial=new HudPlacement(HudAnchor.TOP_LEFT,0,0,1);
        var binding=new Binding<>(initial);
        var editor=new HudEditor("hud",60,20,10,binding);
        editor.geometry(box(),null);
        assertTrue(editor.press(new PointerEvent(PointerEvent.Type.PRESS,30,10,0)));
        editor.drag(new PointerEvent(PointerEvent.Type.MOVE,150,90,-1));
        editor.geometry(box(),null);
        assertEquals(HudAnchor.CENTER,editor.placement().anchor());
        assertEquals(new Rect(120,80,60,20),editor.itemBounds());
        editor.release(new PointerEvent(PointerEvent.Type.RELEASE,150,90,0));
        assertEquals(1,binding.commits);
        editor.beginEdit();editor.setScale(2);editor.geometry(box(),null);
        assertEquals(new Rect(90,70,120,40),editor.itemBounds());
        editor.cancel();assertEquals(1,binding.cancels);assertEquals(1,editor.placement().scale());
        for(HudAnchor anchor:HudAnchor.values()){
            editor.beginEdit();editor.setAnchor(anchor);editor.commit();editor.geometry(box(),null);
            assertEquals(anchor.x()*240,editor.itemBounds().x(),.001);
            assertEquals(anchor.y()*160,editor.itemBounds().y(),.001);
        }
        assertEquals(10,binding.commits);
    }
    @Test void editorsDoNotShareCaptureAndInvalidScaleIsRejected() {
        var a=new HudEditor("same",60,20,8,new Binding<>(new HudPlacement(HudAnchor.TOP_LEFT,0,0,1)));
        var b=new HudEditor("same",60,20,8,new Binding<>(new HudPlacement(HudAnchor.TOP_LEFT,0,0,1)));
        a.geometry(box(),null);b.geometry(box(),null);
        a.beginEdit();a.setOffset(20,30);
        assertEquals(0,b.placement().offsetX());
        assertThrows(IllegalArgumentException.class,()->a.setScale(Float.NaN));
        a.cancel();assertEquals(0,a.placement().offsetX());
    }
    @Test void editStartsFromLatestHostValueAndFailedPreviewRestoresOriginal(){
        var initial=new HudPlacement(HudAnchor.TOP_LEFT,0,0,1);
        var binding=new Binding<>(initial);var editor=new HudEditor("hud",60,20,8,binding);
        binding.value=new HudPlacement(HudAnchor.BOTTOM_RIGHT,10,20,1);
        editor.setScale(2);
        assertEquals(HudAnchor.BOTTOM_RIGHT,editor.placement().anchor());assertEquals(10,editor.placement().offsetX());
        editor.cancel();
        var failed=new ValueBinding<HudPlacement>(){
            public HudPlacement read(){return initial;}
            public void preview(HudPlacement value){throw new IllegalStateException("preview failure");}
            public void commit(HudPlacement value){fail("cannot commit failed preview");}
            public void cancel(HudPlacement value){assertEquals(initial,value);}
        };
        var other=new HudEditor("other",60,20,8,failed);
        assertThrows(IllegalStateException.class,()->other.setOffset(10,10));assertEquals(initial,other.placement());
        other.commit();
    }
}
