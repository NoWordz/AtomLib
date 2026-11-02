package com.atom.lib.controls;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.*;
import io.github.humbleui.skija.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ToggleRenderTest {
    @Test void pressedToggleChangesPixelsWithoutTogglingItsValue(){
        try(var f=new ControlFixture()){
            var t=new Toggle(new NodeId("half"),f.context,new ToggleModel("半屏模式","",false,true),v->{});f.session.setControls(List.of(t));f.show(480);
            f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);byte[] focused=SliderRenderTest.render(f);f.key(KeyEvent.Key.ENTER,true);
            assertFalse(Arrays.equals(focused,SliderRenderTest.render(f)),"Press feedback must precede activation");
        }
    }
    @Test void offTrackKeepsItsMaterialAlphaInsteadOfBecomingAnOpaqueWhitePill() throws Exception {
        try(var f=new ControlFixture()){
            var t=new Toggle(new NodeId("half"),f.context,new ToggleModel("半屏模式","",false,true),v->{});f.session.setControls(List.of(t));f.show(480);
            byte[] bytes=SliderRenderTest.render(f);var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));var r=f.frame.box(new NodeId("half/switch")).content();
            assertTrue((image.getRGB(Math.round(r.right()-5),Math.round(r.y()+r.height()/2))>>>24)<240,"Off track must preserve translucent material alpha");
        }
    }
    @Test void realToggleAnimationUsesFrameDeltaAndSameFrameIdIsIdempotent(){
        try(var f=new ControlFixture()){
            var t=new Toggle(new NodeId("half"),f.context,new ToggleModel("半屏模式","",false,true),v->{});f.session.setControls(List.of(t));f.show(480);
            t.update(new ToggleModel("半屏模式","",true,true));
            try(var surface=Surface.makeRasterN32Premul(480,860)){
                var time=new FrameTime(++f.ticks,f.ticks*16000000,.07);f.session.paint(surface.getCanvas(),f.frame,f.viewport,time,true);
                assertEquals(.5,t.position,.0001);f.session.paint(surface.getCanvas(),f.frame,f.viewport,time,true);assertEquals(.5,t.position,.0001);
                f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16000000,0),false);assertEquals(1,t.position);
            }
        }
    }
}
