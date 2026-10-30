package com.atom.lib.view;

import com.atom.lib.*;
import com.atom.lib.component.*;
import com.atom.lib.font.*;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.overlay.*;
import com.atom.lib.text.TextService;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.yoga.Yoga.YGConfigGetInstanceCount;

class UiViewOverlayTest {
    RuntimeHandle runtime; FontRegistry fonts; TextService text; HostContext host;
    @BeforeEach void setup(){runtime=AtomLibRuntime.open();fonts=new FontRegistry(runtime);text=new TextService(fonts);var id=new FontId("body");fonts.register(id,FontSource.file(Path.of(System.getProperty("atomlib.testFont"))));host=new HostContext(fonts,id,text,UiViewIntegrationTest.theme(18),()->{});}
    @AfterEach void cleanup(){text.close();fonts.close();runtime.close();}
    static class Element implements UiElement,PointerTarget {
        final NodeId id; LayoutStyle style; List<UiElement> children; boolean enabled=true; int presses,cancels,closes; final List<Boolean> focusEvents=new ArrayList<>(); String typed=""; int color;
        Element(String id,LayoutStyle style,UiElement... children){this.id=new NodeId(id);this.style=style;this.children=List.of(children);}
        public NodeId id(){return id;}
        public List<UiElement> children(){return children;}
        public LayoutNode layout(HostContext host){return new LayoutNode(id,style,children.stream().map(e->e.layout(host)).toList(),null,0);}
        public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){if(color!=0)try(var p=new Paint().setColor(color)){var r=box.border();canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height()),p);}}
        public PointerTarget pointerTarget(){return this;} public boolean enabled(){return enabled;}
        public boolean press(PointerEvent e){presses++;return true;} public void drag(PointerEvent e){} public void release(PointerEvent e){} public void cancel(){cancels++;}
        public void focusChanged(boolean value){focusEvents.add(value);} public void textInput(String value){typed+=value;}
        public void overlayClosed(){closes++;}
    }
    static LayoutStyle fixed(float w,float h){return LayoutStyle.column().size(Length.points(w),Length.points(h)).flex(0,0,Length.auto());}
    static KeyEvent key(KeyEvent.Key key){return new KeyEvent(key,true,false,false,false);}
    static void press(UiView ui,float x,float y){ui.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));}

    @Test void modalContentPaintsAboveBaseAndBlocksPointerUntilEscapeRestoresFocus(){
        var base=new Element("base",LayoutStyle.column());base.color=0xff0000ff;
        var popup=new Element("popup",fixed(80,40));popup.color=0xffff0000;
        try(var ui=new UiView(host,base,new Viewport(200,160,1));var surface=Surface.makeRasterN32Premul(200,160)){
            press(ui,2,2);ui.showOverlay(OverlaySpec.centered(popup).withSize(OverlaySize.COMPACT));
            assertNull(ui.captureOwner());assertEquals(1,base.cancels);assertEquals(popup.id(),ui.focused());
            ui.paint(surface.getCanvas(),new FrameTime(1,0,.016));
            var r=ui.frame().box(popup.id()).border();assertEquals(40,r.height(),.01);assertTrue(r.x()>=0&&r.bottom()<=160);
            try(var image=surface.makeImageSnapshot();var bitmap=Bitmap.makeFromImage(image)){assertEquals(0xffff0000,bitmap.getColor((int)r.x()+1,(int)r.y()+1));}
            assertEquals(InputResult.CONSUMED,ui.pointer(new PointerEvent(PointerEvent.Type.PRESS,0,0,0)));assertEquals(1,base.presses);
            press(ui,r.x()+1,r.y()+1);assertEquals(popup.id(),ui.captureOwner());
            ui.key(key(KeyEvent.Key.ESCAPE));assertTrue(ui.overlaySnapshot().isEmpty());assertNull(ui.captureOwner());assertEquals(1,popup.cancels);assertEquals(base.id(),ui.focused());
            assertFalse(ui.frame().paintOrder().contains(popup.id()));assertEquals(List.of(true,false,true),base.focusEvents);
        }
    }
    @Test void focusLifecycleAndTextFollowCurrentEnabledElementAndReplacementIdentity(){
        var a=new Element("a",fixed(80,40));var b=new Element("b",fixed(80,40));var root=new Element("root",LayoutStyle.column(),a,b);root.enabled=false;
        try(var ui=new UiView(host,root,new Viewport(200,160,1))){
            ui.key(key(KeyEvent.Key.TAB));assertEquals(InputResult.CONSUMED,ui.textInput("你🙂"));assertEquals("你🙂",a.typed);
            a.enabled=false;ui.refresh();assertEquals(b.id(),ui.focused());assertEquals(List.of(true,false),a.focusEvents);assertEquals(List.of(true),b.focusEvents);
            var replacement=new Element("b",fixed(80,40));ui.setRoot(replacement);ui.refresh();assertEquals(List.of(true,false),b.focusEvents);assertEquals(List.of(true),replacement.focusEvents);
            ui.close();assertEquals(List.of(true,false),replacement.focusEvents);
        }
    }
    @Test void overlayIdsAreUniqueAcrossBaseAndEveryLayerWithoutChangingTheStackOnFailure(){
        var base=new Element("base",LayoutStyle.column());
        try(var ui=new UiView(host,base,new Viewport(200,160,1))){
            assertThrows(IllegalArgumentException.class,()->ui.showOverlay(OverlaySpec.centered(new Element("base",fixed(10,10)))));
            var popup=new Element("popup",fixed(40,20));ui.showOverlay(OverlaySpec.centered(popup));
            assertThrows(IllegalArgumentException.class,()->ui.showOverlay(OverlaySpec.centered(new Element("other",fixed(40,20),new Element("popup",fixed(10,10))))));
            assertThrows(IllegalArgumentException.class,()->ui.setRoot(new Element("popup",LayoutStyle.column())));
            assertEquals(1,ui.overlaySnapshot().size());assertEquals(base.id(),ui.frame().paintOrder().get(0));
        }
    }
    @Test void anchorTracksDisplayedScrollAndResizeAndDisappearsWithItsAnchor(){
        var anchor=new Element("anchor",fixed(60,20));var filler=new Element("filler",fixed(60,200));filler.enabled=false;
        var base=new Element("base",LayoutStyle.column().behavior(false,false,true,true),anchor,filler);base.enabled=false;
        var popup=new Element("popup",fixed(70,20));
        try(var ui=new UiView(host,base,new Viewport(200,160,1))){
            ui.showOverlay(OverlaySpec.anchored(popup,anchor.id(),OverlayPlacement.BELOW_ANCHOR).withModal(false).withSize(OverlaySize.ANCHOR));
            assertEquals(20,ui.frame().box(popup.id()).border().y(),.01);
            ui.wheel(1,1,10);assertEquals(10,ui.frame().box(popup.id()).border().y(),.01);
            ui.setViewport(new Viewport(50,40,1));var r=ui.frame().box(popup.id()).border();assertTrue(r.x()>=0&&r.right()<=50&&r.bottom()<=40);
            ui.setRoot(new Element("base",LayoutStyle.column()));ui.refresh();assertTrue(ui.overlaySnapshot().isEmpty());
        }
    }
    @Test void disablingModalTargetCancelsItsCaptureAndRetrapsTabWithoutTouchingBase(){
        var base=new Element("base",LayoutStyle.column());var a=new Element("a",fixed(80,40));var b=new Element("b",fixed(80,40));var popup=new Element("popup",LayoutStyle.column(),a,b);popup.enabled=false;
        try(var ui=new UiView(host,base,new Viewport(200,160,1))){
            ui.key(key(KeyEvent.Key.TAB));ui.showOverlay(OverlaySpec.centered(popup));var r=ui.frame().box(a.id()).border();press(ui,r.x()+1,r.y()+1);a.enabled=false;ui.refresh();
            assertNull(ui.captureOwner());assertEquals(1,a.cancels);assertEquals(b.id(),ui.focused());ui.key(key(KeyEvent.Key.TAB));assertEquals(b.id(),ui.focused());
            b.enabled=false;ui.refresh();assertNull(ui.focused());assertEquals(InputResult.CONSUMED,ui.textInput("blocked"));assertEquals("",base.typed);
            ui.closeTopOverlay();assertEquals(base.id(),ui.focused());
        }
    }
    @Test void tallDialogIsBoundedScrollableAndTabRevealsTheLastTarget(){
        var first=new Element("first",fixed(60,40));var filler=new Element("filler",fixed(60,300));filler.enabled=false;var last=new Element("last",fixed(60,40));
        var popup=new Element("popup",LayoutStyle.column().flex(0,0,Length.auto()),first,filler,last);popup.enabled=false;
        try(var ui=new UiView(host,new Element("base",LayoutStyle.column()),new Viewport(200,120,1))){
            ui.showOverlay(OverlaySpec.centered(popup));assertEquals(first.id(),ui.focused());var firstBox=ui.frame().box(first.id());assertTrue(firstBox.clip().bottom()<=120);
            ui.key(key(KeyEvent.Key.TAB));var r=ui.frame().box(last.id());assertEquals(last.id(),ui.focused());assertTrue(r.border().intersect(r.clip()).height()>0);
            assertTrue(ui.wheel(100,60,-100)<0||ui.frame().box(first.id()).border().y()>firstBox.border().y()-300);
        }
    }
    @Test void closingCaptureMayOpenReplacementModalAndStillUsesOneStack(){
        var base=new Element("base",LayoutStyle.column());UiView[] ref={null};boolean[] replace={false};var next=new Element("next",fixed(60,30));
        var old=new Element("old",fixed(60,30)){public void cancel(){super.cancel();if(replace[0]){replace[0]=false;ref[0].showOverlay(OverlaySpec.centered(next));}}};
        int configs=YGConfigGetInstanceCount();
        try(var ui=new UiView(host,base,new Viewport(200,160,1))){ref[0]=ui;ui.showOverlay(OverlaySpec.centered(old));var r=ui.frame().box(old.id()).border();press(ui,r.x()+1,r.y()+1);replace[0]=true;ui.closeTopOverlay();
            assertEquals(List.of(next),ui.overlaySnapshot().stream().map(OverlaySpec::content).toList());assertEquals(next.id(),ui.focused());assertFalse(ui.frame().paintOrder().contains(old.id()));ui.key(key(KeyEvent.Key.TAB));assertEquals(next.id(),ui.focused());
        }
        assertEquals(configs,YGConfigGetInstanceCount());
    }
    @Test void centerUsesExplicitContentWidthAndHiddenContentCannotLeaveAnInvisibleModal(){
        var base=new Element("base",LayoutStyle.column());var popup=new Element("popup",fixed(80,40));
        try(var ui=new UiView(host,base,new Viewport(200,160,1))){
            ui.showOverlay(OverlaySpec.centered(popup));assertEquals(new Rect(60,60,80,40),ui.frame().box(popup.id()).border());
            popup.style=popup.style.behavior(false,true,false,false);ui.refresh();assertTrue(ui.overlaySnapshot().isEmpty());
            press(ui,1,1);assertEquals(base.id(),ui.captureOwner());
        }
    }
    @Test void tooltipDoesNotInterruptAnExistingBaseGestureOrStealFocus(){
        var base=new Element("base",LayoutStyle.column());var tooltip=new Element("tip",fixed(40,20));tooltip.enabled=false;
        try(var ui=new UiView(host,base,new Viewport(200,160,1))){
            press(ui,1,1);ui.showOverlay(OverlaySpec.anchored(tooltip,base.id(),OverlayPlacement.TOOLTIP));
            assertEquals(base.id(),ui.captureOwner());assertEquals(base.id(),ui.focused());assertEquals(0,base.cancels);
        }
    }
    @Test void anchorScrolledCompletelyOutOfClipClosesItsOverlay(){
        var anchor=new Element("anchor",fixed(60,20));var filler=new Element("filler",fixed(60,300));filler.enabled=false;
        var base=new Element("base",LayoutStyle.column().behavior(false,false,true,true),anchor,filler);base.enabled=false;
        try(var ui=new UiView(host,base,new Viewport(200,160,1))){
            ui.showOverlay(OverlaySpec.anchored(new Element("popup",fixed(40,20)),anchor.id(),OverlayPlacement.BELOW_ANCHOR).withModal(false));
            ui.wheel(1,1,50);assertTrue(ui.overlaySnapshot().isEmpty());
        }
    }
    @Test void hostServicesRemainBorrowedAcrossThemeChangesAndFiveArgumentConstruction(){
        var clipboard=new HostContext.Clipboard(){String value="";public String read(){return value;}public void write(String value){this.value=value;}};
        var extended=new HostContext(fonts,host.bodyFont(),text,host.theme(),()->{},clipboard,key->"translated:"+key);
        var changed=extended.withTheme(UiViewIntegrationTest.theme(24));changed.clipboard().write("shared");
        assertEquals("shared",extended.clipboard().read());assertEquals("translated:title",changed.translate("title"));assertEquals("title",host.translate("title"));
    }
    @Test void failingOverlayMeasurementRollsBackItsLayerAndNativeOwner(){
        var base=new Element("base",LayoutStyle.column());
        UiElement bad=new Element("bad",LayoutStyle.column()){
            public LayoutNode layout(HostContext host){return LayoutNode.leaf("bad",LayoutStyle.column(),c->{throw new IllegalStateException("bad measure");},0);}
        };
        try(var ui=new UiView(host,base,new Viewport(200,160,1))){
            ui.key(key(KeyEvent.Key.TAB));int before=YGConfigGetInstanceCount();assertThrows(IllegalStateException.class,()->ui.showOverlay(OverlaySpec.centered(bad)));
            assertTrue(ui.overlaySnapshot().isEmpty());assertEquals(before,YGConfigGetInstanceCount());assertEquals(base.id(),ui.focused());press(ui,1,1);assertEquals(base.id(),ui.captureOwner());
        }
    }
    @Test void longMeasuredTextKeepsItsFullNaturalHeightInsideTheScrollableOverlay(){
        var label=new Label("long-text","这是一段很长的中文文本。".repeat(100),Label.Tone.PRIMARY);
        try(var ui=new UiView(host,new Element("base",LayoutStyle.column()),new Viewport(160,100,1))){
            ui.showOverlay(OverlaySpec.centered(label));var before=ui.frame().box(label.id());assertTrue(before.border().height()>100);
            ui.wheel(80,50,200);assertEquals(before.border().y()-200,ui.frame().box(label.id()).border().y(),.01);
        }
    }
    @Test void overlayLifecycleRunsOnceOnEscapeAndOnViewCloseEvenWithoutPointerFocus(){
        var base=new Element("base",LayoutStyle.column());var a=new Element("a",fixed(80,40));a.enabled=false;var b=new Element("b",fixed(60,30));b.enabled=false;
        try(var ui=new UiView(host,base,new Viewport(200,160,1))){
            ui.showOverlay(OverlaySpec.centered(a));ui.key(key(KeyEvent.Key.ESCAPE));assertEquals(1,a.closes);assertFalse(ui.closeTopOverlay());
            ui.showOverlay(OverlaySpec.centered(b));ui.close();assertEquals(1,b.closes);ui.close();assertEquals(1,b.closes);
        }
    }
    @Test void modalEventRemainsConsumedWhenItsHandlerClosesTheLastLayer(){
        UiView[] ref={null};var popup=new Element("popup",fixed(80,40)){
            public InputResult key(KeyEvent event){ref[0].closeTopOverlay();return InputResult.PASS;}
        };
        try(var ui=new UiView(host,new Element("base",LayoutStyle.column()),new Viewport(200,160,1))){
            ref[0]=ui;ui.showOverlay(OverlaySpec.centered(popup));assertEquals(InputResult.CONSUMED,ui.key(key(KeyEvent.Key.ENTER)));assertTrue(ui.overlaySnapshot().isEmpty());
        }
    }
    @Test void nativeMeasurementCannotMutateTheViewLayerStack(){
        UiView[] ref={null};boolean[] once={true};
        var popup=new Element("popup",LayoutStyle.column()){
            public LayoutNode layout(HostContext host){return LayoutNode.leaf("popup",LayoutStyle.column(),c->{
                if(once[0]){once[0]=false;assertThrows(IllegalStateException.class,()->ref[0].showOverlay(OverlaySpec.centered(new Element("nested",fixed(10,10)))));assertThrows(IllegalStateException.class,ref[0]::closeTopOverlay);}
                return new MeasuredSize(c.width().constrain(80),30,0);
            },0);}
        };
        try(var ui=new UiView(host,new Element("base",LayoutStyle.column()),new Viewport(200,160,1))){ref[0]=ui;ui.showOverlay(OverlaySpec.centered(popup));assertEquals(1,ui.overlaySnapshot().size());}
    }
    @Test void oversizedSingleFocusablePopupStartsAtItsTopAndStableFramesReuseMeasurement(){
        int[] measures={0};LeafMeasure measure=c->{measures[0]++;return new MeasuredSize(c.width().constrain(80),400,0);};
        var popup=new Element("popup",LayoutStyle.column()){
            public LayoutNode layout(HostContext host){return LayoutNode.leaf("popup",LayoutStyle.column().flex(0,0,Length.auto()),measure,0);}
        };
        try(var ui=new UiView(host,new Element("base",LayoutStyle.column()),new Viewport(200,120,1))){
            ui.showOverlay(OverlaySpec.centered(popup));assertEquals(0,ui.frame().box(popup.id()).border().y(),.01);int before=measures[0];long revision=ui.frame().revision();
            for(int i=0;i<10;i++)assertEquals(revision,ui.frame().revision());assertEquals(before,measures[0]);
            ui.wheel(100,60,100);assertEquals(-100,ui.frame().box(popup.id()).border().y(),.01);
        }
    }
    @Test void blurMayCloseTheViewDuringTheRefreshThatPrecedesPainting(){
        UiView[] ref={null};var base=new Element("base",LayoutStyle.column()){
            public void focusChanged(boolean focused){super.focusChanged(focused);if(!focused)ref[0].close();}
        };
        try(var ui=new UiView(host,base,new Viewport(200,160,1));var surface=Surface.makeRasterN32Premul(200,160)){
            ref[0]=ui;ui.key(key(KeyEvent.Key.TAB));base.enabled=false;
            assertDoesNotThrow(()->ui.paint(surface.getCanvas(),new FrameTime(1,0,.016)));assertTrue(ui.isClosed());assertEquals(List.of(true,false),base.focusEvents);
        }
    }
    @Test void closeFailuresStillReleaseTheLayerRestoreFocusAndPreserveBothExceptions(){
        var primary=new IllegalStateException("cancel");var secondary=new IllegalArgumentException("overlayClosed");
        var popup=new Element("popup",fixed(80,40)){
            public void cancel(){super.cancel();throw primary;}
            public void overlayClosed(){super.overlayClosed();throw secondary;}
        };
        try(var ui=new UiView(host,new Element("base",LayoutStyle.column()),new Viewport(200,160,1))){
            ui.key(key(KeyEvent.Key.TAB));int before=YGConfigGetInstanceCount();ui.showOverlay(OverlaySpec.centered(popup));var r=ui.frame().box(popup.id()).border();press(ui,r.x()+1,r.y()+1);
            assertSame(primary,assertThrows(IllegalStateException.class,ui::closeTopOverlay));assertTrue(Arrays.asList(primary.getSuppressed()).contains(secondary));assertEquals(1,popup.cancels);assertEquals(1,popup.closes);
            assertEquals(before,YGConfigGetInstanceCount());assertTrue(ui.overlaySnapshot().isEmpty());assertNull(ui.captureOwner());assertEquals(new NodeId("base"),ui.focused());
        }
    }
    @Test void closingAViewCancelsEachCapturedOrFocusedTargetOnlyOnce(){
        for(boolean modal:new boolean[]{false,true}){
            var base=new Element("base",LayoutStyle.column());var popup=new Element("popup",fixed(80,40));
            try(var ui=new UiView(host,base,new Viewport(200,160,1))){
                if(modal)ui.showOverlay(OverlaySpec.centered(popup));var target=modal?popup:base;var r=ui.frame().box(target.id()).border();press(ui,r.x()+1,r.y()+1);
                ui.close();assertEquals(1,target.cancels);assertNull(ui.captureOwner());
            }
        }
    }
}
