package com.atom.lib.layout.internal;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * LWJGL resolves a native from {@code java.library.path} or
 * {@code org.lwjgl.librarypath}, and caches both when its Configuration class
 * initializes — long before any mod runs. A native nested inside a mod jar is
 * therefore invisible to it, which is exactly how lwjgl_yoga.dll went missing
 * inside Minecraft while every Gradle test passed.
 *
 * <p>This extracts the platform library next to the game and extends LWJGL's
 * already-initialized library path before the first Yoga call. When the
 * library is already reachable nothing is extracted.</p>
 */
final class YogaNatives {
    private static boolean prepared;

    private YogaNatives() { }

    static synchronized void ensure() {
        if (prepared) return;
        prepared = true;
        try {
            Path directory = extractNative();
            if (directory != null) extendLibraryPath(directory);
        } catch (Throwable ignored) {
            // Leave the diagnostics to LWJGL: it reports the missing library with
            // the platform it searched, which is more useful than a wrapped error.
        }
    }

    /** Extracts the platform library into a content-addressed temp directory. */
    static Path extractNative() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64") || arch.startsWith("arm");
        String platform = os.startsWith("windows")
                ? "windows/" + (arm ? "arm64" : "x64")
                : os.contains("mac") || os.contains("darwin")
                ? "macos/" + (arm ? "arm64" : "x64")
                : "linux/" + (arm ? "arm64" : "x64");
        String fileName = os.startsWith("windows") ? "lwjgl_yoga.dll"
                : os.contains("mac") || os.contains("darwin") ? "liblwjgl_yoga.dylib" : "liblwjgl_yoga.so";
        String resource = platform + "/org/lwjgl/yoga/" + fileName;
        byte[] library = read("/" + resource);
        if (library == null) library = readFromEmbeddedNatives(platform, fileName);
        if (library == null) return null;
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "atomlib-natives", digest(library));
        Files.createDirectories(directory);
        Path target = directory.resolve(fileName);
        if (!Files.isRegularFile(target) || Files.size(target) != library.length) {
            Path temporary = Files.createTempFile(directory, fileName, ".tmp");
            Files.write(temporary, library);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return directory;
    }

    private static byte[] read(String resource) throws Exception {
        try (InputStream in = YogaNatives.class.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    /** Fallback for loaders that keep nested jars out of the classpath resources. */
    private static byte[] readFromEmbeddedNatives(String platform, String fileName) {
        String entry = platform + "/org/lwjgl/yoga/" + fileName;
        try {
            var source = YogaNatives.class.getProtectionDomain().getCodeSource();
            if (source == null) return null;
            Path self = Path.of(source.getLocation().toURI());
            if (!Files.isRegularFile(self)) return null;
            try (ZipFile jar = new ZipFile(self.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var candidate = entries.nextElement();
                    String name = candidate.getName();
                    if (!name.startsWith("META-INF/jarjar/") || !name.contains("yoga") || !name.endsWith(".jar")) continue;
                    try (InputStream in = jar.getInputStream(candidate); ZipInputStream nested = new ZipInputStream(in)) {
                        var nestedEntry = nested.getNextEntry();
                        while (nestedEntry != null) {
                            if (entry.equals(nestedEntry.getName())) return nested.readAllBytes();
                            nestedEntry = nested.getNextEntry();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    static void extendLibraryPath(Path directory) {
        String current = org.lwjgl.system.Configuration.LIBRARY_PATH.get();
        String path = directory.toAbsolutePath().toString();
        if (current != null && !current.isEmpty() && (current.equals(path) || current.startsWith(path + File.pathSeparator) || current.contains(File.pathSeparator + path))) return;
        org.lwjgl.system.Configuration.LIBRARY_PATH.set(current == null || current.isEmpty() ? path : current + File.pathSeparator + path);
    }

    private static String digest(byte[] library) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(library);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 8; i++) text.append(String.format(Locale.ROOT, "%02x", hash[i]));
        return text.toString();
    }
}
