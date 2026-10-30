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

class TextLayoutTest {
    private static final FontId BODY = new FontId("body");
    private static Constraints width(float width) {
        return new Constraints(Constraints.Axis.atMost(width), Constraints.Axis.undefined());
    }
    @Test void wrapAndHitNeverSplitGraphemes() {
        try (var handle = AtomLibRuntime.open("text"); var fonts = new FontRegistry(handle); var text = new TextService(fonts, 2, 200)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            String input = "中e\u0301👩‍💻🇨🇳文";
            var layout = text.layout(new TextRequest(input, BODY, 20, 0xff000000), width(1));
            Set<Integer> boundaries = Set.of(0, 1, 3, 8, 12, 13);
            assertEquals(5, layout.lines().size());
            for (var line : layout.lines()) {
                assertTrue(boundaries.contains(line.start())); assertTrue(boundaries.contains(line.end()));
                assertTrue(boundaries.contains(layout.hitTest(100, line.top() + 1)));
            }
            assertEquals(1, layout.size().width());
            assertThrows(UnsupportedOperationException.class, () -> layout.lines().clear());
        }
    }
    @Test void sharedLayoutDrivesDrawingSelectionAndGenerationInvalidation() {
        try (var handle = AtomLibRuntime.open("text"); var fonts = new FontRegistry(handle); var text = new TextService(fonts);
             var surface = Surface.makeRasterN32Premul(300, 100)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var request = new TextRequest("AV中文", BODY, 24, 0xff000000);
            var layout = text.layout(request, width(300));
            assertSame(layout, text.layout(request, width(300)));
            assertTrue(layout.size().width() > 10);
            var selected = layout.selection(0, 4);
            assertEquals(1, selected.size());
            assertEquals(layout.size().width(), selected.get(0).width(), 0.02);
            surface.getCanvas().clear(0xffffffff);
            text.draw(surface.getCanvas(), layout, 0, 0);
            try (var image = surface.makeImageSnapshot(); var bitmap = new Bitmap()) {
                bitmap.allocN32Pixels(300, 100); assertTrue(image.readPixels(bitmap));
                int ink = 0, overflow = 0;
                for (int y = 0; y < 100; y++) for (int x = 0; x < 300; x++) if (bitmap.getColor(x,y) != 0xffffffff) {
                    ink++;
                    if (x >= Math.ceil(layout.size().width()) || y >= Math.ceil(layout.size().height())) overflow++;
                }
                assertTrue(ink > 0, "Drawing must produce real text pixels"); assertEquals(0, overflow);
            }
            fonts.reload();
            assertThrows(IllegalStateException.class, () -> text.draw(surface.getCanvas(), layout, 0, 0));
            assertNotSame(layout, text.layout(request, width(300)));
        }
    }
    @Test void cacheIsBoundedAndConstraintsArePartOfIdentity() {
        try (var handle = AtomLibRuntime.open(); var fonts = new FontRegistry(handle); var text = new TextService(fonts, 2, 20)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var a = new TextRequest("中文中文", BODY, 16, 0xff000000);
            var narrow = text.layout(a, width(20)); var wide = text.layout(a, width(200));
            assertTrue(narrow.lines().size() > wide.lines().size());
            text.layout(new TextRequest("third", BODY, 20, 0xffffffff), width(200));
            assertEquals(2, text.cachedLayoutCount());
            assertNotSame(narrow, text.layout(a, width(20)));
            text.layout(new TextRequest("a".repeat(100), BODY, 16, 0), width(200));
            assertTrue(text.cachedCharacterCount() <= 20);
        }
    }
    @Test void ellipsisHeightZeroAndEmptyTextStayFinite() {
        try (var handle = AtomLibRuntime.open(); var fonts = new FontRegistry(handle); var text = new TextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var result = text.layout(new TextRequest("中文中文中文", BODY, 18, 0xff000000, 1, true), width(50));
            assertTrue(result.truncated()); assertEquals(1, result.lines().size());
            assertTrue(result.lines().get(0).runs().stream().anyMatch(r -> r.text().endsWith("…")));
            var empty = text.layout(new TextRequest("", BODY, 18, 0), width(0));
            assertEquals(0, empty.size().height()); assertEquals(0, empty.hitTest(0, 0));
            var zero = text.layout(new TextRequest("abc", BODY, 18, 0), new Constraints(Constraints.Axis.exactly(0), Constraints.Axis.exactly(0)));
            assertEquals(0, zero.size().width()); assertEquals(0, zero.size().height());
            assertTrue(zero.selection(0, 3).isEmpty());
        }
    }
    @Test void trailingNewlineHasCaretRowAndEllipsisRetainsSecondLineOffset() {
        try (var handle = AtomLibRuntime.open(); var fonts = new FontRegistry(handle); var text = new TextService(fonts)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var newline = text.layout(new TextRequest("a\n", BODY, 18, 0), width(100));
            assertEquals(2, newline.lines().size());
            assertEquals(2, newline.lines().get(1).start());
            assertEquals(2, newline.hitTest(0, newline.size().height()-1));
            var dots = text.layout(new TextRequest("first\nsecond third", BODY, 18, 0, 2, true), width(1));
            assertEquals(1, dots.lines().get(1).start());
            assertEquals(1, dots.hitTest(0, dots.lines().get(1).top()+1));
        }
    }
    @Test void requestRejectsUnpairedSurrogatesAndUnsupportedBidiInsteadOfCorruptingOffsets() {
        assertThrows(IllegalArgumentException.class, () -> new TextRequest(String.valueOf((char) 0xd83d), BODY, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> new TextRequest("עברית", BODY, 16, 0));
    }
    @Test void partialGraphemeSelectionExpandsAndForeignLayoutsAreRejected() {
        try (var handle = AtomLibRuntime.open(); var fonts = new FontRegistry(handle); var text = new TextService(fonts); var other = new TextService(fonts);
             var surface = Surface.makeRasterN32Premul(100, 100)) {
            fonts.register(BODY, FontSource.file(FontIsolationTest.fontPath()));
            var layout = text.layout(new TextRequest("éx", BODY, 24, 0), width(100));
            assertEquals(layout.selection(0,2), layout.selection(1,2));
            assertThrows(IllegalStateException.class, () -> other.draw(surface.getCanvas(), layout, 0, 0));
            text.close();
            assertThrows(IllegalStateException.class, () -> text.layout(layout.request(), width(100)));
        }
    }
}
