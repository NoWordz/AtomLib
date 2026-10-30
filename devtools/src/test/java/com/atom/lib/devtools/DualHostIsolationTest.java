package com.atom.lib.devtools;

import com.atom.lib.*;
import com.atom.lib.component.*;
import com.atom.lib.font.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.style.*;
import com.atom.lib.text.TextService;
import com.atom.lib.view.*;
import io.github.humbleui.skija.Surface;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DualHostIsolationTest {
    @Test void sameNodeIdsWithDifferentFontsThemesAndClocksStayIsolated(){
        try(var ra=AtomLibRuntime.open("host-a");var rb=AtomLibRuntime.open("host-b");
            var fa=new FontRegistry(ra);var fb=new FontRegistry(rb);var ta=new TextService(fa);var tb=new TextService(fb)){
            var id=new FontId("body");var path=Path.of(System.getProperty("atomlib.testFont"));
            fa.register(id,FontSource.file(path));fb.register(id,FontSource.file(path));
            var a=new HostContext(fa,id,ta,theme(16,0xff101010),()->{});
            var b=new HostContext(fb,id,tb,theme(28,0xffe0e0e0),()->{});
            UiView[] views={null,null};
            try(var va=new UiView(a,page(),new Viewport(240,400,1));var vb=new UiView(b,page(),new Viewport(320,500,1));
                var surface=Surface.makeRasterN32Premul(600,500)){
                views[0]=va;views[1]=vb;va.paint(surface.getCanvas(),new FrameTime(1,0,.016));
                vb.paint(surface.getCanvas(),new FrameTime(1,0,.016));
                long revB=vb.frame().revision();float heightB=vb.frame().box(new com.atom.lib.layout.NodeId("label")).border().height();
                long generationB=fb.generation();va.close();assertTrue(va.isClosed());
                vb.paint(surface.getCanvas(),new FrameTime(2,1,.016));
                assertEquals(revB,vb.frame().revision());assertEquals(heightB,vb.frame().box(new com.atom.lib.layout.NodeId("label")).border().height());
                assertEquals(generationB,fb.generation());
            }
        }
    }
    private static UiElement page(){return new Section("root",Section.Kind.ROOT,List.of(new Label("label","Shared node id, independent state",Label.Tone.PRIMARY)));}
    private static ThemeSnapshot theme(float body,int surface){return new ThemeSnapshot(
        new Palette(surface,surface+0x101010,surface+0x202020,0xff4a90e2,0xffffffff,0xff202020,0xff707070,0xffcc4444),
        new TypeScale(body,body+6,body-2),new SpacingScale(4,10,16,36),new RadiusScale(12,8),new MotionPolicy(true));}
}
