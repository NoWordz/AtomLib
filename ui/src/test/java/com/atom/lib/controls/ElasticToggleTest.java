package com.atom.lib.controls;

import com.atom.lib.design.atomdefault.AtomDefault;
import com.atom.lib.layout.NodeId;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Surface;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import com.atom.lib.input.KeyEvent;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElasticToggleTest {
    @Test void elasticSkinKeepsCancellationAndActivationTransactions() {
        try(var f=new ControlFixture()){
            var events=new java.util.ArrayList<Boolean>();
            var context=new ControlContext(f.text,AtomDefault.skin(AtomDefault.presets().get(3)));
            var t=new Toggle(new NodeId("switch"),context,new ToggleModel("Toggle","",false,true),events::add);
            f.session.setControls(List.of(t));f.show(480);
            f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);
            f.key(KeyEvent.Key.SPACE,true);assertTrue(events.isEmpty());
            t.update(new ToggleModel("Toggle","",false,false));
            f.key(KeyEvent.Key.SPACE,false);assertTrue(events.isEmpty());
            t.update(new ToggleModel("Toggle","",false,true));f.show(480);
            f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);
            f.key(KeyEvent.Key.SPACE,true);f.key(KeyEvent.Key.SPACE,true);f.key(KeyEvent.Key.SPACE,false);
            assertEquals(List.of(true),events);
        }
    }
    @Test void firstDisplayUsesLatestModelWithoutPlayingAStaleChange() {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(480,860)){
            var context=new ControlContext(f.text,AtomDefault.skin(AtomDefault.presets().get(3)));
            var t=new Toggle(new NodeId("switch"),context,new ToggleModel("Toggle","",false,true),ignored->{});
            f.session.setControls(List.of(t));
            t.update(new ToggleModel("Toggle","",true,true));
            var viewport=new com.atom.lib.geometry.Viewport(480,860,1);
            var tree=new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column().padding(15),List.of(t.node(LayoutStyle.column())),null,0));
            var frame=PresentationFrame.create(tree,f.engine.layout(tree,viewport),viewport,Map.of(),new PresentationFrame.Offset(0,0));
            f.session.paint(surface.getCanvas(),frame,viewport,new FrameTime(1,0,.016),true);
            assertEquals(1,t.position);
        }
    }
    @Test void returningAfterTheControlWasHiddenSettlesStaleMotion() throws Exception {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(480,860)){
            var context=new ControlContext(f.text,AtomDefault.skin(AtomDefault.presets().get(3)));
            var t=new Toggle(new NodeId("switch"),context,new ToggleModel("Toggle","",false,true),ignored->{});
            f.session.setControls(List.of(t));f.show(480);
            t.update(new ToggleModel("Toggle","",true,true));
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),true);
            var emptyTree=new LayoutTree(LayoutNode.leaf("empty",LayoutStyle.column(),null,0));
            var empty=PresentationFrame.create(emptyTree,f.engine.layout(emptyTree,f.viewport),f.viewport,Map.of(),new PresentationFrame.Offset(0,0));
            for(int i=0;i<40;i++)f.session.paint(surface.getCanvas(),empty,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),true);
            surface.getCanvas().clear(0);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),true);
            int returned=whiteEdge(surface,f,t);
            surface.getCanvas().clear(0);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,0),false);
            assertEquals(whiteEdge(surface,f,t),returned);
        }
    }
    @Test void staleGapDoesNotReplayAnOldTransition() throws Exception {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(480,860)){
            var context=new ControlContext(f.text,AtomDefault.skin(AtomDefault.presets().get(3)));
            var t=new Toggle(new NodeId("switch"),context,new ToggleModel("Toggle","",false,true),ignored->{});
            f.session.setControls(List.of(t));f.show(480);
            t.update(new ToggleModel("Toggle","",true,true));
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),true);
            surface.getCanvas().clear(0);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,1),true);
            int afterGap=whiteEdge(surface,f,t);
            surface.getCanvas().clear(0);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,0),false);
            assertEquals(whiteEdge(surface,f,t),afterGap,"A stale animation must reach the target");
        }
    }
    @Test void reversalKeepsMomentumAndReducedMotionSnapsOnSameFrame() throws Exception {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(480,860)){
            var context=new ControlContext(f.text,AtomDefault.skin(AtomDefault.presets().get(3)));
            var t=new Toggle(new NodeId("switch"),context,new ToggleModel("Toggle","",false,true),ignored->{});
            f.session.setControls(List.of(t));f.show(480);
            t.update(new ToggleModel("Toggle","",true,true));
            for(int i=0;i<8;i++)f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),true);
            surface.getCanvas().clear(0);
            var frame=new FrameTime(++f.ticks,f.ticks*16_000_000L,.016);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,frame,true);
            int before=whiteEdge(surface,f,t);
            t.update(new ToggleModel("Toggle","",false,true));
            surface.getCanvas().clear(0);
            frame=new FrameTime(++f.ticks,f.ticks*16_000_000L,.016);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,frame,true);
            assertTrue(whiteEdge(surface,f,t)>=before,"Reversal must preserve in-flight velocity");
            surface.getCanvas().clear(0);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,frame,false);
            int snapped=whiteEdge(surface,f,t);
            surface.getCanvas().clear(0);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),false);
            assertEquals(snapped,whiteEdge(surface,f,t),"Reduced motion must reach target immediately");
            assertTrue(snapped<before);
        }
    }
    @Test void knobOvershootsAndSettlesWhileColourProgressStaysBounded() throws Exception {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(480,860)){
            var context=new ControlContext(f.text,AtomDefault.skin(AtomDefault.presets().get(3)));
            var t=new Toggle(new NodeId("switch"),context,new ToggleModel("Toggle","",false,true),ignored->{});
            f.session.setControls(List.of(t));f.show(480);
            t.update(new ToggleModel("Toggle","",true,true));
            int furthest=0;
            for(int i=0;i<36;i++){
                surface.getCanvas().clear(0);
                f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),true);
                assertTrue(t.position>=0&&t.position<=1);
                furthest=Math.max(furthest,whiteEdge(surface,f,t));
            }
            for(int i=0;i<120;i++) f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),true);
            surface.getCanvas().clear(0);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),true);
            int settled=whiteEdge(surface,f,t);
            assertTrue(furthest>settled,"Knob must visibly overshoot its final position: "+furthest+" / "+settled);
        }
    }
    private int whiteEdge(Surface surface,ControlFixture f,Toggle t)throws Exception {
        var r=f.frame.box(new NodeId(t.id().value()+"/switch")).content();
        try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){
            var pixels=ImageIO.read(new ByteArrayInputStream(data.getBytes()));int right=-1;
            int y=Math.round(r.y()+r.height()/2);
            for(int x=Math.round(r.x());x<Math.min(pixels.getWidth(),r.right()+10);x++){
                int color=pixels.getRGB(x,y);
                if((color>>>24)>230&&(color>>16&255)>230&&(color>>8&255)>230&&(color&255)>230)right=x;
            }
            assertTrue(right>=0,"Knob pixels must be visible");return right;
        }
    }
}
