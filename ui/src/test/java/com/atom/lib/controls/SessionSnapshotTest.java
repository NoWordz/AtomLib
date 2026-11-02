package com.atom.lib.controls;
import com.atom.lib.design.atomchat.*;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SessionSnapshotTest {
    @Test void replacementIsInactiveUntilActuallyDisplayedAndFailedPaintDoesNotPublish(){
        try(var f=new ControlFixture()){
            f.show(480);var old=f.frame;float x=f.thumb(),y=f.track().y()+2;
            var replacement=new Slider(new NodeId("width"),f.context,f.model(480),v->""+v,new SliderEvents(){public void preview(float v){}public void commit(float a,float v){}public void cancel(float v,CancelReason r){}});
            f.session.setControls(List.of(replacement));assertEquals(InputResult.PASS,f.pointer(PointerEvent.Type.PRESS,x,y,0));
            f.slider=replacement;f.show(480);f.press();f.move(f.thumb()+10);f.session.cancel(CancelReason.ESCAPE);
            try(var surface=Surface.makeRasterN32Premul(480,860)){
                assertThrows(IllegalArgumentException.class,()->f.session.paint(surface.getCanvas(),f.frame,new Viewport(100,100,1),new FrameTime(++f.ticks,f.ticks*16000000,.016),false));
            }
            assertEquals(InputResult.CONSUMED,f.pointer(PointerEvent.Type.PRESS,x,y,0));
        }
    }
    @Test void twoSessionsWithSameIdsNeverShareFocusCapturePaletteOrClose(){
        try(var a=new ControlFixture();var b=new ControlFixture()){
            b.skin.updateStyle(new AtomChatControlStyle(0,false,0xffff5500,0xff333333,1,0xffeeeeee,0xffffffff,0xff111111));a.show(480);b.show(480);
            byte[] before=SliderRenderTest.render(a);b.key(KeyEvent.Key.TAB,true);b.press();b.move(b.thumb()+10);
            assertArrayEquals(before,SliderRenderTest.render(a));a.press();a.move(a.thumb()+10);a.session.close();b.release(1000);
            assertEquals(1,b.commits.size());assertEquals(1,a.cancels.size());
        }
    }
}
