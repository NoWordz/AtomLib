// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.layout;

import com.atom.lib.geometry.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for the direction-B release blocker: relayouting a live engine at
 * a new pixelsPerUnit must re-measure and re-round instead of crashing. The
 * locked adapter used to call YGNodeMarkDirtyAndPropogateToDescendants, whose
 * LWJGL spelling differs between the locked target runtimes (3.3.1
 * "Propogate" vs 3.3.3 "Propagate") — bytecode carrying either spelling threw
 * NoSuchMethodError on the other version's natives the moment a host resized a
 * viewport. This file must keep compiling and passing against BOTH targets.
 */
class YogaRescaleHiddenGlTest {
    private static LayoutNode node(String id,LayoutStyle style,LayoutNode... children){
        return new LayoutNode(new NodeId(id),style,List.of(children),null,0);
    }
    @Test void relayoutAtANewPixelsPerUnitRe_measuresAndRe_rounds() {
        int[] measures={0};
        LeafMeasure measure=c->{
            measures[0]++;
            float width=c.width().constrain(40);
            return new MeasuredSize(width,width/2,10);
        };
        var label=LayoutNode.leaf("label",LayoutStyle.column(),measure,0);
        // A fractional root width: the point scale grid decides which values
        // survive. 100.4 is not on the 1-unit grid but sits exactly on 1/16.
        var root=node("root",LayoutStyle.row().spacing(0,2)
                .size(Length.points(100.4f),Length.points(60)),label);
        var tree=new LayoutTree(root);
        try(var engine=new YogaLayoutEngine()){
            var coarse=engine.layout(tree,new Viewport(400,120,1));
            assertEquals(100,coarse.box(new NodeId("root")).border().width(),1e-4,
                    "pointScaleFactor 1 snaps layout results to whole units");
            assertEquals(40,coarse.box(new NodeId("label")).border().width(),1e-4);
            int coarseMeasures=measures[0];
            // The crash site: same engine, new pixelsPerUnit.
            var fine=engine.layout(tree,new Viewport(400,120,16));
            assertTrue(measures[0]>coarseMeasures,
                    "a new pixelsPerUnit must dirty measured leaves so Yoga re-measures");
            assertEquals(100.375f,fine.box(new NodeId("root")).border().width(),1e-4,
                    "pointScaleFactor 16 snaps to the 1/16-unit grid");
            assertEquals(40,fine.box(new NodeId("label")).border().width(),1e-4);
            // Stable frames keep their snapshot and skip measurement.
            assertSame(fine,engine.layout(tree,new Viewport(400,120,16)));
            int stable=measures[0];
            assertSame(fine,engine.layout(tree,new Viewport(400,120,16)));
            assertEquals(stable,measures[0]);
        }
    }
    @Test void relayoutAtANewPixelsPerUnitSnapsEveryResultToItsScaleGrid() {
        // Round trip through several densities, so the leaf-dirty path is
        // exercised repeatedly on the target runtime.
        LeafMeasure grow=c->new MeasuredSize(c.width().constrain(30),c.height().constrain(20),5);
        var a=LayoutNode.leaf("a",LayoutStyle.column().flex(1,1,Length.points(0)),grow,0);
        var b=node("b",LayoutStyle.column().flex(1,1,Length.points(0)));
        var root=node("root",LayoutStyle.row(),a,b);
        try(var engine=new YogaLayoutEngine()){
            // 101/2 = 50.5: on the 2-unit grid it snaps up to 51, on any finer
            // grid it survives exactly.
            assertEquals(51,engine.layout(new LayoutTree(root),new Viewport(101,50,1)).box(new NodeId("a")).border().width(),1e-4);
            assertEquals(50.5f,engine.layout(new LayoutTree(root),new Viewport(101,50,16)).box(new NodeId("a")).border().width(),1e-4);
            assertEquals(50.5f,engine.layout(new LayoutTree(root),new Viewport(101,50,2)).box(new NodeId("a")).border().width(),1e-4);
            assertEquals(50.5f,engine.layout(new LayoutTree(root),new Viewport(101,50,8)).box(new NodeId("a")).border().width(),1e-4);
            assertEquals(51,engine.layout(new LayoutTree(root),new Viewport(101,50,1)).box(new NodeId("a")).border().width(),1e-4);
            // Measure-func swap on a live tree: node b gains the callback and
            // its measured 50.5 snaps to 51 on the 1-unit grid.
            var bMeasured=LayoutNode.leaf("b",LayoutStyle.column().flex(1,1,Length.points(0)),grow,1);
            var swapped=node("root",LayoutStyle.row(),a,bMeasured);
            var snapshot=engine.layout(new LayoutTree(swapped),new Viewport(101,50,1));
            assertEquals(51,snapshot.box(new NodeId("b")).border().width(),1e-4);
        }
    }
}
