package com.atom.lib.view;

import com.atom.lib.*;
import com.atom.lib.component.*;
import com.atom.lib.font.*;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.TextService;
import io.github.humbleui.skija.Canvas;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.yoga.Yoga.YGConfigGetInstanceCount;

class UiViewLifecycleTest {
    RuntimeHandle runtime;FontRegistry fonts;TextService text;HostContext host;
    @BeforeEach void setup(){runtime=AtomLibRuntime.open();fonts=new FontRegistry(runtime);text=new TextService(fonts);var id=new FontId("body");fonts.register(id,FontSource.file(Path.of(System.getProperty("atomlib.testFont"))));host=new HostContext(fonts,id,text,UiViewIntegrationTest.theme(18),()->{});}
    @AfterEach void cleanup(){text.close();fonts.close();runtime.close();}
    private UiView view(UiElement root){return new UiView(host,root,new Viewport(100,100,1));}
    private static UiElement box(String id,LayoutStyle style,UiElement... children){
        return new UiElement(){
            public NodeId id(){return new NodeId(id);}
            public List<UiElement> children(){return List.of(children);}
            public LayoutNode layout(HostContext host){return new LayoutNode(id(),style,children().stream().map(c->c.layout(host)).toList(),null,0);}
            public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){}
        };
    }
    @Test void hiddenScrollAndHiddenAncestorsCanHideAndShow() {
        var scrollStyle=LayoutStyle.column().behavior(false,false,true,true);
        var hidden=box("hidden",LayoutStyle.column().behavior(false,true,false,false),box("scroll",scrollStyle));
        try(var ui=view(box("root",LayoutStyle.column(),hidden))){
            assertDoesNotThrow(ui::refresh);assertEquals(1,ui.frame().paintOrder().size());
            ui.setRoot(box("root",LayoutStyle.column(),box("scroll",scrollStyle)));assertDoesNotThrow(ui::refresh);assertEquals(2,ui.frame().paintOrder().size());
        }
        try(var ui=view(box("root",LayoutStyle.column(),box("scroll",scrollStyle.behavior(false,true,true,true))))){assertDoesNotThrow(ui::refresh);}
    }
    @Test void closingFromPointerAndKeyboardActionsEndsTheEventNormally() {
        for(boolean keyboard:new boolean[]{false,true}){
            UiView[] reference={null};var button=new Button("close","关闭",()->reference[0].close());
            try(var ui=view(box("root",LayoutStyle.column(),button))){
                reference[0]=ui;ui.refresh();
                if(keyboard){ui.key(new KeyEvent(KeyEvent.Key.TAB,true,false,false,false));assertDoesNotThrow(()->ui.key(new KeyEvent(KeyEvent.Key.ENTER,true,false,false,false)));}
                else {var hit=ui.frame().box(button.id()).hit();ui.pointer(new PointerEvent(PointerEvent.Type.PRESS,hit.x()+1,hit.y()+1,0));assertDoesNotThrow(()->ui.pointer(new PointerEvent(PointerEvent.Type.RELEASE,hit.x()+1,hit.y()+1,0)));}
                assertThrows(IllegalStateException.class,ui::refresh);
            }
        }
    }
    @Test void closeReleasesNativeOwnerEvenIfCancelThrowsAndReentersClose() {
        int before=YGConfigGetInstanceCount();UiView[] reference={null};
        var slider=new Slider(new NodeId("s"),new SliderSpec(0,100,1),new ValueBinding<Float>(){
            float value=20;public Float read(){return value;}public void preview(Float v){value=v;}public void commit(Float v){value=v;}
            public void cancel(Float v){value=v;reference[0].close();throw new IllegalStateException("cancel failed");}
        },false);
        var ui=view(box("root",LayoutStyle.column(),slider));reference[0]=ui;ui.refresh();var track=slider.track();
        ui.pointer(new PointerEvent(PointerEvent.Type.PRESS,track.x()+1,track.y(),0));
        assertThrows(IllegalStateException.class,ui::close);assertEquals(before,YGConfigGetInstanceCount());assertDoesNotThrow(ui::close);
    }
    @Test void releasePreviewOpeningModalOrRemovingTheControlDoesNotCommit() {
        for(boolean modal:new boolean[]{false,true}){
            UiView[] reference={null};boolean[] interrupt={false};float[] value={20};int[] commits={0},cancels={0};
            var slider=new Slider(new NodeId("s"),new SliderSpec(0,100,1),new ValueBinding<Float>(){
                public Float read(){return value[0];}
                public void preview(Float v){value[0]=v;if(interrupt[0]){if(modal)reference[0].suspendInput(true);else reference[0].setRoot(box("root",LayoutStyle.column()));}}
                public void commit(Float v){commits[0]++;}public void cancel(Float v){cancels[0]++;value[0]=v;}
            },false);
            try(var ui=view(box("root",LayoutStyle.column(),slider))){
                reference[0]=ui;ui.refresh();var track=slider.track();ui.pointer(new PointerEvent(PointerEvent.Type.PRESS,track.x()+1,track.y(),0));interrupt[0]=true;
                ui.pointer(new PointerEvent(PointerEvent.Type.RELEASE,track.right()-1,track.y(),0));
                assertEquals(0,commits[0]);assertEquals(1,cancels[0]);assertEquals(20,value[0]);assertNull(ui.captureOwner());
            }
        }
    }
    @Test void tabRevealsFocusThroughEveryScrollAncestor() {
        var scroll=LayoutStyle.column().behavior(false,false,true,true);
        var filler=box("filler",LayoutStyle.column().size(Length.points(50),Length.points(200)).flex(0,0,Length.auto()));
        var target=new Button("focus","目标",()->{});
        var inner=box("inner",scroll.size(Length.points(100),Length.points(50)).flex(0,0,Length.auto()),target);
        try(var ui=view(box("outer",scroll,filler,inner))){
            ui.key(new KeyEvent(KeyEvent.Key.TAB,true,false,false,false));var box=ui.frame().box(target.id());
            assertEquals(target.id(),ui.focused());assertTrue(box.border().intersect(box.clip()).height()>0);
        }
    }
}
