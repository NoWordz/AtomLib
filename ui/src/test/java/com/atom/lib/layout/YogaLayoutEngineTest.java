package com.atom.lib.layout;

import com.atom.lib.geometry.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class YogaLayoutEngineTest {
    static LayoutNode node(String id,LayoutStyle style,LayoutNode... children){return new LayoutNode(new NodeId(id),style,List.of(children),null,0);}
    static final LayoutStyle ROW=LayoutStyle.row().spacing(10,8);
    @Test void singleGeometryUpdatesOnResizeAndStableFramesAvoidMeasurement() {
        int[] measures={0};
        LeafMeasure measure=c->{measures[0]++;float w=c.width().constrain(120);return new MeasuredSize(w,c.height().constrain(w<100?40:20),15);};
        var label=LayoutNode.leaf("label",LayoutStyle.column().flex(1,1,Length.points(0)),measure,0);
        var fixed=node("fixed",LayoutStyle.column().size(Length.points(100),Length.points(20)));
        var tree=new LayoutTree(node("root",ROW,fixed,label));
        try(var engine=new YogaLayoutEngine()) {
            var first=engine.layout(tree,new Viewport(320,100,1));
            assertEquals(new Rect(118,10,192,80),first.box(new NodeId("label")).border());
            int before=measures[0];
            assertSame(first,engine.layout(tree,new Viewport(320,100,1)));
            assertEquals(before,measures[0]);
            var second=engine.layout(tree,new Viewport(200,100,1));
            assertEquals(72,second.box(new NodeId("label")).border().width(),.01);
            assertTrue(second.revision()>first.revision());
            assertEquals(second.box(new NodeId("label")).border(),second.box(new NodeId("label")).hit());
            assertThrows(IllegalArgumentException.class,()->second.box(new NodeId("missing")));
        }
    }
    @Test void clipsAreInheritedHiddenNodesAreNotHitAndRemovedNodesDisappear() {
        var child=node("child",LayoutStyle.column().size(Length.points(200),Length.points(20)));
        var hidden=node("hidden",LayoutStyle.column().behavior(false,true,false,false));
        var rootStyle=LayoutStyle.column().size(Length.points(100),Length.points(80)).behavior(false,false,true,true);
        try(var engine=new YogaLayoutEngine()){
            var s=engine.layout(new LayoutTree(node("root",rootStyle,child,hidden)),new Viewport(400,300,1));
            assertEquals(100,s.box(new NodeId("child")).clip().width(),.01);
            assertEquals(new NodeId("root"),s.box(new NodeId("child")).scrollOwner());
            assertFalse(s.paintOrder().contains(new NodeId("hidden")));
            var s2=engine.layout(new LayoutTree(node("root",rootStyle)),new Viewport(400,300,1));
            assertThrows(IllegalArgumentException.class,()->s2.box(new NodeId("child")));
            engine.close();engine.close();
            assertThrows(IllegalStateException.class,()->engine.layout(new LayoutTree(node("root",rootStyle)),new Viewport(1,1,1)));
        }
    }
    @Test void callbacksPropagateExceptionsAfterNativeReturnsAndTheEngineRecovers() {
        try(var engine=new YogaLayoutEngine()){
            var bad=LayoutNode.leaf("text",LayoutStyle.column(),c->{throw new IllegalStateException("measurement failed");},0);
            var tree=new LayoutTree(node("root",LayoutStyle.column(),bad));
            assertThrows(IllegalStateException.class,()->engine.layout(tree,new Viewport(100,100,1)));
            var good=LayoutNode.leaf("text",LayoutStyle.column(),c->new MeasuredSize(c.width().constrain(20),c.height().constrain(20),15),1);
            assertTrue(engine.layout(new LayoutTree(node("root",LayoutStyle.column(),good)),new Viewport(100,100,1)).box(new NodeId("text")).border().width()>0);
        }
    }
    @Test void changingPixelDensityInvalidatesNativeRounding() {
        var flex=LayoutStyle.column().flex(1,1,Length.points(0));
        var tree=new LayoutTree(node("root",LayoutStyle.row(),node("a",flex),node("b",flex)));
        try(var engine=new YogaLayoutEngine()){
            assertEquals(51,engine.layout(tree,new Viewport(101,10,1)).box(new NodeId("a")).border().width());
            assertEquals(50.5f,engine.layout(tree,new Viewport(101,10,2)).box(new NodeId("a")).border().width());
            assertEquals(51,engine.layout(tree,new Viewport(101,10,1)).box(new NodeId("a")).border().width());
        }
    }
    @Test void promotingAChildToRootDoesNotRetainItsFormerParentGap() {
        try(var engine=new YogaLayoutEngine()){
            var child=node("a",LayoutStyle.column());
            engine.layout(new LayoutTree(node("parent",LayoutStyle.row().spacing(0,8),child,node("b",LayoutStyle.column()))),new Viewport(100,100,1));
            assertEquals(100,engine.layout(new LayoutTree(child),new Viewport(100,100,1)).box(child.id()).border().width());
        }
    }
    @Test void removingMeasurementClearsTheOldHeight() {
        try(var engine=new YogaLayoutEngine()){
            var measured=LayoutNode.leaf("a",LayoutStyle.column(),c->new MeasuredSize(c.width().constrain(20),30,15),0);
            engine.layout(new LayoutTree(node("root",LayoutStyle.column(),measured)),new Viewport(100,100,1));
            var empty=new LayoutTree(node("root",LayoutStyle.column(),node("a",LayoutStyle.column())));
            assertEquals(0,engine.layout(empty,new Viewport(100,100,1)).box(new NodeId("a")).border().height());
        }
    }
    @Test void unsupportedWrapGapFailsBeforeNativeMutation() {
        var style=LayoutStyle.row().spacing(0,8).behavior(true,false,false,false);
        try(var engine=new YogaLayoutEngine()){
            assertThrows(IllegalArgumentException.class,()->engine.layout(new LayoutTree(node("root",style)),new Viewport(128,100,1)));
        }
    }
    @Test void measurementCannotCloseOrReenterItsActiveNativeTree() {
        // The old path could free an executing JNI callback; do not run that
        // unsafe operation until the activity guard exists.
        try(var engine=new YogaLayoutEngine()){
            var viewport=new Viewport(100,100,1);
            var other=new LayoutTree(node("other",LayoutStyle.column()));
            var text=LayoutNode.leaf("a",LayoutStyle.column(),c->{
                assertThrows(IllegalStateException.class,engine::close);
                assertThrows(IllegalStateException.class,()->engine.layout(other,viewport));
                return new MeasuredSize(c.width().constrain(20),30,15);
            },0);
            assertEquals(30,engine.layout(new LayoutTree(node("root",LayoutStyle.column(),text)),viewport).box(text.id()).border().height());
            assertNotNull(engine.layout(other,viewport));
        }
    }
    @Test void intrinsicOverlayRootUsesNaturalHeightAndSwitchingBackRetainsFillSemantics(){
        int[] measured={0};LeafMeasure measure=c->{measured[0]++;return new MeasuredSize(c.width().constrain(80),c.height().constrain(30),15);};
        var tree=new LayoutTree(node("root",LayoutStyle.column(),LayoutNode.leaf("text",LayoutStyle.column(),measure,0)));
        var viewport=new Viewport(200,160,1);
        try(var engine=new YogaLayoutEngine()){
            var natural=engine.layoutIntrinsicHeight(tree,viewport);assertEquals(30,natural.box(new NodeId("root")).border().height());int count=measured[0];
            assertSame(natural,engine.layoutIntrinsicHeight(tree,viewport));assertEquals(count,measured[0]);
            assertEquals(160,engine.layout(tree,viewport).box(new NodeId("root")).border().height());
            assertEquals(30,engine.layoutIntrinsicHeight(tree,viewport).box(new NodeId("root")).border().height());
        }
    }
}
