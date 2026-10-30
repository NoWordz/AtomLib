// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.shell;
import com.atom.lib.component.*;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.interaction.TestHost;
import com.atom.lib.layout.NodeId;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.UiView;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class AppShellTest {
    private static Section page(String id){return new Section(id,Section.Kind.GROUP,List.of(new Label(id+".text","宿主页面",Label.Tone.PRIMARY)));}
    @Test void popWaitsForCompletionAndReversalPreservesMotion(){
        var shell=new AppShell("shell",page("root"),new ShellHeader("header","Host",null,List.of()),null);
        try(shell){
            shell.push(page("detail"));
            shell.advance(new FrameTime(1,0,.016),true);
            float p=shell.transitionValue(),v=shell.transitionVelocity();
            assertEquals(2,shell.depth());assertTrue(shell.pop());assertEquals(2,shell.depth());
            assertEquals(p,shell.transitionValue());assertEquals(v,shell.transitionVelocity());
            for(int i=2;i<500;i++)shell.advance(new FrameTime(i,0,.016),true);
            assertEquals(1,shell.depth());assertFalse(shell.pop());
        }
    }
    @Test void productionShellPaintsNestedPagesAndRoutesClicksThroughTheirSnapshot(){
        try(var h=new TestHost();var surface=Surface.makeRasterN32Premul(420,500)){
            var actions=new AtomicInteger();
            var button=new Button("page.button","Host action",actions::incrementAndGet);
            var root=new Section("page",Section.Kind.GROUP,List.of(button));
            var tabs=new TabBar("tabs",List.of(new TabSpec("one","One"),new TabSpec("two","Two"),new TabSpec("three","Three"),new TabSpec("four","Four")),"one",id->{});
            try(var shell=new AppShell("shell",root,new ShellHeader("head","可选壳",null,List.of()),tabs);
                var ui=new UiView(h.host,shell,new Viewport(420,500,1))){
                ui.paint(surface.getCanvas(),new FrameTime(1,0,.016));
                var local=shell.contentFrame().box(button.id()).hit();var body=shell.contentBounds();
                float x=body.x()+local.x()+local.width()/2,y=body.y()+local.y()+local.height()/2;
                ui.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));ui.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0));assertEquals(1,actions.get());
                var tabBox=ui.frame().box(new NodeId("tabs.tab.four")).hit();
                ui.pointer(new PointerEvent(PointerEvent.Type.PRESS,tabBox.x()+2,tabBox.y()+2,0));ui.pointer(new PointerEvent(PointerEvent.Type.RELEASE,tabBox.x()+2,tabBox.y()+2,0));assertEquals("four",tabs.selected());
                shell.push(page("detail"));ui.paint(surface.getCanvas(),new FrameTime(2,0,.016));
                assertEquals(2,shell.depth());
                for(int i=3;i<180;i++)ui.paint(surface.getCanvas(),new FrameTime(i,0,.016));
                assertFalse(shell.transitioning());assertTrue(shell.pop());assertEquals(2,shell.depth());
                ui.paint(surface.getCanvas(),new FrameTime(180,0,.016));
                float p=shell.transitionValue(),v=shell.transitionVelocity();assertTrue(shell.cancelPop());assertEquals(p,shell.transitionValue());assertEquals(v,shell.transitionVelocity());
                for(int i=181;i<400;i++)ui.paint(surface.getCanvas(),new FrameTime(i,0,.016));assertEquals(2,shell.depth());
            }
        }
    }
    @Test void headerPressAnimationAndActionsAreIsolatedAndSameFrameIsIdempotent(){
        try(var h=new TestHost();var a=Surface.makeRasterN32Premul(300,100);var b=Surface.makeRasterN32Premul(300,100)){
            var leftCalls=new AtomicInteger();var rightCalls=new AtomicInteger();
            var left=new ShellHeader("header","Host",new ShellAction("back","Back",leftCalls::incrementAndGet),List.of());
            var right=new ShellHeader("header","Host",new ShellAction("back","Back",rightCalls::incrementAndGet),List.of());
            try(var av=new UiView(h.host,left,new Viewport(300,100,1));var bv=new UiView(h.host,right,new Viewport(300,100,1))){
                av.paint(a.getCanvas(),new FrameTime(1,0,.016));bv.paint(b.getCanvas(),new FrameTime(1,0,.016));
                var hit=av.frame().box(new NodeId("header.back")).hit();float x=hit.x()+hit.width()/2,y=hit.y()+hit.height()/2;
                av.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));
                av.paint(a.getCanvas(),new FrameTime(2,0,.016));bv.paint(b.getCanvas(),new FrameTime(2,0,.016));
                int sampleX=(int)x,sampleY=(int)(hit.bottom()-4);
                int color=pixel(a,sampleX,sampleY);assertNotEquals(color,pixel(b,sampleX,sampleY));
                av.paint(a.getCanvas(),new FrameTime(2,0,.016));assertEquals(color,pixel(a,sampleX,sampleY));
                av.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0));assertEquals(1,leftCalls.get());assertEquals(0,rightCalls.get());
            }
        }
    }
    @Test void navigationCancelsCapturedPageGestureAndBusyPushDoesNotAlterStack(){
        try(var h=new TestHost();var surface=Surface.makeRasterN32Premul(300,200)){
            var calls=new AtomicInteger();var button=new Button("button","Run",calls::incrementAndGet);
            try(var shell=new AppShell("shell",new Section("root",Section.Kind.GROUP,List.of(button)),null,null);
                var ui=new UiView(h.host,shell,new Viewport(300,200,1))){
                ui.paint(surface.getCanvas(),new FrameTime(1,0,.016));var hit=shell.contentFrame().box(button.id()).hit();
                float x=hit.x()+hit.width()/2,y=hit.y()+hit.height()/2;
                ui.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));assertTrue(button.pressed());
                shell.push(page("detail"));assertFalse(button.pressed());
                assertThrows(IllegalStateException.class,()->shell.push(page("third")));assertEquals(2,shell.depth());
                ui.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0));assertEquals(0,calls.get());
                shell.close();shell.close();assertDoesNotThrow(h.fonts::generation);
            }
        }
    }
    private static int pixel(Surface surface,int x,int y){
        try(var image=surface.makeImageSnapshot();var bitmap=new Bitmap()){
            bitmap.allocN32Pixels(300,100);assertTrue(image.readPixels(bitmap));return bitmap.getColor(x,y);
        }
    }
    @Test void committedCharactersReachTheFocusedNestedTextField(){
        try(var h=new TestHost();var surface=Surface.makeRasterN32Premul(300,200)){
            String[] value={""};var field=new TextField("field",new ValueBinding<String>(){
                public String read(){return value[0];}public void preview(String next){value[0]=next;}
                public void commit(String next){value[0]=next;}public void cancel(String before){value[0]=before;}
            },new TextField.Clipboard(){public String read(){return "";}public void write(String text){}});
            try(var shell=new AppShell("shell",new Section("root",Section.Kind.GROUP,List.of(field)),null,null);
                var view=new UiView(h.host,shell,new Viewport(300,200,1))){
                view.paint(surface.getCanvas(),new FrameTime(1,0,.016));var hit=shell.contentFrame().box(field.id()).hit();
                float x=hit.x()+5,y=hit.y()+hit.height()/2;
                view.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));view.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0));
                assertEquals(InputResult.CONSUMED,view.textInput("中文"));assertEquals("中文",value[0]);
            }
        }
    }
}
