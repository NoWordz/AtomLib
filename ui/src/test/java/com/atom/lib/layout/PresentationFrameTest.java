package com.atom.lib.layout;

import com.atom.lib.geometry.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PresentationFrameTest {
    @Test void controlsAtOneScaleSharePaintingGeometryWithoutSharingAcrossFrames() {
        var first=LayoutNode.leaf("first",LayoutStyle.column().size(Length.points(40),Length.points(20)),null,0);
        var second=LayoutNode.leaf("second",LayoutStyle.column().size(Length.points(60),Length.points(30)),null,0);
        var tree=new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column(),List.of(first,second),null,0));
        var viewport=new Viewport(200,200,1);
        try(var engine=new YogaLayoutEngine()) {
            var frame=PresentationFrame.create(tree,engine.layout(tree,viewport),viewport,Map.of(),new PresentationFrame.Offset(0,0)).scaled(1.5f);
            var painting=frame.forPainting(first.id());
            assertSame(painting,frame.forPainting(second.id()),"one geometry map per scale, not per control");
            assertEquals(40,painting.box(first.id()).border().width(),.001);
            assertEquals(60,painting.box(second.id()).border().width(),.001);
            assertNotSame(painting,frame.scaled(2).forPainting(first.id()),"a new displayed transform owns new geometry");
        }
    }
    @Test void scrollingMovesDrawingAndHitTogetherWhileKeepingViewportClipStationary() {
        var child=LayoutNode.leaf("button",LayoutStyle.column().size(Length.points(80),Length.points(200)).flex(0,0,Length.auto()),null,0);
        var root=new LayoutNode(new NodeId("scroll"),LayoutStyle.column().behavior(false,false,true,true),List.of(child),null,0);
        var tree=new LayoutTree(root);var viewport=new Viewport(100,100,1);
        try(var engine=new YogaLayoutEngine()){
            var frame=PresentationFrame.create(tree,engine.layout(tree,viewport),viewport,Map.of(root.id(),new PresentationFrame.Offset(0,60)),new PresentationFrame.Offset(10,5));
            assertEquals(new Rect(10,-55,80,200),frame.box(child.id()).border());
            assertEquals(new Rect(10,5,90,95),frame.box(child.id()).clip());
            assertEquals(child.id(),frame.topAt(Set.of(child.id()),20,10));
            assertNull(frame.topAt(Set.of(child.id()),20,0));
        }
    }
    @Test void scalingPresentedScrollFrameScalesPublishedBoundsOffsetAndExtent() {
        var child=LayoutNode.leaf("row",LayoutStyle.column().size(Length.points(80),Length.points(240)).flex(0,0,Length.auto()),null,0);
        var root=new LayoutNode(new NodeId("scroll-root"),LayoutStyle.column().size(Length.points(100),Length.auto()).behavior(false,false,true,true),List.of(child),null,0);
        var tree=new LayoutTree(root);var viewport=new Viewport(200,200,1);var scroll=new ScrollViewport();
        try(var engine=new YogaLayoutEngine()) {
            var natural=engine.layoutIntrinsicHeight(tree,viewport);
            var initial=scroll.present(tree,natural,viewport,new Rect(10,20,100,80),1);scroll.publish(initial);assertTrue(scroll.scrollBy(20));
            var scaled=scroll.present(tree,natural,viewport,new Rect(10,20,100,80),1).scaled(2);
            assertTrue(scroll.contains(scaled,child.id()),"scaled frame retains its scroll membership");
            scroll.publish(scaled);
            assertEquals(new Rect(20,40,200,160),scroll.bounds());assertEquals(480,scroll.extent(),.001);assertEquals(40,scroll.offset(),.001);
            assertEquals(child.id(),scaled.topAt(Set.of(child.id()),30,50));
            scroll.publish(scaled.forPainting(child.id()));
            assertEquals(new Rect(10,20,100,80),scroll.bounds());assertEquals(240,scroll.extent(),.001);assertEquals(20,scroll.offset(),.001);
            scroll.publish(PresentationFrame.compose(List.of(scaled)));
            assertEquals(new Rect(20,40,200,160),scroll.bounds());assertTrue(scroll.contains(child.id()));
        }
    }
}
