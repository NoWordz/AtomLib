// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.*;
import com.atom.lib.layout.Constraints;
import com.atom.lib.text.*;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ParagraphTextServiceTest {
    private static final FontId BODY = new FontId("body");
    private static Constraints width(float width) {
        return new Constraints(Constraints.Axis.atMost(width), Constraints.Axis.undefined());
    }
    private static io.github.humbleui.skija.paragraph.Paragraph nativeParagraph(ParagraphTextLayout layout) throws Exception {
        var field = ParagraphTextLayout.class.getDeclaredField("paragraph");
        field.setAccessible(true);
        return (io.github.humbleui.skija.paragraph.Paragraph) field.get(layout);
    }
    @Test void widthClippingReportsTruncationWithoutConfusingWrapping() {
        try (var runtime=AtomLibRuntime.open(); var fonts=new FontRegistry(runtime); var service=new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var request=new TextRequest("W", BODY, 40, 0xff000000);
            try (var clipped=service.layout(request, width(2)); var full=service.layout(request, width(200));
                 var wrapped=service.layout(new TextRequest("one two three", BODY, 20, 0xff000000), width(50))) {
                assertEquals(2, clipped.size().width());
                assertTrue(full.size().width() > clipped.size().width());
                assertTrue(clipped.truncated(), "oversized glyph is clipped horizontally, lines=" + clipped.lines());
                assertFalse(full.truncated());
                assertTrue(wrapped.lines().size() > 1);
                assertFalse(wrapped.truncated(), "normal wrapping is not truncation");
            }
        }
    }
    @Test void nextLayoutAfterReloadClosesEveryStaleNativeParagraph() throws Exception {
        try (var runtime=AtomLibRuntime.open(); var fonts=new FontRegistry(runtime); var service=new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var a=service.layout(new TextRequest("first", BODY, 20, 0), width(100));
            var b=service.layout(new TextRequest("second", BODY, 20, 0), width(100));
            var pa=nativeParagraph(a); var pb=nativeParagraph(b);
            var sa=pa._text; var sb=pb._text;
            assertFalse(pa.isClosed()); assertFalse(pb.isClosed());
            fonts.reload();
            try (var fresh=service.layout(new TextRequest("fresh", BODY, 20, 0), width(100))) {
                assertTrue(pa.isClosed()); assertTrue(pb.isClosed());
                assertTrue(sa.isClosed()); assertTrue(sb.isClosed());
                assertFalse(nativeParagraph(fresh).isClosed());
                assertThrows(IllegalStateException.class, () -> a.hitTest(0,0));
                assertThrows(IllegalStateException.class, () -> b.selection(0,1));
            }
        }
    }
    @Test void staleLayoutAccessAlsoClosesEveryStaleNativeParagraph() throws Exception {
        try (var runtime=AtomLibRuntime.open(); var fonts=new FontRegistry(runtime); var service=new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var a=service.layout(new TextRequest("first", BODY, 20, 0), width(100));
            var b=service.layout(new TextRequest("second", BODY, 20, 0), width(100));
            var pa=nativeParagraph(a); var pb=nativeParagraph(b);
            fonts.reload();
            assertThrows(IllegalStateException.class, () -> a.size());
            assertTrue(pa.isClosed()); assertTrue(pb.isClosed());
        }
    }
    @Test void staleNativeIsClosedEvenWhenNextLayoutFailsOrRegistryIsClosed() throws Exception {
        try (var runtime=AtomLibRuntime.open(); var fonts=new FontRegistry(runtime); var service=new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var first=service.layout(new TextRequest("first", BODY, 20, 0), width(100));
            var firstNative=nativeParagraph(first);
            fonts.reload();
            assertThrows(IllegalArgumentException.class, () -> service.layout(new TextRequest("x", new FontId("missing"), 20, 0), width(100)));
            assertTrue(firstNative.isClosed());
            var second=service.layout(new TextRequest("second", BODY, 20, 0), width(100));
            var secondNative=nativeParagraph(second);
            fonts.close();
            assertThrows(IllegalStateException.class, () -> second.hitTest(0,0));
            assertTrue(secondNative.isClosed());
        }
    }
    @Test void reloadAndConcurrentLayoutOperationsDoNotDeadlock() throws Exception {
        try (var runtime=AtomLibRuntime.open(); var fonts=new FontRegistry(runtime); var service=new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var first=service.layout(new TextRequest("first", BODY, 20, 0), width(100));
            var firstNative=nativeParagraph(first);
            var executor=java.util.concurrent.Executors.newFixedThreadPool(3);
            var start=new java.util.concurrent.CountDownLatch(1);
            try {
                var reload=executor.submit(() -> { start.await(); for (int i=0;i<25;i++) fonts.reload(); return null; });
                var query=executor.submit(() -> {
                    start.await();
                    for (int i=0;i<100;i++) try { first.hitTest(2,2); } catch (IllegalStateException stale) { break; }
                    return null;
                });
                var build=executor.submit(() -> {
                    start.await();
                    for (int i=0;i<50;i++) try (var current=service.layout(new TextRequest("text", BODY, 20, 0), width(100))) {
                        try { current.selection(0,1); } catch (IllegalStateException stale) { /* reload invalidated it */ }
                    }
                    return null;
                });
                start.countDown();
                reload.get(20, java.util.concurrent.TimeUnit.SECONDS);
                query.get(20, java.util.concurrent.TimeUnit.SECONDS);
                build.get(20, java.util.concurrent.TimeUnit.SECONDS);
                try (var current=service.layout(new TextRequest("end", BODY, 20, 0), width(100))) {
                    assertTrue(firstNative.isClosed());
                    assertTrue(current.size().width()>0);
                }
            } finally { executor.shutdownNow(); }
        }
    }
    @Test void paragraphGeometryAndDrawingShareTheSameLayout() {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime);
             var service = new ParagraphTextService(fonts); var surface = Surface.makeRasterN32Premul(300, 100)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            String text = "中e\u0301 ABC";
            try (var layout = service.layout(new TextRequest(text, BODY, 24, 0xff000000), width(250))) {
                assertTrue(layout.size().width() > 0);
                assertEquals(layout.selection(1, 3), layout.selection(2, 3));
                var boxes = layout.selection(0, text.length());
                assertFalse(boxes.isEmpty());
                assertEquals(0, layout.hitTest(-10, 5));
                assertEquals(text.length(), layout.hitTest(280, 5));
                surface.getCanvas().clear(0xffffffff);
                layout.draw(surface.getCanvas(), 5, 7);
                try (var image = surface.makeImageSnapshot(); var bitmap = new Bitmap()) {
                    bitmap.allocN32Pixels(300, 100); assertTrue(image.readPixels(bitmap));
                    int ink = 0;
                    for (int y=0; y<100; y++) for (int x=0; x<300; x++) {
                        if (bitmap.getColor(x,y) != 0xffffffff) {
                            ink++;
                            assertTrue(x >= 5 && y >= 7 && x < 5 + Math.ceil(layout.size().width()) && y < 7 + Math.ceil(layout.size().height()));
                        }
                    }
                    assertTrue(ink > 0);
                }
            }
        }
    }
    @Test void graphemeHitsAndSelectionNeverSplitClusters() {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime); var service = new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            String text = "中e\u0301👩‍💻文";
            try (var layout = service.layout(new TextRequest(text, BODY, 20, 0xff000000), width(300))) {
                Set<Integer> boundaries = Set.of(0,1,3,8,9);
                for (int x=0; x<150; x++) assertTrue(boundaries.contains(layout.hitTest(x, 5)));
                assertEquals(layout.selection(3,8), layout.selection(4,7));
            }
        }
    }
    @Test void lifecycleAndFontReloadRejectUseAfterCloseOrStaleGeneration() {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime); var service = new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var layout = service.layout(new TextRequest("abc", BODY, 20, 0), width(200));
            fonts.reload();
            assertThrows(IllegalStateException.class, () -> layout.hitTest(0,0));
            layout.close(); layout.close();
            var next = service.layout(new TextRequest("abc", BODY, 20, 0), width(200));
            service.close();
            assertThrows(IllegalStateException.class, () -> next.selection(0,1));
            assertThrows(IllegalStateException.class, () -> service.layout(next.request(), width(200)));
        }
    }
    @Test void emptyClippingAndMaxLines() {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime); var service = new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            try (var empty = service.layout(new TextRequest("", BODY, 20, 0), width(0));
                 var clipped = service.layout(new TextRequest("abc", BODY, 20, 0), new Constraints(Constraints.Axis.exactly(0), Constraints.Axis.exactly(0)));
                 var limited = service.layout(new TextRequest("one two three four five", BODY, 20, 0xff000000, 1, true), width(60))) {
                assertEquals(0, empty.size().height()); assertEquals(0, empty.hitTest(0,0));
                assertTrue(clipped.selection(0,3).isEmpty());
                assertEquals(0, clipped.size().height());
                assertTrue(limited.truncated());
                assertTrue(limited.lines().size() <= 1);
                assertTrue(limited.selection(14, 23).isEmpty(), "hidden text must not have selection geometry");
                assertTrue(limited.hitTest(1000, 2) < 14, "a click beyond an ellipsized line cannot select hidden text");
            }
        }
    }
    @Test void newlineFormsCreateRowsAndEndHitRemainsVisible() {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime); var service = new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            for (String separator : new String[]{"\n", "\r", "\r\n"}) {
                String text = "a" + separator + "b" + separator;
                try (var layout = service.layout(new TextRequest(text, BODY, 20, 0xff000000), width(200))) {
                    assertEquals(3, layout.lines().size(), "separator length=" + separator.length() + " first=" + (int)separator.charAt(0) + " lines=" + layout.lines() + " size=" + layout.size());
                    assertEquals(text.length(), layout.hitTest(100, layout.size().height()-1), separator);
                    assertFalse(layout.selection(0, 1).isEmpty());
                    assertFalse(layout.selection(1 + separator.length(), 2 + separator.length()).isEmpty());
                }
            }
        }
    }
    @Test void registeredFallbackIsUsedForMissingCjkGlyphs() {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime); var service = new ParagraphTextService(fonts)) {
            FontId fallback = new FontId("fallback");
            fonts.register(BODY, FontSource.resource("/com/atom/lib/fonts/bundled-default.otf"));
            fonts.register(fallback, FontSource.file(FontIsolationTest.fontPath()));
            fonts.setFallbacks(java.util.List.of(fallback));
            String glyph = "龘";
            try (var primaryFont = fonts.openFont(BODY, 24, ""); var primary = primaryFont.getTypeface();
                 var fallbackFont = fonts.openFont(fallback, 24, ""); var fallbackFace = fallbackFont.getTypeface()) {
                org.junit.jupiter.api.Assumptions.assumeTrue(primary.getUTF32Glyph(glyph.codePointAt(0)) == 0
                        && fallbackFace.getUTF32Glyph(glyph.codePointAt(0)) != 0, "test font must cover 龘 while bundled subset does not");
            }
            assertEquals(fallback, fonts.resolve(BODY, glyph), "fixture needs a glyph absent from the subset");
            try (var layout = service.layout(new TextRequest(glyph, BODY, 24, 0xff000000), width(100));
                 var direct = service.layout(new TextRequest(glyph, fallback, 24, 0xff000000), width(100))) {
                assertTrue(layout.size().width() > 0);
                assertEquals(direct.size().width(), layout.size().width(), 0.001, "fallback must shape with the resolved face");
                assertFalse(layout.selection(0, 1).isEmpty());
                try (var surface=Surface.makeRasterN32Premul(100, 60)) {
                    surface.getCanvas().clear(0xffffffff); layout.draw(surface.getCanvas(), 0, 0);
                    try (var image=surface.makeImageSnapshot(); var bitmap=new Bitmap()) {
                        bitmap.allocN32Pixels(100,60); assertTrue(image.readPixels(bitmap));
                        boolean ink=false;
                        for (int y=0;y<60 && !ink;y++) for (int x=0;x<100;x++) if (bitmap.getColor(x,y)!=0xffffffff) { ink=true; break; }
                        assertTrue(ink);
                    }
                }
            }
        }
    }
    @Test void undefinedWidthDoesNotWrapLongText() {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime); var service = new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var unconstrained = new Constraints(Constraints.Axis.undefined(), Constraints.Axis.undefined());
            try (var layout = service.layout(new TextRequest("W".repeat(12000), BODY, 120, 0xff000000), unconstrained)) {
                assertEquals(1, layout.lines().size(), "size=" + layout.size() + " lines=" + layout.lines());
                assertTrue(layout.size().width() > 1000000);
            }
        }
    }
    @Test void clippingConstrainsHitAndSelectionsAndInvalidArgumentsFail() {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime); var service = new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            try (var layout = service.layout(new TextRequest("W", BODY, 40, 0xff000000), width(2))) {
                assertEquals(layout.hitTest(2, 1), layout.hitTest(1000, 1));
                for (var rect : layout.selection(0,1)) assertTrue(rect.right() <= 2);
                assertThrows(IllegalArgumentException.class, () -> layout.hitTest(Float.NaN,0));
                assertThrows(IndexOutOfBoundsException.class, () -> layout.selection(-1,1));
            }
            assertThrows(IllegalArgumentException.class, () -> service.layout(new TextRequest("x", new FontId("missing"), 20, 0), width(20)));
            try (var valid = service.layout(new TextRequest("x", BODY, 20, 0), width(20))) {
                assertTrue(valid.size().width() > 0, "failed creation must leave service usable");
            }
        }
    }
    @Test void trailingSpacesHaveSelectableVisibleAdvance() {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime); var service = new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            try (var layout = service.layout(new TextRequest("a   ", BODY, 24, 0xff000000), width(200))) {
                assertFalse(layout.selection(1, 4).isEmpty());
                assertEquals(4, layout.hitTest(200, 5));
            }
        }
    }
    @Test void repeatedCreateDrawReloadAndCloseRemainsUsable() {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime);
             var service = new ParagraphTextService(fonts); var surface = Surface.makeRasterN32Premul(100, 60)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            for (int i=0;i<150;i++) {
                try (var layout = service.layout(new TextRequest("ab中 e\u0301", BODY, 20, 0xff000000), width(100))) {
                    layout.draw(surface.getCanvas(), 0, 0);
                    assertFalse(layout.selection(0, 3).isEmpty());
                }
                if (i % 30 == 0) fonts.reload();
            }
        }
    }
    @Test void concurrentGeometryAndCloseAreSerialized() throws Exception {
        try (var runtime = AtomLibRuntime.open(); var fonts = new FontRegistry(runtime); var service = new ParagraphTextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var layout = service.layout(new TextRequest("abc e\u0301 中文", BODY, 20, 0xff000000), width(100));
            var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
            try {
                var ready = new java.util.concurrent.CountDownLatch(4);
                var start = new java.util.concurrent.CountDownLatch(1);
                var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i=0;i<4;i++) tasks.add(executor.submit(() -> {
                    assertTrue(layout.hitTest(10, 2) >= 0);
                    ready.countDown();
                    try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                    for (int j=0;j<1000;j++) {
                        try {
                            int offset=layout.hitTest(10, 2);
                            assertTrue(offset>=0 && offset<=layout.request().text().length());
                            layout.selection(0,1);
                        } catch (IllegalStateException closed) { return; }
                    }
                }));
                assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
                start.countDown();
                layout.close();
                for (var task : tasks) task.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertThrows(IllegalStateException.class, () -> layout.hitTest(0,0));
            } finally { executor.shutdownNow(); }
        }
    }
    @Test void heightLimitClipsPaintingAndSelectionAndReportsTruncation() {
        try (var runtime=AtomLibRuntime.open(); var fonts=new FontRegistry(runtime);
             var service=new ParagraphTextService(fonts); var surface=Surface.makeRasterN32Premul(120, 80)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var bounds=new Constraints(Constraints.Axis.atMost(100), Constraints.Axis.exactly(12));
            try (var layout=service.layout(new TextRequest("first\nsecond", BODY, 24, 0xff000000, 2, true), bounds)) {
                assertEquals(12, layout.size().height());
                assertTrue(layout.truncated());
                assertTrue(layout.selection(6,11).isEmpty(), "fully clipped second line has no selection");
                for (var rect : layout.selection(0,5)) assertTrue(rect.bottom() <= 12);
                surface.getCanvas().clear(0xffffffff); layout.draw(surface.getCanvas(), 3, 4);
                try (var image=surface.makeImageSnapshot(); var bitmap=new Bitmap()) {
                    bitmap.allocN32Pixels(120,80); assertTrue(image.readPixels(bitmap));
                    int ink=0;
                    for (int y=0;y<80;y++) for (int x=0;x<120;x++) if (bitmap.getColor(x,y)!=0xffffffff) {
                        ink++; assertTrue(x>=3 && y>=4 && y<16);
                    }
                    assertTrue(ink>0);
                }
            }
        }
    }
}
