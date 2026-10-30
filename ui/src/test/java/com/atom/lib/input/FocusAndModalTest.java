package com.atom.lib.input;

import com.atom.lib.layout.NodeId;
import com.atom.lib.overlay.OverlayHost;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FocusAndModalTest {
    @Test void closingAModalMaySynchronouslyOpenItsReplacement() {
        var focus=new FocusManager();var base=new NodeId("base");var old=new NodeId("oldConfirm");var next=new NodeId("newConfirm");focus.setOrder(List.of(base));focus.focus(base,false);
        OverlayHost[] reference={null};boolean[] replace={false};
        var host=new OverlayHost(focus,()->{if(replace[0]){replace[0]=false;reference[0].open(new OverlayHost.Overlay(new NodeId("new"),true,List.of(next)));}});reference[0]=host;
        host.open(new OverlayHost.Overlay(new NodeId("old"),true,List.of(old)));replace[0]=true;host.closeTop();
        assertEquals(next,focus.focused());assertEquals(next,focus.tab(false));assertTrue(host.allows(focus.focused()));assertEquals(1,host.snapshot().size());
    }
    @Test void cancelFailureDoesNotLeaveAHiddenFocusTrap() {
        var focus=new FocusManager();var body=new NodeId("body");var confirm=new NodeId("confirm");focus.setOrder(List.of(body));focus.focus(body,false);
        int[] calls={0};var host=new OverlayHost(focus,()->{if(++calls[0]>1)throw new IllegalStateException("cancel failed");});
        host.open(new OverlayHost.Overlay(new NodeId("dialog"),true,List.of(confirm)));
        assertThrows(IllegalStateException.class,host::closeTop);
        assertFalse(host.blocking());assertEquals(0,host.snapshot().size());assertEquals(body,focus.tab(false));
    }
    @Test void aModalOpenedBeforeAnyFocusCanCloseIntoAnEmptyView() {
        var focus=new FocusManager();var host=new OverlayHost(focus,()->{});
        host.open(new OverlayHost.Overlay(new NodeId("empty"),true,List.of()));
        assertNull(focus.tab(false));host.closeTop();assertNull(focus.focused());assertFalse(host.allows(null));
    }
    @Test void tabTraversalModalTrapAndRestorationShareOneVisibleState() {
        var a=new NodeId("a");var b=new NodeId("b");var confirm=new NodeId("confirm");
        var focus=new FocusManager();focus.setOrder(List.of(a,b));
        assertEquals(a,focus.tab(false));assertEquals(b,focus.tab(false));assertEquals(a,focus.tab(false));
        focus.focus(b,false);
        int[] cancels={0};var overlays=new OverlayHost(focus,()->cancels[0]++);
        overlays.open(new OverlayHost.Overlay(new NodeId("dialog"),true,List.of(confirm)));
        assertEquals(confirm,focus.focused());assertEquals(confirm,focus.tab(false));
        assertFalse(overlays.allows(b));assertTrue(overlays.allows(confirm));assertEquals(1,overlays.snapshot().size());
        assertTrue(overlays.escape());assertEquals(b,focus.focused());assertFalse(overlays.blocking());assertEquals(2,cancels[0]);
        overlays.open(new OverlayHost.Overlay(new NodeId("dialog"),true,List.of(confirm)));
        focus.setOrder(List.of(a));overlays.closeTop();assertEquals(a,focus.focused());
    }
}
