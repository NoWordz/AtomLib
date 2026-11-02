package com.atom.lib.controls.internal;
import com.atom.lib.layout.NodeId;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FocusStateTest {
    @Test void orderedTraversalFilteringAndRestoration(){
        var a=new NodeId("a");var b=new NodeId("b");var c=new NodeId("c");
        var focus=new FocusState();focus.candidates(List.of(a,b,c));
        assertEquals(a,focus.step(false));assertEquals(c,focus.step(true));
        NodeId saved=focus.current();focus.candidates(List.of(a,b));assertEquals(a,focus.current());
        focus.restore(saved);assertEquals(a,focus.current());focus.restore(b);assertEquals(b,focus.current());
        focus.candidates(List.of());assertNull(focus.current());
    }
}
