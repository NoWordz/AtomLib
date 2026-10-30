package com.atom.lib.geometry;

import com.atom.lib.layout.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class GeometryContractTest {
    @Test void clippingAndHalfOpenHitUseTheSameGeometry() {
        var r = new Rect(10, 20, 50, 30);
        assertTrue(r.contains(10,20)); assertFalse(r.contains(60,20));
        assertEquals(new Rect(30,30,30,20),r.intersect(new Rect(30,30,100,100)));
        assertEquals(0,r.intersect(new Rect(90,90,10,10)).width());
        assertThrows(IllegalArgumentException.class,()->new Rect(0,0,Float.NaN,2));
    }
    @Test void constraintsLimitMeasurementWithoutInventingAnUndefinedBound() {
        assertEquals(42,Constraints.Axis.undefined().constrain(42));
        assertEquals(20,Constraints.Axis.atMost(20).constrain(42));
        assertEquals(20,Constraints.Axis.exactly(20).constrain(5));
        assertThrows(IllegalArgumentException.class,()->Constraints.Axis.atMost(-1));
    }
    @Test void duplicateIdsCannotSilentlyRedirectInput() {
        var id=new NodeId("row");
        var leaf=new LayoutNode(id,LayoutStyle.column(),List.of(),null,0);
        assertThrows(IllegalArgumentException.class,()->new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column(),List.of(leaf,leaf),null,0)));
        var tree=new LayoutTree(leaf);
        assertSame(leaf,tree.node(id));
        assertThrows(IllegalArgumentException.class,()->tree.node(new NodeId("missing")));
    }
}
