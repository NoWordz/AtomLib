package com.atom.lib.controls;

import com.atom.lib.input.KeyEvent;
import com.atom.lib.layout.LayoutStyle;
import com.atom.lib.layout.*;
import com.atom.lib.input.PointerEvent;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ComposableModalTest {
    @Test void stableButtonCanBeActivatedTwiceInsideModal() {
        try (var f = new ControlFixture()) {
            var invoked = new AtomicInteger();
            var action = new ActionButton(new NodeId("pick/stone"), f.context,
                    new ActionModel("石头", true, ActionModel.Role.NORMAL), invoked::incrementAndGet);
            var dialog = new ModalDialog(new NodeId("pick"), f.context,
                    new ModalOptions("选择物品", 640, 16, true, action.id()));
            dialog.content(action.node(LayoutStyle.column()), List.of(action));
            dialog.actions(List.of());
            f.session.open(dialog, reason -> {});
            f.show(480);
            f.key(KeyEvent.Key.ENTER, true); f.key(KeyEvent.Key.ENTER, false);
            action.update(new ActionModel("石头", true, ActionModel.Role.NORMAL));
            f.show(480);
            f.key(KeyEvent.Key.ENTER, true); f.key(KeyEvent.Key.ENTER, false);
            assertEquals(2, invoked.get());
        }
    }

    @Test void wheelAndTabReachLastItemWithoutMovingBase(){
        try(var f=new ControlFixture()){
            var hits=new AtomicInteger();var list=new ArrayList<ActionButton>();
            for(int i=0;i<24;i++)list.add(new ActionButton(new NodeId("item/"+i),f.context,new ActionModel("Item "+i,true,ActionModel.Role.NORMAL),hits::incrementAndGet));
            var body=new LayoutNode(new NodeId("body"),LayoutStyle.column().spacing(0,2),list.stream().map(b->b.node(LayoutStyle.column())).toList(),null,0);
            var done=new ActionButton(new NodeId("done"),f.context,new ActionModel("Done",true,ActionModel.Role.NORMAL),hits::incrementAndGet);
            var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",300,10,true,list.get(0).id()));d.content(body,list);d.actions(List.of(done));
            f.show(380,320,1);f.session.open(d,r->{});f.show(380,320,1);
            var first=f.frame.box(list.get(0).id()).border();
            f.session.wheel(first.x()+2,first.y()+2,0,-100);f.show(380,320,1);
            var last=f.frame.box(list.get(23).id());assertTrue(last.clip().contains(last.hit().x()+last.hit().width()/2,last.hit().y()+last.hit().height()/2),"last item visible");
            assertEquals(0,f.session.wheel(0,0,0,0)==com.atom.lib.input.InputResult.PASS?1:0);
            var r=last.hit();float x=r.x()+r.width()/2,y=r.y()+r.height()/2;f.pointer(PointerEvent.Type.PRESS,x,y,0);f.pointer(PointerEvent.Type.RELEASE,x,y,0);assertEquals(1,hits.get());
            var end=f.frame.box(done.id());assertTrue(end.clip().contains(end.hit().x()+end.hit().width()/2,end.hit().y()+end.hit().height()/2));
            f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertEquals(2,hits.get());
        }
    }
    @Test void tabAutomaticallyScrollsOffscreenItemIntoView(){
        try(var f=new ControlFixture()){
            var calls=new AtomicInteger();var list=new ArrayList<ActionButton>();for(int i=0;i<20;i++)list.add(new ActionButton(new NodeId("row/"+i),f.context,new ActionModel("Row "+i,true,ActionModel.Role.NORMAL),calls::incrementAndGet));
            var body=new LayoutNode(new NodeId("body"),LayoutStyle.column(),list.stream().map(b->b.node(LayoutStyle.column())).toList(),null,0);
            var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",300,10,false,list.get(0).id()));d.content(body,list);
            f.session.setControls(List.of());f.session.open(d,r->{});f.show(380,320,1);
            for(int i=0;i<19;i++){f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);f.show(380,320,1);}
            var box=f.frame.box(list.get(19).id());var r=box.hit();assertTrue(box.clip().contains(r.x()+r.width()/2,r.y()+r.height()/2));
            f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertEquals(1,calls.get());
        }
    }
    @Test void modalWheelPreservesAnAlreadyScrolledBaseViewport(){
        try(var f=new ControlFixture()){
            var items=new ArrayList<ActionButton>();for(int i=0;i<20;i++)items.add(new ActionButton(new NodeId("base/"+i),f.context,new ActionModel("Base "+i,true,ActionModel.Role.NORMAL),()->{}));
            f.session.setControls(items);var scroll=new ScrollViewport();f.session.scrollViewport(scroll);
            var vp=new com.atom.lib.geometry.Viewport(480,320,1);var bounds=new com.atom.lib.geometry.Rect(0,0,480,100);
            var root=new LayoutNode(new NodeId("base-scroll"),LayoutStyle.column().size(Length.points(480),Length.auto()).behavior(false,false,true,true),items.stream().map(b->b.node(LayoutStyle.column())).toList(),null,0);
            var tree=new LayoutTree(root);var natural=f.engine.layoutIntrinsicHeight(tree,vp);var base=scroll.present(tree,natural,vp,bounds,1);
            try(var surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(480,320)){
                f.session.paint(surface.getCanvas(),base,vp,new com.atom.lib.motion.FrameTime(++f.ticks,f.ticks*16000000,.016),false);
                scroll.scrollBy(80);base=scroll.present(tree,natural,vp,bounds,1);f.session.paint(surface.getCanvas(),base,vp,new com.atom.lib.motion.FrameTime(++f.ticks,f.ticks*16000000,.016),false);float before=scroll.offset();assertTrue(before>0);
                var content=new ArrayList<ActionButton>();for(int i=0;i<20;i++)content.add(new ActionButton(new NodeId("modal/row"+i),f.context,new ActionModel("Item "+i,true,ActionModel.Role.NORMAL),()->{}));
                var body=new LayoutNode(new NodeId("modal/body"),LayoutStyle.column(),content.stream().map(b->b.node(LayoutStyle.column())).toList(),null,0);
                var dialog=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",300,10,false,null));dialog.content(body,content);f.session.open(dialog,r->{});
                var frame=PresentationFrame.compose(List.of(base,f.session.modalFrame(vp)));f.session.paint(surface.getCanvas(),frame,vp,new com.atom.lib.motion.FrameTime(++f.ticks,f.ticks*16000000,.016),false);
                var clip=frame.box(new NodeId("modal/scroll")).clip();f.session.wheel(clip.x()+2,clip.y()+2,0,-10);
                frame=PresentationFrame.compose(List.of(base,f.session.modalFrame(vp)));f.session.paint(surface.getCanvas(),frame,vp,new com.atom.lib.motion.FrameTime(++f.ticks,f.ticks*16000000,.016),false);assertEquals(before,scroll.offset());
            }
        }
    }
    @Test void backdropEscapeAndHeldTailAreIsolated(){
        try(var f=new ControlFixture()){
            var reasons=new ArrayList<ModalCloseReason>();var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",280,20,true,null));
            f.show(480);f.session.open(d,reasons::add);f.show(480);
            var card=f.frame.box(new NodeId("modal")).border();float x=card.x()+2,y=card.y()+2;
            f.pointer(PointerEvent.Type.PRESS,x,y,0);f.pointer(PointerEvent.Type.RELEASE,x,y,0);assertTrue(reasons.isEmpty());
            f.key(KeyEvent.Key.ESCAPE,true);assertEquals(List.of(ModalCloseReason.CANCELLED),reasons);
            f.show(480);f.key(KeyEvent.Key.ESCAPE,true);f.key(KeyEvent.Key.ESCAPE,false);assertEquals(1,reasons.size());
            f.session.open(d,reasons::add);f.show(480);f.pointer(PointerEvent.Type.PRESS,1,1,0);f.pointer(PointerEvent.Type.RELEASE,1,1,0);assertEquals(2,reasons.size());
        }
    }
    @Test void callbackFailureIsSingleAndHostCloseReleasesOwner(){
        try(var f=new ControlFixture()){
            var calls=new AtomicInteger();var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",280,20,false,null));
            f.session.open(d,r->{calls.incrementAndGet();throw new IllegalStateException("callback");});f.show(480);
            assertThrows(IllegalStateException.class,()->f.session.closeModal(ModalCloseReason.COMPLETED));
            f.session.closeModal(ModalCloseReason.CANCELLED);assertEquals(1,calls.get());
            f.session.open(d,r->{});
        }
    }
    @Test void callbackExceptionReleasesDialogAndControlsImmediately(){
        try(var f=new ControlFixture();var other=new UiSession(List.of())){
            var action=new ActionButton(new NodeId("choice"),f.context,new ActionModel("Choice",true,ActionModel.Role.NORMAL),()->{});
            var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",280,20,false,null));d.content(action.node(LayoutStyle.column()),List.of(action));
            f.session.open(d,r->{throw new IllegalStateException("callback");});f.show(480);
            assertThrows(IllegalStateException.class,()->f.session.closeModal(ModalCloseReason.CANCELLED));
            assertDoesNotThrow(()->other.open(d,r->{}));
        }
    }
    @Test void focusLostAndCoordinateChangeReportReasons(){
        try(var f=new ControlFixture()){
            var why=new AtomicReference<ModalCloseReason>();var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",280,20,false,null));
            f.show(480);f.session.open(d,why::set);f.show(480);f.session.cancel(CancelReason.FOCUS_LOST);assertEquals(ModalCloseReason.FOCUS_LOST,why.get());f.show(480);
            f.session.open(d,why::set);f.show(480);f.session.prepare(new com.atom.lib.geometry.Viewport(480,860,2));assertEquals(ModalCloseReason.COORDINATE_SPACE_CHANGED,why.get());
        }
    }

    @Test void reverseTabSkipsDisabledAndWraps(){
        try(var f=new ControlFixture()){
            var a=new AtomicInteger();var b=new AtomicInteger();
            var one=new ActionButton(new NodeId("one"),f.context,new ActionModel("one",true,ActionModel.Role.NORMAL),a::incrementAndGet);
            var disabled=new ActionButton(new NodeId("disabled"),f.context,new ActionModel("disabled",false,ActionModel.Role.NORMAL),()->fail("disabled activated"));
            var two=new ActionButton(new NodeId("two"),f.context,new ActionModel("two",true,ActionModel.Role.NORMAL),b::incrementAndGet);
            var body=new LayoutNode(new NodeId("body"),LayoutStyle.column(),List.of(one.node(LayoutStyle.column()),disabled.node(LayoutStyle.column()),two.node(LayoutStyle.column())),null,0);
            var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",280,20,false,one.id()));d.content(body,List.of(one,disabled,two));
            f.session.open(d,r->{});f.show(480);
            f.session.key(new KeyEvent(KeyEvent.Key.TAB,true,true,false,false));f.session.key(new KeyEvent(KeyEvent.Key.TAB,false,true,false,false));
            f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertEquals(1,b.get());assertEquals(0,a.get());
        }
    }
    @Test void failedChildPaintDoesNotPublishMovedHitGeometry(){
        try(var f=new ControlFixture()){
            var skin=new FailingSkin(f.skin);var context=new ControlContext(f.text,skin);var calls=new AtomicInteger();
            var button=new ActionButton(new NodeId("choice"),context,new ActionModel("Choice",true,ActionModel.Role.NORMAL),calls::incrementAndGet);
            var d=new ModalDialog(new NodeId("modal"),context,new ModalOptions("Choose",200,10,false,button.id()));d.content(button.node(LayoutStyle.column()),List.of(button));
            f.session.open(d,r->{});f.show(480);var old=f.frame.box(button.id()).hit();
            var vp=new com.atom.lib.geometry.Viewport(760,860,1);var tree=new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column().size(Length.points(760),Length.auto()).padding(15),f.session.controls().stream().map(c->c.node(LayoutStyle.column())).toList(),null,0));
            var base=PresentationFrame.create(tree,f.engine.layout(tree,vp),vp,java.util.Map.of(),new PresentationFrame.Offset(0,0));
            var next=PresentationFrame.compose(List.of(base,f.session.modalFrame(vp)));skin.fail=true;
            try(var surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(760,860)){
                int before=surface.getCanvas().getSaveCount();assertThrows(IllegalStateException.class,()->f.session.paint(surface.getCanvas(),next,vp,new com.atom.lib.motion.FrameTime(++f.ticks,f.ticks*16000000,.016),false));assertEquals(before,surface.getCanvas().getSaveCount());
            }
            skin.fail=false;float x=old.x()+old.width()/2,y=old.y()+old.height()/2;f.pointer(PointerEvent.Type.PRESS,x,y,0);f.pointer(PointerEvent.Type.RELEASE,x,y,0);assertEquals(1,calls.get());
        }
    }

    @Test void resizeCancelsModalDragWithoutClosing(){
        try(var f=new ControlFixture()){
            var why=new AtomicReference<ModalCloseReason>();f.session.setControls(List.of());
            var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",350,10,false,f.slider.id()));d.content(f.slider.node(LayoutStyle.column()),List.of(f.slider));
            f.session.open(d,why::set);f.show(480);f.press();f.move(f.track().right());assertFalse(f.previews.isEmpty());
            f.show(520);assertNull(why.get());assertEquals(1,f.cancels.size());
            f.show(520);assertNotNull(f.session.modalFrame(f.viewport));
        }
    }
    @Test void closingRestoresBaseFocusAndConsumesHeldEscape(){
        try(var f=new ControlFixture()){
            var hits=new AtomicInteger();var base=new ActionButton(new NodeId("base"),f.context,new ActionModel("Base",true,ActionModel.Role.NORMAL),hits::incrementAndGet);
            f.session.setControls(List.of(base));f.show(480);f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);
            var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",200,10,false,null));
            f.session.open(d,r->{});f.show(480);f.key(KeyEvent.Key.ESCAPE,true);f.show(480);
            f.key(KeyEvent.Key.ESCAPE,true);f.key(KeyEvent.Key.ESCAPE,false);assertEquals(0,hits.get());
            f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertEquals(1,hits.get());
        }
    }
    @Test void reservedModalSlotsCannotCollideWithBase(){
        try(var f=new ControlFixture()){
            var base=new ActionButton(new NodeId("modal/title"),f.context,new ActionModel("Base",true,ActionModel.Role.NORMAL),()->{});f.session.setControls(List.of(base));
            var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",200,10,false,null));
            assertThrows(IllegalArgumentException.class,()->f.session.open(d,r->{}));
        }
    }
    @Test void cancellationFailureDuringPaintDoesNotPublishNewHitBoxes(){
        try(var f=new ControlFixture()){
            f.session.setControls(List.of());var calls=new AtomicInteger();
            var slider=new Slider(new NodeId("modal-slider"),f.context,f.model(480),v->""+Math.round(v),new SliderEvents(){public void preview(float v){}public void commit(float a,float b){}public void cancel(float v,CancelReason r){throw new IllegalStateException("cancel");}});
            var button=new ActionButton(new NodeId("modal-button"),f.context,new ActionModel("Button",true,ActionModel.Role.NORMAL),calls::incrementAndGet);
            var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",350,10,false,null));
            d.content(new LayoutNode(new NodeId("body"),LayoutStyle.column(),List.of(slider.node(LayoutStyle.column()),button.node(LayoutStyle.column())),null,0),List.of(slider,button));
            f.session.open(d,r->{});f.show(480);var oldButton=f.frame.box(button.id()).hit();var track=f.frame.box(new NodeId("modal-slider/track")).content();
            f.pointer(PointerEvent.Type.PRESS,track.x()+track.width()*.4f,track.y()+track.height()/2,0);
            var spacer=LayoutNode.leaf("spacer",LayoutStyle.column().size(Length.auto(),Length.points(1400)),null,0);
            d.content(new LayoutNode(new NodeId("body"),LayoutStyle.column(),List.of(spacer,slider.node(LayoutStyle.column()),button.node(LayoutStyle.column())),null,0),List.of(slider,button));
            assertThrows(IllegalStateException.class,()->f.show(480));
            f.pointer(PointerEvent.Type.RELEASE,track.x()+10,track.y()+track.height()/2,0);
            float x=oldButton.x()+oldButton.width()/2,y=oldButton.y()+oldButton.height()/2;f.pointer(PointerEvent.Type.PRESS,x,y,0);f.pointer(PointerEvent.Type.RELEASE,x,y,0);assertEquals(1,calls.get());
        }
    }
    @Test void callbackCannotReopenBeforeRetreatAndHostCloseReleases(){
        try(var f=new ControlFixture()){
            var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",280,20,false,null));
            var reason=new AtomicReference<ModalCloseReason>();
            f.session.open(d,r->{reason.set(r);assertThrows(IllegalStateException.class,()->f.session.open(d,x->{}));});f.show(480);
            f.session.cancel(CancelReason.FOCUS_LOST);assertEquals(ModalCloseReason.FOCUS_LOST,reason.get());
            assertDoesNotThrow(()->f.session.open(d,x->{}));
        }
    }
    @Test void veryShortWindowAllowsScrollingToActions(){
        try(var f=new ControlFixture()){
            var hits=new AtomicInteger();var done=new ActionButton(new NodeId("done"),f.context,new ActionModel("Finish",true,ActionModel.Role.NORMAL),hits::incrementAndGet);
            var d=new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Choose",200,4,false,null));d.actions(List.of(done));
            f.session.setControls(List.of());f.session.open(d,r->{});f.show(240,80,1);
            var clip=f.frame.box(new NodeId("modal/scroll")).clip();
            f.session.wheel(clip.x()+2,clip.y()+2,0,-100);f.show(240,80,1);
            var box=f.frame.box(done.id());var r=box.hit();float x=r.x()+r.width()/2,y=r.y()+r.height()/2;
            assertTrue(box.clip().contains(x,y));f.pointer(PointerEvent.Type.PRESS,x,y,0);f.pointer(PointerEvent.Type.RELEASE,x,y,0);assertEquals(1,hits.get());
        }
    }
    @Test void customSkinOwnsModalSurface(){
        try(var f=new ControlFixture()){
            var skin=new FailingSkin(f.skin){
                public LayoutNode modalTitle(NodeId id,String title,com.atom.lib.text.TextService text){return LayoutNode.leaf(id.value(),LayoutStyle.column().size(Length.auto(),Length.points(12)),null,0);}
                public void paintModalSurface(io.github.humbleui.skija.Canvas c,PresentationFrame frame,NodeId id,String title,com.atom.lib.text.TextService text){var r=frame.box(id).border();try(var p=new io.github.humbleui.skija.Paint().setColor(0xff336699)){c.drawRect(io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height()),p);}}
            };
            var context=new ControlContext(f.text,skin);var count=new AtomicInteger();var button=new ActionButton(new NodeId("custom"),context,new ActionModel("Custom",true,ActionModel.Role.NORMAL),count::incrementAndGet);
            var d=new ModalDialog(new NodeId("modal"),context,new ModalOptions("Choose",210,10,false,button.id()));d.content(button.node(LayoutStyle.column()),List.of(button));
            f.session.open(d,r->{});f.show(480);f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertEquals(1,count.get());
        }
    }
    private static class FailingSkin implements ControlSkin {
        final ControlSkin delegate;boolean fail;FailingSkin(ControlSkin delegate){this.delegate=delegate;}
        public ControlMotion motion(){return delegate.motion();}
        public LayoutNode modalTitle(NodeId id,String title,com.atom.lib.text.TextService text){return delegate.modalTitle(id,title,text);}
        public void paintModalSurface(io.github.humbleui.skija.Canvas c,PresentationFrame f,NodeId id,String title,com.atom.lib.text.TextService text){delegate.paintModalSurface(c,f,id,title,text);}
        public LayoutStyle modalDialogStyle(com.atom.lib.geometry.Viewport v){return delegate.modalDialogStyle(v);}
        public LayoutNode actionNode(NodeId id,ActionModel m,LayoutStyle s,com.atom.lib.text.TextService t){return delegate.actionNode(id,m,s,t);}
        public void paintAction(io.github.humbleui.skija.Canvas c,PresentationFrame f,NodeId id,ActionModel m,ControlVisual v,com.atom.lib.text.TextService t){if(fail)throw new IllegalStateException("paint");delegate.paintAction(c,f,id,m,v,t);}
        public LayoutNode sliderNode(NodeId id,SliderModel m,String v,LayoutStyle s,com.atom.lib.text.TextService t){return delegate.sliderNode(id,m,v,s,t);}
        public void paintSlider(io.github.humbleui.skija.Canvas c,PresentationFrame f,NodeId id,SliderModel m,float v,String label,ControlVisual visual,com.atom.lib.text.TextService t){delegate.paintSlider(c,f,id,m,v,label,visual,t);}
        public LayoutNode toggleNode(NodeId id,ToggleModel m,LayoutStyle s,com.atom.lib.text.TextService t){return delegate.toggleNode(id,m,s,t);}
        public void paintToggle(io.github.humbleui.skija.Canvas c,PresentationFrame f,NodeId id,ToggleModel m,ControlVisual v,com.atom.lib.text.TextService t){delegate.paintToggle(c,f,id,m,v,t);}
        public LayoutNode dialogNode(NodeId id,ConfirmationModel m,LayoutStyle s,com.atom.lib.text.TextService t){return delegate.dialogNode(id,m,s,t);}
        public void paintDialog(io.github.humbleui.skija.Canvas c,PresentationFrame f,NodeId id,ConfirmationModel m,NodeId focus,ControlVisual v,com.atom.lib.text.TextService t){delegate.paintDialog(c,f,id,m,focus,v,t);}
    }
}
