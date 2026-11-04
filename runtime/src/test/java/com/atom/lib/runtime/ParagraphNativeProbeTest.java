// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.paragraph.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the installed native binary, not just the Java class inventory. */
class ParagraphNativeProbeTest {
    @Test void layoutKeepsFaceAfterProviderAndRegistryReload() {
        try (var runtime = AtomLibRuntime.open("paragraph-reload"); var fonts = new FontRegistry(runtime);
             var surface = Surface.makeRasterN32Premul(300, 120)) {
            var id = new FontId("probe");
            fonts.register(id, FontSource.file(FontIsolationTest.fontPath()));
            Paragraph paragraph;
            try (var font = fonts.openFont(id, 20, ""); var face = font.getTypeface();
                 var provider = new TypefaceFontProvider(); var collection = new FontCollection();
                 var style = new TextStyle(); var paragraphStyle = new ParagraphStyle()) {
                provider.registerTypeface(face, "probe-reload");
                collection.setAssetFontManager(provider).setEnableFallback(false);
                style.setFontFamily("probe-reload").setFontSize(20).setColor(0xff000000);
                paragraphStyle.setTextStyle(style);
                try (var builder = new ParagraphBuilder(paragraphStyle, collection)) {
                    builder.addText("中文 reload e\u0301");
                    paragraph = builder.build();
                }
            }
            try (paragraph) {
                fonts.reload();
                paragraph.layout(300);
                assertTrue(paragraph.getHeight() > 0);
                assertTrue(paragraph.getRectsForRange(0, 2, RectHeightMode.TIGHT, RectWidthMode.TIGHT).length > 0);
                surface.getCanvas().clear(0xffffffff);
                paragraph.paint(surface.getCanvas(), 0, 0);
                try (var image = surface.makeImageSnapshot(); var bitmap = new Bitmap()) {
                    bitmap.allocN32Pixels(300, 120);
                    assertTrue(image.readPixels(bitmap));
                    boolean ink = false;
                    for (int y = 0; y < 120 && !ink; y++) for (int x = 0; x < 300; x++) {
                        if (bitmap.getColor(x, y) != 0xffffffff) { ink = true; break; }
                    }
                    assertTrue(ink);
                }
            }
        }
    }

    @Test void registeredFaceCanLayoutPaintHitSelectAndClose() {
        String sample = "中e\u0301 👩‍💻 abc שלום";
        try (var runtime = AtomLibRuntime.open("paragraph-probe"); var fonts = new FontRegistry(runtime);
             var surface = Surface.makeRasterN32Premul(400, 120)) {
            var id = new FontId("probe");
            fonts.register(id, FontSource.file(FontIsolationTest.fontPath()));
            try (var font = fonts.openFont(id, 20, ""); var face = font.getTypeface();
                 var provider = new TypefaceFontProvider(); var collection = new FontCollection();
                 var style = new TextStyle(); var paragraphStyle = new ParagraphStyle()) {
                provider.registerTypeface(face, "probe-face");
                collection.setAssetFontManager(provider).setEnableFallback(false);
                style.setFontFamily("probe-face").setFontSize(20).setColor(0xff000000);
                paragraphStyle.setTextStyle(style);
                try (var builder = new ParagraphBuilder(paragraphStyle, collection)) {
                    builder.addText(sample);
                    try (var paragraph = builder.build()) {
                        paragraph.layout(300);
                        assertTrue(paragraph.getHeight() > 0);
                        assertEquals(sample.length(), paragraph.getLineMetrics()[0].getEndIndex());
                        assertTrue(paragraph.getRectsForRange(1, 3, RectHeightMode.TIGHT, RectWidthMode.TIGHT).length > 0);
                        assertTrue(paragraph.getRectsForRange(4, 9, RectHeightMode.TIGHT, RectWidthMode.TIGHT).length > 0);
                        int hit = paragraph.getGlyphPositionAtCoordinate(0, 5).getPosition();
                        assertTrue(hit >= 0 && hit <= sample.length());
                        surface.getCanvas().clear(0xffffffff);
                        paragraph.paint(surface.getCanvas(), 0, 0);
                        try (var image = surface.makeImageSnapshot(); var bitmap = new Bitmap()) {
                            bitmap.allocN32Pixels(400, 120);
                            assertTrue(image.readPixels(bitmap));
                            boolean ink = false;
                            for (int y = 0; y < 120 && !ink; y++) for (int x = 0; x < 400; x++) {
                                if (bitmap.getColor(x, y) != 0xffffffff) { ink = true; break; }
                            }
                            assertTrue(ink, "Paragraph must actually paint pixels");
                        }
                    }
                }
            }
        }
    }
}
