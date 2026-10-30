// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.geometry.Rect;
import com.atom.lib.image.*;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ImageReloadTest {
    private byte[] png() {
        try (var surface = Surface.makeRasterN32Premul(20, 10)) {
            surface.getCanvas().clear(0xffff0000);
            try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) { return data.getBytes(); }
        }
    }
    private int color(Surface surface, int x, int y) {
        try (var image = surface.makeImageSnapshot(); var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(40, 40); assertTrue(image.readPixels(bitmap)); return bitmap.getColor(x,y);
        }
    }
    @Test void imageFitReloadEvictionAndStaleCompletions() {
        try (var handle = AtomLibRuntime.open("images"); var store = new ImageStore(handle, 1);
             var surface = Surface.makeRasterN32Premul(40, 40)) {
            long ticket = store.generation(); byte[] data = png();
            assertTrue(store.complete("icon", ticket, data));
            var canvas = surface.getCanvas(); canvas.clear(0xffffffff);
            store.draw("icon", canvas, new Rect(0,0,40,40), ImageFit.CONTAIN, 0);
            assertEquals(0xffffffff, color(surface, 20, 2)); assertEquals(0xffff0000, color(surface, 20, 20));
            canvas.clear(0xffffffff);
            store.draw("icon", canvas, new Rect(0,0,40,40), ImageFit.COVER, 10);
            assertEquals(0xffff0000, color(surface, 20, 2)); assertEquals(0xffffffff, color(surface, 0, 0));
            store.reload();
            assertFalse(store.complete("late", ticket, data));
            assertThrows(IllegalArgumentException.class, () -> store.draw("icon", canvas, new Rect(0,0,10,10), ImageFit.CONTAIN, 0));
            store.put("one", data); store.put("two", data); assertEquals(1, store.size());
            assertThrows(IllegalArgumentException.class, () -> store.draw("one", canvas, new Rect(0,0,10,10), ImageFit.CONTAIN, 0));
            long closingTicket = store.generation(); store.close(); store.close();
            assertFalse(store.complete("closed", closingTicket, data));
        }
    }
    @Test void svgTintUsesRealDomAndPreservesCanvasState() {
        try (var handle = AtomLibRuntime.open("svg"); var store = new SvgStore(handle, 2);
             var surface = Surface.makeRasterN32Premul(40, 40)) {
            byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'><rect width='10' height='10' fill='red'/></svg>".getBytes(StandardCharsets.UTF_8);
            store.put("square", svg);
            var canvas = surface.getCanvas(); canvas.clear(0xffffffff); int saved = canvas.getSaveCount();
            store.draw("square", canvas, new Rect(10,10,20,20), 0xff00ff00);
            assertEquals(0xff00ff00, color(surface, 20, 20)); assertEquals(0xffffffff, color(surface, 5, 5));
            assertEquals(saved, canvas.getSaveCount());
            long ticket = store.generation(); store.reload(); assertFalse(store.complete("old", ticket, svg));
            assertEquals(0, store.size());
            assertThrows(RuntimeException.class, () -> store.put("broken", new byte[]{1,2,3}));
            assertEquals(0, store.size());
        }
    }
    @Test void transferredNativeImagesCloseOnStaleEvictionReloadAndHostShutdown() {
        try (var handle = AtomLibRuntime.open(); var store = new ImageStore(handle, 1)) {
            byte[] bytes = png();
            try (var first = Image.makeFromEncoded(bytes); var second = Image.makeFromEncoded(bytes); var late = Image.makeFromEncoded(bytes)) {
                assertTrue(store.acceptDecoded("a", store.generation(), first));
                assertTrue(store.acceptDecoded("b", store.generation(), second));
                assertTrue(first.isClosed()); assertFalse(second.isClosed());
                long old = store.generation(); store.reload();
                assertTrue(second.isClosed());
                assertFalse(store.acceptDecoded("late", old, late)); assertTrue(late.isClosed());
            }
            try (var image = Image.makeFromEncoded(bytes)) {
                store.acceptDecoded("a", store.generation(), image);
                handle.close(); assertTrue(image.isClosed());
            }
        }
    }
    @Test void transferredSvgDomClosesWhenStaleAndOnReload() {
        try (var handle = AtomLibRuntime.open(); var store = new SvgStore(handle, 1);
             var data = Data.makeFromBytes("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8));
             var first = new io.github.humbleui.skija.svg.SVGDOM(data); var late = new io.github.humbleui.skija.svg.SVGDOM(data)) {
            long ticket = store.generation();
            store.acceptDecoded("a", ticket, first); store.reload(); assertTrue(first.isClosed());
            assertFalse(store.acceptDecoded("late", ticket, late)); assertTrue(late.isClosed());
        }
    }
}
