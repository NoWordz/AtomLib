package com.atom.lib.controls;

import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.PointerEvent;
import com.atom.lib.input.KeyEvent;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Surface;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntranceInteractionTest {
    @Test void keyboardCanActivateTheNewPageEvenAtZeroEntranceOpacity() {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(240,240)){
            int[] calls={0};
            var action=new ActionButton(new NodeId("action"),f.context,new ActionModel("Click",true,ActionModel.Role.SEGMENT,true),()->calls[0]++);
            f.session.setControls(List.of(action));
            var tree=new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column().padding(10),List.of(action.node(LayoutStyle.column().size(Length.points(120),Length.points(40)))),null,0));
            var viewport=new Viewport(240,240,1);var snapshot=f.engine.layout(tree,viewport);
            var frame=PresentationFrame.create(tree,snapshot,viewport,Map.of(),new PresentationFrame.Offset(0,0),Map.of(action.id(),new EntrancePose(0,12,0)));
            f.session.paint(surface.getCanvas(),frame,viewport,new FrameTime(1,0,.016),true);
            f.session.key(new KeyEvent(KeyEvent.Key.TAB,true,false,false,false));
            f.session.key(new KeyEvent(KeyEvent.Key.TAB,false,false,false,false));
            f.session.key(new KeyEvent(KeyEvent.Key.ENTER,true,false,false,false));
            f.session.key(new KeyEvent(KeyEvent.Key.ENTER,false,false,false,false));
            assertEquals(1,calls[0]);
        }
    }
    @Test void entranceDoesNotHideAnAlreadyOversizedControl() {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(240,240)){
            var action=new ActionButton(new NodeId("action"),f.context,new ActionModel("Click",true,ActionModel.Role.SEGMENT,true),()->{});
            f.session.setControls(List.of(action));
            var tree=new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column().padding(10),List.of(action.node(LayoutStyle.column().size(Length.points(300),Length.points(40)))),null,0));
            var viewport=new Viewport(240,240,1);var snapshot=f.engine.layout(tree,viewport);
            var frame=PresentationFrame.create(tree,snapshot,viewport,Map.of(),new PresentationFrame.Offset(0,0),Map.of(action.id(),new EntrancePose(0,10,.5f)));
            assertThrows(IllegalArgumentException.class,()->f.session.paint(surface.getCanvas(),frame,viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),true));
        }
    }
    @Test void entranceMayMovePartlyOutsideViewportAndRemainsClipped() {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(240,240)){
            var action=new ActionButton(new NodeId("action"),f.context,new ActionModel("Click",true,ActionModel.Role.SEGMENT,true),()->{});
            f.session.setControls(List.of(action));
            var tree=new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column().padding(10),List.of(action.node(LayoutStyle.column().size(Length.points(120),Length.points(40)))),null,0));
            var viewport=new Viewport(240,240,1);var snapshot=f.engine.layout(tree,viewport);
            var frame=PresentationFrame.create(tree,snapshot,viewport,Map.of(),new PresentationFrame.Offset(0,0),Map.of(action.id(),new EntrancePose(0,-20,.5f)));
            assertDoesNotThrow(()->f.session.paint(surface.getCanvas(),frame,viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),true));
            assertEquals(0,frame.box(action.id()).clip().y());
        }
    }
    @Test void fadedTranslatedActionPaintsAndAcceptsInputAtDisplayedPosition() throws Exception {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(240,240)){
            int[] activated={0};
            var action=new ActionButton(new NodeId("action"),f.context,new ActionModel("Click",true,ActionModel.Role.SEGMENT,true),()->activated[0]++);
            f.session.setControls(List.of(action));
            var tree=new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column().padding(10),List.of(action.node(LayoutStyle.column().size(Length.points(120),Length.points(40)))),null,0));
            var viewport=new Viewport(240,240,1);var snapshot=f.engine.layout(tree,viewport);
            var frame=PresentationFrame.create(tree,snapshot,viewport,Map.of(),new PresentationFrame.Offset(0,0),Map.of(action.id(),new EntrancePose(0,30,.5f)));
            f.session.paint(surface.getCanvas(),frame,viewport,new FrameTime(++f.ticks,f.ticks*16_000_000L,.016),false);
            var rect=frame.box(action.id()).hit();
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){
                var pixels=ImageIO.read(new ByteArrayInputStream(data.getBytes()));
                int alpha=pixels.getRGB(Math.round(rect.x()+10),Math.round(rect.y()+10))>>>24;
                assertTrue(alpha>=120&&alpha<=130,"Opacity is applied to the rendered control: "+alpha);
            }
            float x=rect.x()+10,y=rect.y()+10;
            f.session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));
            f.session.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0));
            assertEquals(1,activated[0]);
        }
    }
}
