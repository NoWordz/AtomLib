// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.render.RenderSession;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderScopeTest {
    @Test void exceptionRestoresStackAndRecursionIsRejectedAcrossSessions() {
        try (var handle = AtomLibRuntime.open("render"); var surface = Surface.makeRasterN32Premul(20, 20);
             var first = new RenderSession(handle, surface.getCanvas()); var other = new RenderSession(handle, surface.getCanvas())) {
            var canvas = surface.getCanvas(); int baseline = canvas.getSaveCount();
            assertThrows(IllegalArgumentException.class, () -> first.paint(c -> {
                c.save(); c.translate(10, 10);
                assertThrows(IllegalStateException.class, () -> other.paint(nested -> nested.clear(0)));
                throw new IllegalArgumentException("paint failed");
            }));
            assertEquals(baseline, canvas.getSaveCount());
            other.paint(c -> c.clear(0xff112233));
            first.close(); first.close();
            assertThrows(IllegalStateException.class, () -> first.paint(c -> c.clear(0)));
            other.close();
            canvas.clear(0xffffffff); // closing a session must not close the borrowed Canvas
        }
    }
    @Test void closedHandleBlocksNewPaints() {
        try (var handle = AtomLibRuntime.open(); var surface = Surface.makeRasterN32Premul(2, 2);
             var session = new RenderSession(handle, surface.getCanvas())) {
            handle.close();
            assertThrows(IllegalStateException.class, () -> session.paint(c -> c.clear(0)));
        }
    }
}
