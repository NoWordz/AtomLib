// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.FontId;
import com.atom.lib.font.FontRegistry;
import io.github.humbleui.skija.Font;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Direction F: the bundled Noto Sans SC subset must give CJK out of the box. */
class BundledDefaultFontTest {
    @Test void registerDefaultsLoadsBothFacesAndRendersCjk() {
        try (var runtime = AtomLibRuntime.open("default-fonts");
             var registry = new FontRegistry(runtime)) {
            assertEquals(List.of(FontRegistry.DEFAULT_REGULAR, FontRegistry.DEFAULT_BOLD),
                    registry.registerDefaults());
            try (Font regular = registry.openFont(FontRegistry.DEFAULT_REGULAR, 16, "中文");
                 Font bold = registry.openFont(FontRegistry.DEFAULT_BOLD, 16, "中文")) {
                assertTrue(regular.measureTextWidth("中文abc") > 0);
                assertTrue(bold.measureTextWidth("中文") > 0);
            }
            // Both ids are registered faces, so hosts may reference them directly.
            registry.setFallbacks(List.of(FontRegistry.DEFAULT_REGULAR, FontRegistry.DEFAULT_BOLD));
        }
    }

    @Test void registerDefaultsIsIdempotentAndKeepsWorking() {
        try (var runtime = AtomLibRuntime.open("default-fonts-idempotent");
             var registry = new FontRegistry(runtime)) {
            registry.registerDefaults();
            long before = registry.generation();
            assertEquals(List.of(FontRegistry.DEFAULT_REGULAR, FontRegistry.DEFAULT_BOLD),
                    registry.registerDefaults());
            assertTrue(registry.generation() > before);
            // Re-registration replaced the faces, so the reserved ids stay
            // usable and remain valid fallback entries.
            registry.setFallbacks(List.of(FontRegistry.DEFAULT_BOLD, FontRegistry.DEFAULT_REGULAR));
            try (Font font = registry.openFont(FontRegistry.DEFAULT_REGULAR, 16, "é中")) {
                assertTrue(font.measureTextWidth("é中") > 0);
            }
        }
    }
}
