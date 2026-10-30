// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.geometry.Rect;
import io.github.humbleui.skija.impl.Library;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeLoadingTest {
    @Test void classLoadingDoesNotInitializeNativeAndCloseDoesNotUnload() throws Exception {
        Set<String> classpath = new LinkedHashSet<>();
        for (Class<?> type : List.of(NativeLoadingProbe.class, AtomLibRuntime.class, Rect.class, Library.class, io.github.humbleui.types.Rect.class))
            classpath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        // Locate the native jar without changing production startup or relying on global library paths.
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        String arch = Set.of("aarch64", "arm64").contains(System.getProperty("os.arch")) ? "arm64" : "x64";
        String platform = windows ? "windows" : "linux";
        String binary = windows ? "skija.dll" : "libskija.so";
        var resource = Library.class.getClassLoader().getResource("io/github/humbleui/skija/" + platform + "/" + arch + "/" + binary);
        assertNotNull(resource, "Current-platform native test runtime must be configured");
        var nativeJar = (java.net.JarURLConnection) resource.openConnection();
        classpath.add(Path.of(nativeJar.getJarFileURL().toURI()).toString());
        var command = List.of(Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString(), "-cp",
                String.join(File.pathSeparator, classpath), NativeLoadingProbe.class.getName());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Native probe timed out");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            for (String expected : List.of("before=false", "afterClasses=false", "opened=true", "afterClose=true"))
                assertTrue(output.contains(expected), output);
        } finally { process.destroyForcibly(); }
    }
}
