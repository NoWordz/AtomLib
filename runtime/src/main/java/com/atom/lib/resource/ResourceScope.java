// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Host-local ownership. accept transfers ownership, including rejected/stale results. */
public final class ResourceScope implements AutoCloseable {
    private final String namespace;
    private final List<AutoCloseable> resources = new ArrayList<>();
    private long generation;
    private boolean closed;

    public ResourceScope(String namespace) {
        this.namespace = Objects.requireNonNull(namespace);
        if (namespace.isBlank()) throw new IllegalArgumentException("Empty namespace");
    }
    public String namespace() { return namespace; }
    public synchronized long generation() { return generation; }
    public synchronized boolean isClosed() { return closed; }
    public boolean accept(long ticket, AutoCloseable resource) {
        Objects.requireNonNull(resource);
        synchronized (this) {
            if (!closed && ticket == generation) {
                if (resources.stream().anyMatch(r -> r == resource)) throw new IllegalArgumentException("Already owned");
                resources.add(resource);
                return true;
            }
        }
        dispose(List.of(resource));
        return false;
    }
    /** Detach and dispose an owned resource (e.g. cache eviction). */
    public boolean release(AutoCloseable resource) {
        Objects.requireNonNull(resource);
        synchronized (this) {
            int found = -1;
            for (int i = 0; i < resources.size(); i++) if (resources.get(i) == resource) { found = i; break; }
            if (found < 0) return false;
            resources.remove(found);
        }
        dispose(List.of(resource));
        return true;
    }
    public void reload() {
        List<AutoCloseable> old;
        synchronized (this) {
            if (closed) throw new IllegalStateException("Scope closed");
            generation++;
            old = detach();
        }
        dispose(old);
    }
    @Override public void close() {
        List<AutoCloseable> old;
        synchronized (this) {
            if (closed) return;
            closed = true;
            generation++;
            old = detach();
        }
        dispose(old);
    }
    private List<AutoCloseable> detach() {
        List<AutoCloseable> old = new ArrayList<>(resources);
        resources.clear();
        return old;
    }
    private static void dispose(List<AutoCloseable> old) {
        IllegalStateException failure = null;
        for (int i = old.size() - 1; i >= 0; i--) {
            try { old.get(i).close(); }
            catch (Exception e) {
                if (failure == null) failure = new IllegalStateException("Resource disposal failed", e);
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
    }
}
