package com.atom.lib.layout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayoutStyleTest {
    @Test void newFieldsHaveDefaultsAndUniformSugar() {
        var s=LayoutStyle.column();
        assertEquals(Edges.ZERO,s.padding());
        assertEquals(Edges.ZERO,s.margin());
        assertEquals(0,s.aspectRatio());
        assertEquals(LayoutStyle.Align.STRETCH,s.align());
        assertEquals(LayoutStyle.Justify.FLEX_START,s.justify());
        assertEquals(new Edges(8,8,8,8),s.padding(8).padding());
        assertEquals(new Edges(-4,-4,-4,-4),s.margin(-4).margin());
        assertEquals(new Edges(3,3,3,3),s.spacing(3,5).padding());
    }
    @Test void withersCarryTheNewFieldsThroughEveryRewrite() {
        var s=LayoutStyle.row().size(Length.points(10),Length.points(20))
                .padding(4).margin(Edges.uniform(-2)).aspectRatio(1.5f)
                .align(LayoutStyle.Align.CENTER).justify(LayoutStyle.Justify.SPACE_BETWEEN);
        assertEquals(LayoutStyle.Align.CENTER,s.direction(LayoutStyle.Direction.COLUMN).align());
        assertEquals(LayoutStyle.Justify.SPACE_BETWEEN,s.direction(LayoutStyle.Direction.COLUMN).justify());
        assertEquals(Edges.uniform(4),s.size(Length.auto(),Length.auto()).padding());
        assertEquals(Edges.uniform(-2),s.flex(0,1,Length.auto()).margin());
        assertEquals(1.5f,s.limits(0,0,50,50).aspectRatio());
        assertEquals(Edges.uniform(4),s.behavior(true,false,false,false).padding());
        assertEquals(Edges.uniform(-2),s.behavior(true,false,false,false).margin());
        assertEquals(Edges.uniform(6),s.spacing(6,7).padding());
        assertEquals(Edges.uniform(-2),s.spacing(6,7).margin());
        assertEquals(new Edges(1,2,3,4),s.margin(new Edges(1,2,3,4)).margin());
    }
    @Test void negativeMarginIsLegalButNegativePaddingIsNot() {
        var s=LayoutStyle.column().margin(new Edges(-10,0,-5,0));
        assertEquals(new Edges(-10,0,-5,0),s.margin());
        assertThrows(IllegalArgumentException.class,()->LayoutStyle.column().padding(new Edges(-1,0,0,0)));
        assertThrows(IllegalArgumentException.class,()->LayoutStyle.column().padding(-1));
        assertThrows(IllegalArgumentException.class,()->LayoutStyle.column().spacing(-1,0));
    }
    @Test void illegalEdgeAndRatioValuesThrow() {
        assertThrows(IllegalArgumentException.class,()->new Edges(Float.NaN,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new Edges(0,0,Float.POSITIVE_INFINITY,0));
        assertThrows(IllegalArgumentException.class,()->new Edges(0,0,0,Float.NaN));
        assertThrows(NullPointerException.class,()->LayoutStyle.column().margin(null));
        assertThrows(NullPointerException.class,()->LayoutStyle.column().padding((Edges)null));
        assertThrows(NullPointerException.class,()->LayoutStyle.column().align(null));
        assertThrows(NullPointerException.class,()->LayoutStyle.column().justify(null));
        assertThrows(IllegalArgumentException.class,()->LayoutStyle.column().aspectRatio(-0.5f));
        assertThrows(IllegalArgumentException.class,()->LayoutStyle.column().aspectRatio(Float.NaN));
        assertThrows(IllegalArgumentException.class,()->LayoutStyle.column().aspectRatio(Float.POSITIVE_INFINITY));
    }
}
