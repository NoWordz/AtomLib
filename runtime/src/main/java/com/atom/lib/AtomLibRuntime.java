// SPDX-License-Identifier: Apache-2.0
package com.atom.lib;

import io.github.humbleui.skija.impl.Library;
import java.util.UUID;

/** No native work occurs until a host explicitly opens the runtime. */
public final class AtomLibRuntime {
    private AtomLibRuntime() {}
    public static RuntimeHandle open() { return open("host-" + UUID.randomUUID()); }
    public static RuntimeHandle open(String namespace) {
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("Empty namespace");
        Library.load();
        return new RuntimeHandle(namespace);
    }
}
