// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.*;
import io.github.humbleui.skija.Font;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FontIsolationTest {
    static Path fontPath() {
        String path = System.getProperty("atomlib.testFont");
        assertNotNull(path, "Pass explicit -Datomlib.testFont=<host font absolute path> to test JVM");
        assertTrue(Path.of(path).isAbsolute());
        return Path.of(path);
    }
    @Test void callerFontsAreFreshAndSurviveRegistryReload() {
        try (var runtime = AtomLibRuntime.open("fonts"); var registry = new FontRegistry(runtime)) {
            FontId id = new FontId("body");
            registry.register(id, FontSource.file(fontPath()));
            try (Font a = registry.openFont(id, 16, "中文"); Font b = registry.openFont(id, 16, "中文")) {
                float width = a.measureTextWidth("中文abc");
                a.setSize(40);
                assertEquals(width, b.measureTextWidth("中文abc"), 0.01);
                long before = registry.generation();
                registry.reload();
                assertTrue(registry.generation() > before);
                assertEquals(width, b.measureTextWidth("中文abc"), 0.01);
            }
        }
    }
    @Test void invalidReplacementIsAtomicAndHostsStayIndependent() {
        try (var a = AtomLibRuntime.open("a"); var b = AtomLibRuntime.open("b");
             var left = new FontRegistry(a); var right = new FontRegistry(b)) {
            FontId id = new FontId("same"); left.register(id, FontSource.file(fontPath()));
            long gen = left.generation();
            assertThrows(IllegalArgumentException.class, () -> left.register(id, FontSource.bytes(new byte[]{1,2,3})));
            assertEquals(gen, left.generation());
            assertThrows(IllegalArgumentException.class, () -> right.openFont(id, 16, "a"));
            assertThrows(IllegalArgumentException.class, () -> left.setFallbacks(List.of(new FontId("missing"))));
            try (Font f = left.openFont(id, 16, "a")) { assertTrue(f.measureTextWidth("abc") > 0); }
            a.close(); a.close();
            assertThrows(IllegalStateException.class, () -> left.openFont(id, 16, "a"));
        }
    }
    @Test void failedReloadPreservesAllFontsAndGeneration() {
        try (var handle = AtomLibRuntime.open(); var registry = new FontRegistry(handle)) {
            byte[] bytes;
            try { bytes = java.nio.file.Files.readAllBytes(fontPath()); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
            FontId id = new FontId("reload");
            registry.register(id, () -> {
                if (reads.incrementAndGet() > 1) throw new java.io.IOException("Host source unavailable");
                return bytes;
            });
            registry.setFallbacks(List.of(id));
            long before = registry.generation();
            assertThrows(IllegalArgumentException.class, registry::reload);
            assertEquals(before, registry.generation());
            try (var font = registry.openFont(id, 18, "中文")) { assertTrue(font.measureTextWidth("中文") > 0); }
        }
    }
}
