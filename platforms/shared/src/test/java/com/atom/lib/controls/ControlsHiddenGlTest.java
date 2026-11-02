package com.atom.lib.controls;
import com.atom.lib.AtomLibRuntime;
import com.atom.lib.client.render.*;
import com.atom.lib.font.*;
import com.atom.lib.text.*;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import com.atom.lib.design.atomchat.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.input.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real bridge, framebuffer, three controls and input round trips; no Minecraft launch. */
public final class ControlsHiddenGlTest extends HiddenGlFixture {
    @Test void threeControlsPaintAndCompleteModalWithoutOwningHostFramebuffer(){
        var target=framebuffer(480,860);int[] changes={0},confirmed={0};
        try(var runtime=AtomLibRuntime.open("controls-gl");var fonts=new FontRegistry(runtime);var text=new TextService(fonts,TextBreakPolicy.CJK_UI);var engine=new YogaLayoutEngine();var modalEngine=new YogaLayoutEngine();var bridge=new SkijaRenderBridge(runtime)){
            fonts.registerDefaults();AtomChatFonts.register(fonts);var style=AtomChatControlStyle.defaults();var context=new ControlContext(text,new AtomChatControlSkin(style));
            var slider=new Slider(new NodeId("width"),context,new SliderModel("面板宽度","连续预览，松手提交。",480,400,600,10,true),v->Math.round(v)+" px",new SliderEvents(){public void preview(float v){}public void commit(float a,float v){changes[0]++;}public void cancel(float v,CancelReason r){}});
            var toggle=new Toggle(new NodeId("half"),context,new ToggleModel("半屏模式","独立宽度",false,true),v->changes[0]++);
            try(var session=new UiSession(List.of(slider,toggle))){
                var viewport=new Viewport(480,860,1);session.prepare(viewport);var tree=new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column().padding(15).spacing(15,12),List.of(slider.node(LayoutStyle.column()),toggle.node(LayoutStyle.column())),null,0));
                var base=PresentationFrame.create(tree,engine.layout(tree,viewport),viewport,Map.of(),new PresentationFrame.Offset(0,0));
                bridge.draw(target,c->{c.clear(style.panelBackgroundColor());session.paint(c,base,viewport,new FrameTime(1,1,.016),false);});
                var track=base.box(new NodeId("width/track")).content();float y=track.y()+track.height()/2;
                int[] color=pixel(target,Math.round(track.x()+5),860-1-Math.round(y));assertArrayEquals(new int[]{0x4a,0x90,0xe2,255},color);
                float x=track.x()+track.width()*.4f;session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));session.pointer(new PointerEvent(PointerEvent.Type.MOVE,x+10,y,0));session.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x+10,y,0));assertEquals(1,changes[0]);
                var t=base.box(toggle.id()).content();session.pointer(new PointerEvent(PointerEvent.Type.PRESS,t.x()+2,t.y()+2,0));session.pointer(new PointerEvent(PointerEvent.Type.RELEASE,t.x()+2,t.y()+2,0));assertEquals(2,changes[0]);
                session.open(new ConfirmationDialog(new NodeId("confirmation"),context,new ConfirmationModel("确认？","仅增加内存计数。","取消","确认")),r->{if(r==ConfirmationResult.CONFIRMED)confirmed[0]++;});
                var mt=new LayoutTree(session.modalNode(viewport));var composed=PresentationFrame.compose(List.of(base,PresentationFrame.create(mt,modalEngine.layout(mt,viewport),viewport,Map.of(),new PresentationFrame.Offset(0,0))));
                bridge.draw(target,c->{c.clear(style.panelBackgroundColor());session.paint(c,composed,viewport,new FrameTime(2,2,.016),false);});
                session.key(new KeyEvent(KeyEvent.Key.TAB,true,false,false,false));session.key(new KeyEvent(KeyEvent.Key.TAB,false,false,false,false));session.key(new KeyEvent(KeyEvent.Key.ENTER,true,false,false,false));session.key(new KeyEvent(KeyEvent.Key.ENTER,false,false,false,false));assertEquals(1,confirmed[0]);
                bridge.draw(target,c->session.paint(c,composed,viewport,new FrameTime(3,3,.016),false));assertNull(session.modalNode(viewport));
            }
            try(var capture=bridge.captureFrame(target)){assertNotNull(capture);} // host target remains usable after session close
        }
    }
}
