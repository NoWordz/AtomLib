// SPDX-License-Identifier: Apache-2.0
package com.atom.lib;

import com.atom.lib.resource.ResourceScope;

/** Owns host resources, never unloads the process-wide native library. */
public final class RuntimeHandle implements AutoCloseable {
    private final ResourceScope resources;
    RuntimeHandle(String namespace) { resources = new ResourceScope(namespace); }
    public String namespace() { return resources.namespace(); }
    public boolean isClosed() { return resources.isClosed(); }
    public void requireOpen() { if (isClosed()) throw new IllegalStateException("Runtime closed"); }
    public void own(AutoCloseable resource) {
        long generation = resources.generation();
        if (!resources.accept(generation, resource)) throw new IllegalStateException("Runtime closed");
    }
    @Override public void close() { resources.close(); }
}
