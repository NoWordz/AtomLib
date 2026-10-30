package com.atom.lib.scroll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollViewTest {
    @Test void nestedScrollConsumesOnlyAvailableDistanceAndResizeClamps() {
        var inner=new ScrollView();var outer=new ScrollView();
        inner.setExtent(120,100);outer.setExtent(400,100);
        float remaining=inner.scroll(50);assertEquals(20,inner.offset());assertEquals(30,remaining);
        assertEquals(0,outer.scroll(remaining));assertEquals(30,outer.offset());
        inner.setExtent(50,100);assertEquals(0,inner.offset());
        assertEquals(-10,inner.scroll(-10));
    }
}
