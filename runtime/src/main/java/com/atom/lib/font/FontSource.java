// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** A repeatable host-owned source. No font bytes are shipped with AtomLib. */
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
}
