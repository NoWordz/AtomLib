package com.atom.lib.layout;

import com.atom.lib.geometry.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PresentationFrameTest {
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
}
