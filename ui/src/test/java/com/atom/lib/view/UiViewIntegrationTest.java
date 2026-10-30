package com.atom.lib.view;

import com.atom.lib.*;
import com.atom.lib.component.*;
import com.atom.lib.font.*;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.NodeId;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.overlay.OverlayHost;
import com.atom.lib.style.*;
import com.atom.lib.text.TextService;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UiViewIntegrationTest {
    static ThemeSnapshot theme(float body){return new ThemeSnapshot(new Palette(0xff12161c,0xff222831,0xff343b46,0xff4a90e2,0xffffffff,0xffeeeeee,0xffaaaaaa,0xffeb4034),new TypeScale(body,body+6,body-2),new SpacingScale(5,10,20,40),new RadiusScale(20,12),new MotionPolicy(false));}
    @Test void aRealSettingRowResizesDuringCaptureAndUsesOneCommit() {
        try(var runtime=AtomLibRuntime.open("integration");var fonts=new FontRegistry(runtime);var text=new TextService(fonts);var surface=Surface.makeRasterN32Premul(800,900)){
            var body=new FontId("body");fonts.register(body,FontSource.file(Path.of(System.getProperty("atomlib.testFont"))));
            UiView[] view={null};float[] value={480};int[] commits={0};
            var binding=new ValueBinding<Float>(){
                public Float read(){return value[0];}
                public void preview(Float v){value[0]=v;if(view[0]!=null)view[0].setViewport(new Viewport(v,800,1));}
                public void commit(Float v){value[0]=v;commits[0]++;}
                public void cancel(Float v){preview(v);}
            };
            var slider=new Slider(new NodeId("width"),new SliderSpec(320,640,1),binding,false);
            var root=new Section("root",Section.Kind.ROOT,List.of(new Label("title","布局设置",Label.Tone.TITLE),new SettingRow("row","面板宽度","中文长说明会随着布局宽度自动换行，不需要页面手算邻行坐标。",slider)));
            var host=new HostContext(fonts,body,text,theme(18),()->{});
            try(var ui=new UiView(host,root,new Viewport(480,800,1))){
                view[0]=ui;ui.paint(surface.getCanvas(),new FrameTime(1,0,.016));
                var before=slider.track();ui.pointer(new PointerEvent(PointerEvent.Type.PRESS,before.x()+before.width()*.25f,before.y(),0));
                var during=slider.track();assertNotEquals(before.width(),during.width());
                ui.pointer(new PointerEvent(PointerEvent.Type.MOVE,during.x()+during.width()*.75f,during.y(),-1));
                var latest=slider.track();
                ui.pointer(new PointerEvent(PointerEvent.Type.RELEASE,latest.x()+latest.width()*.75f,latest.y(),0));
                assertEquals(1,commits[0]);assertNull(ui.captureOwner());
                assertTrue(Float.isFinite(value[0]));
                var box=ui.frame().box(slider.id());assertTrue(box.hit().contains(slider.track().x(),slider.track().y()));
                ui.pointer(new PointerEvent(PointerEvent.Type.PRESS,slider.track().x(),slider.track().y(),0));
                ui.suspendInput(true);
                ui.pointer(new PointerEvent(PointerEvent.Type.RELEASE,1000,1000,0));
                assertEquals(1,commits[0]);assertNull(ui.captureOwner());
            }
        }
    }
    @Test void themeAndHostStateAreIsolatedAndStaticFramesDoNotMeasureAgain() {
        try(var runtime=AtomLibRuntime.open();var fonts=new FontRegistry(runtime);var text=new TextService(fonts);var surface=Surface.makeRasterN32Premul(400,400)){
            var id=new FontId("body");fonts.register(id,FontSource.file(Path.of(System.getProperty("atomlib.testFont"))));
            var label=new Label("label","同名节点的两个独立视图不会共享焦点。",Label.Tone.PRIMARY);
            var host=new HostContext(fonts,id,text,theme(16),()->{});
            try(var a=new UiView(host,new Section("root",Section.Kind.ROOT,List.of(label)),new Viewport(200,300,1));
                var b=new UiView(host,new Section("root",Section.Kind.ROOT,List.of(new Label("label","B",Label.Tone.PRIMARY))),new Viewport(200,300,1))){
                a.paint(surface.getCanvas(),new FrameTime(1,0,.016));long revision=a.frame().revision();float height=a.frame().box(label.id()).border().height();
                a.paint(surface.getCanvas(),new FrameTime(1,0,.016));assertEquals(revision,a.frame().revision());
                a.setTheme(theme(24));a.refresh();assertTrue(a.frame().box(label.id()).border().height()>height);
                b.refresh();long other=b.frame().revision();a.close();b.paint(surface.getCanvas(),new FrameTime(2,1,.016));assertEquals(other,b.frame().revision());
            }
        }
    }
}
