package com.atom.lib.layout.internal;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

/** The native must reach LWJGL even when it only exists inside a mod jar. */
class YogaNativesTest {
    private static String libraryName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows") ? "lwjgl_yoga.dll"
                : os.contains("mac") || os.contains("darwin") ? "liblwjgl_yoga.dylib" : "liblwjgl_yoga.so";
    }

    @Test void extractsThePlatformLibraryFromTheNativesJar() throws Exception {
        Path directory = YogaNatives.extractNative();
        assertNotNull(directory, "the Yoga natives jar must be on the test classpath");
        Path library = directory.resolve(libraryName());
        assertTrue(Files.isRegularFile(library), library.toString());
        assertTrue(Files.size(library) > 20_000, "extracted library looks truncated: " + Files.size(library));
    }

    @Test void extendsLwjglLibraryPathExactlyOnce() throws Exception {
        Path directory = YogaNatives.extractNative();
        assertNotNull(directory);
        YogaNatives.extendLibraryPath(directory);
        String first = org.lwjgl.system.Configuration.LIBRARY_PATH.get();
        assertNotNull(first);
        assertTrue(first.contains(directory.toAbsolutePath().toString()), first);
        YogaNatives.extendLibraryPath(directory);
        assertEquals(first, org.lwjgl.system.Configuration.LIBRARY_PATH.get(), "the path must not be added twice");
    }
}
