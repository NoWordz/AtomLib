// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.layout;

import com.atom.lib.geometry.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-Yoga assertions for the decided LayoutStyle expansion (per-edge
 * padding, margins, aspect ratio, cross/main-axis alignment). Every numeric
 * expectation is a concrete measured coordinate. This file must keep
 * compiling and passing against BOTH locked Yoga targets (3.3.1 and 3.3.3);
 * the mapping only uses bindings carried identically by both versions.
 */
class LayoutStyleExpansionHiddenGlTest {
    private static LayoutNode node(String id,LayoutStyle style,LayoutNode... children){
        return new LayoutNode(new NodeId(id),style,List.of(children),null,0);
    }
    @Test void crossAxisStretchesByDefaultAndMainAxisStartsAtFlexStart() {
        // A column parent with a fixed frame and an auto-width child pins the
        // Yoga defaults the enum defaults must match: STRETCH fills the cross
        // axis (flex-start would leave width 0) and FLEX_START keeps y at 0
        // (center would push a 20-tall child to y=20).
        var root=node("root",LayoutStyle.column().size(Length.points(100),Length.points(60)),
                node("a",LayoutStyle.column().size(Length.auto(),Length.points(20))));
        try(var engine=new YogaLayoutEngine()){
            var snapshot=engine.layout(new LayoutTree(root),new Viewport(200,120,1));
            assertEquals(new Rect(0,0,100,20),snapshot.box(new NodeId("a")).border());
        }
    }
    @Test void perEdgePaddingOffsetsContentAndChildren() {
        var style=LayoutStyle.column().size(Length.points(100),Length.points(60))
                .padding(new Edges(5,10,15,20));
        var root=node("root",style,node("a",LayoutStyle.column().size(Length.points(10),Length.points(10))));
        try(var engine=new YogaLayoutEngine()){
            var snapshot=engine.layout(new LayoutTree(root),new Viewport(200,120,1));
            assertEquals(new Rect(20,5,10,10),snapshot.box(new NodeId("a")).border(),
                    "child origin is the padding edge: left 20, top 5");
            assertEquals(new Rect(20,5,70,40),snapshot.box(new NodeId("root")).content(),
                    "content rect insets left+right=30 and top+bottom=20");
        }
    }
    @Test void aspectRatioDerivesHeightFromTheDefiniteWidth() {
        var tall=node("tall",LayoutStyle.column().size(Length.points(100),Length.auto()).aspectRatio(2));
        var wide=node("wide",LayoutStyle.column().size(Length.points(100),Length.auto()).aspectRatio(0.5f));
        var natural=LayoutNode.leaf("natural",LayoutStyle.column(),
                c->new MeasuredSize(c.width().constrain(100),30,5),0);
        var tree=new LayoutTree(node("root",LayoutStyle.column(),tall,wide,natural));
        try(var engine=new YogaLayoutEngine()){
            var snapshot=engine.layout(tree,new Viewport(200,400,1));
            assertEquals(50,snapshot.box(new NodeId("tall")).border().height(),1e-4);
            assertEquals(200,snapshot.box(new NodeId("wide")).border().height(),1e-4);
            assertEquals(30,snapshot.box(new NodeId("natural")).border().height(),1e-4,
                    "aspectRatio 0 (the default) leaves natural sizing alone");
            // Dropping the ratio on a live tree must reset it to undefined,
            // not keep the last applied value.
            var cleared=new LayoutTree(node("root",LayoutStyle.column(),
                    node("tall",LayoutStyle.column().size(Length.points(100),Length.auto())),wide,natural));
            assertEquals(0,engine.layout(cleared,new Viewport(200,400,1)).box(new NodeId("tall")).border().height(),1e-4);
        }
    }
    @Test void marginMovesSiblingsAndTheirOwnPosition() {
        var style=LayoutStyle.row();
        var tree=new LayoutTree(node("root",style,
                node("a",LayoutStyle.column().size(Length.points(30),Length.points(20)).margin(new Edges(0,10,0,0))),
                node("b",LayoutStyle.column().size(Length.points(30),Length.points(20)).margin(new Edges(0,-8,0,0)))));
        try(var engine=new YogaLayoutEngine()){
            var snapshot=engine.layout(tree,new Viewport(200,120,1));
            assertEquals(0,snapshot.box(new NodeId("a")).border().x(),1e-4,"row layout ignores a trailing right margin");
            assertEquals(40,snapshot.box(new NodeId("b")).border().x(),1e-4,"b starts after a's 30 width plus its 10 right margin");
            assertEquals(70,snapshot.box(new NodeId("b")).border().right(),1e-4);
            var pulled=new LayoutTree(node("root",style,
                    node("a",LayoutStyle.column().size(Length.points(30),Length.points(20))),
                    node("b",LayoutStyle.column().size(Length.points(30),Length.points(20)).margin(new Edges(0,0,0,-8)))));
            assertEquals(22,engine.layout(pulled,new Viewport(200,120,1)).box(new NodeId("b")).border().x(),1e-4,
                    "Yoga allows negative margins: 30 - 8");
            var shifted=new LayoutTree(node("root",style,
                    node("a",LayoutStyle.column().size(Length.points(30),Length.points(20)).margin(new Edges(4,0,0,6))),
                    node("b",LayoutStyle.column().size(Length.points(30),Length.points(20)))));
            var moved=engine.layout(shifted,new Viewport(200,120,1));
            assertEquals(6,moved.box(new NodeId("a")).border().x(),1e-4,"a node's own left margin offsets it");
            assertEquals(4,moved.box(new NodeId("a")).border().y(),1e-4,"a node's own top margin offsets it");
            assertEquals(36,moved.box(new NodeId("b")).border().x(),1e-4,"a's left margin pushes b along the main axis");
        }
    }
    @Test void styleMarginAddsToGapInsteadOfBeingOverwritten() {
        var tree=new LayoutTree(node("root",LayoutStyle.row().spacing(0,8),
                node("a",LayoutStyle.column().size(Length.points(30),Length.points(20)).margin(new Edges(0,10,0,0))),
                node("b",LayoutStyle.column().size(Length.points(30),Length.points(20)))));
        try(var engine=new YogaLayoutEngine()){
            assertEquals(48,engine.layout(tree,new Viewport(200,120,1)).box(new NodeId("b")).border().x(),1e-4,
                    "30 width + 10 style margin + 8 gap");
        }
    }
    @Test void justifyDistributesChildrenOnTheMainAxis() {
        var base=LayoutStyle.column().size(Length.points(100),Length.points(90));
        var children=new LayoutNode[]{
                node("a",LayoutStyle.column().size(Length.points(20),Length.points(20))),
                node("b",LayoutStyle.column().size(Length.points(20),Length.points(20)))};
        try(var engine=new YogaLayoutEngine()){
            assertEquals(0,engine.layout(new LayoutTree(node("root",base.justify(LayoutStyle.Justify.FLEX_START),children)),
                    new Viewport(200,200,1)).box(new NodeId("a")).border().y(),1e-4);
            assertEquals(25,engine.layout(new LayoutTree(node("root",base.justify(LayoutStyle.Justify.CENTER),children)),
                    new Viewport(200,200,1)).box(new NodeId("a")).border().y(),1e-4,"(90-40)/2");
            assertEquals(50,engine.layout(new LayoutTree(node("root",base.justify(LayoutStyle.Justify.FLEX_END),children)),
                    new Viewport(200,200,1)).box(new NodeId("a")).border().y(),1e-4,"90-40");
            var between=engine.layout(new LayoutTree(node("root",base.justify(LayoutStyle.Justify.SPACE_BETWEEN),children)),
                    new Viewport(200,200,1));
            assertEquals(0,between.box(new NodeId("a")).border().y(),1e-4);
            assertEquals(70,between.box(new NodeId("b")).border().y(),1e-4,"last child flush to the end");
            // space-around halves the leftover space into 4 segments of 12.5;
            // pixelsPerUnit 16 keeps 12.5 exactly on the rounding grid.
            var around=engine.layout(new LayoutTree(node("root",base.justify(LayoutStyle.Justify.SPACE_AROUND),children)),
                    new Viewport(200,200,16));
            assertEquals(12.5f,around.box(new NodeId("a")).border().y(),1e-4);
            assertEquals(57.5f,around.box(new NodeId("b")).border().y(),1e-4,"90-20-12.5");
        }
    }
    @Test void alignPositionsChildrenOnTheCrossAxis() {
        var base=LayoutStyle.row().size(Length.points(100),Length.points(60));
        var child=node("a",LayoutStyle.column().size(Length.points(20),Length.points(20)));
        try(var engine=new YogaLayoutEngine()){
            assertEquals(0,engine.layout(new LayoutTree(node("root",base.align(LayoutStyle.Align.FLEX_START),child)),
                    new Viewport(200,200,1)).box(new NodeId("a")).border().y(),1e-4);
            assertEquals(20,engine.layout(new LayoutTree(node("root",base.align(LayoutStyle.Align.CENTER),child)),
                    new Viewport(200,200,1)).box(new NodeId("a")).border().y(),1e-4,"(60-20)/2");
            assertEquals(40,engine.layout(new LayoutTree(node("root",base.align(LayoutStyle.Align.FLEX_END),child)),
                    new Viewport(200,200,1)).box(new NodeId("a")).border().y(),1e-4,"60-20");
        }
    }
}
