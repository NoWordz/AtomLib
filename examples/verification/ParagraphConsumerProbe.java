// SPDX-License-Identifier: Apache-2.0
package com.atom.example.verification;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.FontRegistry;
import com.atom.lib.layout.Constraints;
import com.atom.lib.text.ParagraphTextService;
import com.atom.lib.text.TextRequest;
import io.github.humbleui.skija.Surface;

/** Runs against the published platform coordinate, not merged project source. */
public final class ParagraphConsumerProbe {
    public static void main(String[] args) {
        try (var runtime = AtomLibRuntime.open("consumer-paragraph"); var fonts = new FontRegistry(runtime);
             var text = new ParagraphTextService(fonts); var surface = Surface.makeRasterN32Premul(200, 80)) {
            if (!fonts.registerDefaults().contains(FontRegistry.DEFAULT_REGULAR)) throw new AssertionError("bundled font unavailable");
            String value = "中e\u0301 hello";
            var bounds = new Constraints(Constraints.Axis.atMost(200), Constraints.Axis.undefined());
            try (var layout = text.layout(new TextRequest(value, FontRegistry.DEFAULT_REGULAR, 20, 0xff000000), bounds)) {
                if (layout.size().width() <= 0 || layout.selection(1, 3).isEmpty()) throw new AssertionError("paragraph geometry unavailable");
                if (!layout.selection(1, 3).equals(layout.selection(2, 3))) throw new AssertionError("grapheme selection differs");
                surface.getCanvas().clear(0xffffffff);
                layout.draw(surface.getCanvas(), 0, 0);
                if (layout.hitTest(0, 1) != 0) throw new AssertionError("incorrect hit result");
            }
        }
        System.out.println("PASS independent Paragraph consumer");
    }
}
