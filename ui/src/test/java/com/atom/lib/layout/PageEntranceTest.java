package com.atom.lib.layout;

import com.atom.lib.geometry.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import com.atom.lib.motion.FrameTime;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageEntranceTest {
    @Test void differentFrameRatesReachTheSamePoseAtTheSameTime() {
        var a=new PageEntrance(.2,.05,8);var b=new PageEntrance(.2,.05,8);
        var id=new NodeId("item");a.start(new FrameTime(1,0,0),List.of(id));b.start(new FrameTime(1,0,0),List.of(id));
        for(int i=1;i<6;i++)a.sample(new FrameTime(i+1,i*16_000_000L,.016),true);
        for(int i=1;i<3;i++)b.sample(new FrameTime(i+1,i*32_000_000L,.032),true);
        var fast=a.sample(new FrameTime(8,96_000_000,.016),true).get(id);
        var slow=b.sample(new FrameTime(4,96_000_000,.032),true).get(id);
        assertEquals(fast,slow);
    }
    @Test void visibleSnapshotIsInterruptibleAndReducedMotionDoesNotReplay() throws Exception {
        Class<?> type=assertDoesNotThrow(()->Class.forName("com.atom.lib.layout.PageEntrance"));
        Object entry=type.getConstructor(double.class,double.class,float.class).newInstance(.2,.05,8f);
        var start=type.getMethod("start",FrameTime.class,List.class);
        var sample=type.getMethod("sample",FrameTime.class,boolean.class);
        var a=new NodeId("a");var b=new NodeId("b");var later=new NodeId("later");
        start.invoke(entry,new FrameTime(1,0,0),List.of(a,b));
        Map<?,?> initial=(Map<?,?>)sample.invoke(entry,new FrameTime(1,0,0),true);
        assertEquals(Set.of(a,b),initial.keySet());
        assertFalse(initial.containsKey(later),"Rows revealed by scrolling do not join the entrance");
        Map<?,?> half=(Map<?,?>)sample.invoke(entry,new FrameTime(2,100_000_000,.1),true);
        Object pose=half.get(a);Class<?> poseType=pose.getClass();
        float alpha=(float)poseType.getMethod("opacity").invoke(pose);
        float y=(float)poseType.getMethod("y").invoke(pose);
        assertTrue(alpha>0&&alpha<1);assertEquals(8*(1-alpha),y,.0001);
        assertTrue((float)half.get(b).getClass().getMethod("opacity").invoke(half.get(b))<alpha);
        assertEquals(half,sample.invoke(entry,new FrameTime(2,100_000_000,.1),true));
        assertEquals(half,sample.invoke(entry,new FrameTime(2,110_000_000,.01),true),"The same frame must not advance twice");
        start.invoke(entry,new FrameTime(3,120_000_000,.02),List.of(later));
        assertEquals(Set.of(later),((Map<?,?>)sample.invoke(entry,new FrameTime(3,120_000_000,.02),true)).keySet());
        assertTrue(((Map<?,?>)sample.invoke(entry,new FrameTime(4,130_000_000,.01),false)).isEmpty());
        assertTrue(((Map<?,?>)sample.invoke(entry,new FrameTime(5,140_000_000,.01),true)).isEmpty());
    }

    @Test void transformedGeometryAndClipAreSharedByDrawingAndInput() throws Exception {
        Class<?> pose=assertDoesNotThrow(()->Class.forName("com.atom.lib.layout.EntrancePose"));
        Object shifted=pose.getConstructor(float.class,float.class,float.class).newInstance(0f,12f,.25f);
        var child=LayoutNode.leaf("button",LayoutStyle.column().size(Length.points(80),Length.points(60)).flex(0,0,Length.auto()),null,0);
        var root=new LayoutNode(new NodeId("root"),LayoutStyle.column().behavior(false,false,true,true),List.of(child),null,0);
        var tree=new LayoutTree(root);var viewport=new Viewport(100,100,1);
        try(var engine=new YogaLayoutEngine()){
            var create=PresentationFrame.class.getMethod("create",LayoutTree.class,LayoutSnapshot.class,Viewport.class,Map.class,PresentationFrame.Offset.class,Map.class);
            var snapshot=engine.layout(tree,viewport);
            var frame=(PresentationFrame)create.invoke(null,tree,snapshot,viewport,Map.of(),new PresentationFrame.Offset(0,0),Map.of(child.id(),shifted));
            assertEquals(12,frame.box(child.id()).border().y());
            assertEquals(frame.box(child.id()).border(),frame.box(child.id()).hit());
            assertEquals(child.id(),frame.topAt(Set.of(child.id()),10,13));
            assertNull(frame.topAt(Set.of(child.id()),10,2));
            assertEquals(0,frame.box(child.id()).clip().y());
            assertEquals(.25f,(float)PresentationFrame.class.getMethod("opacity",NodeId.class).invoke(frame,child.id()));
            var e=assertThrows(InvocationTargetException.class,()->create.invoke(null,tree,snapshot,viewport,Map.of(),new PresentationFrame.Offset(0,0),Map.of(root.id(),shifted,child.id(),shifted)));
            assertInstanceOf(IllegalArgumentException.class,e.getCause());
        }
    }
}
