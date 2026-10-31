// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.font;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A repeatable source of font bytes. Hosts own their fonts; AtomLib
 * additionally ships a subsetted Noto Sans SC default (see
 * {@link FontRegistry#registerDefaults()}) exposed through {@link #resource}.
 */
@FunctionalInterface
public interface FontSource {
    byte[] read() throws IOException;
    static FontSource file(Path path) {
        Objects.requireNonNull(path);
        return () -> Files.readAllBytes(path);
    }
    static FontSource bytes(byte[] bytes) {
        byte[] copy = Objects.requireNonNull(bytes).clone();
        return () -> copy.clone();
    }
    /** Reads a classpath resource, e.g. the library-bundled default faces. */
    static FontSource resource(String path) {
        Objects.requireNonNull(path);
        return () -> {
            try (InputStream in = FontSource.class.getResourceAsStream(path)) {
                if (in == null) throw new IOException("Missing classpath resource " + path);
                return in.readAllBytes();
            }
        };
    }
}
