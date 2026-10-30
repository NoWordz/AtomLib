// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.resource.ResourceScope;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ResourceScopeTest {
    @Test void staleCompletionIsDisposedAndHostsAreIsolated() {
        AtomicInteger disposed = new AtomicInteger();
        try (ResourceScope a = new ResourceScope("alpha"); ResourceScope b = new ResourceScope("beta")) {
            long old = a.generation();
            assertTrue(a.accept(old, disposed::incrementAndGet));
            a.reload();
            assertEquals(1, disposed.get());
            assertFalse(a.accept(old, disposed::incrementAndGet));
            assertEquals(2, disposed.get());
            assertEquals(0, b.generation());
            assertTrue(b.accept(b.generation(), disposed::incrementAndGet));
            a.close(); a.close();
            assertEquals(2, disposed.get());
        }
        assertEquals(3, disposed.get());
    }
    @Test void lateCompletionAfterCloseIsDisposed() {
        ResourceScope scope = new ResourceScope("host");
        long ticket = scope.generation(); scope.close();
        AtomicInteger disposed = new AtomicInteger();
        assertFalse(scope.accept(ticket, disposed::incrementAndGet));
        assertEquals(1, disposed.get());
    }
    @Test void closeContinuesAfterResourceFailureInReverseOrder() {
        StringBuilder order = new StringBuilder();
        ResourceScope scope = new ResourceScope("host");
        scope.accept(0, () -> order.append('a'));
        scope.accept(0, () -> { order.append('b'); throw new Exception("broken"); });
        scope.accept(0, () -> order.append('c'));
        assertThrows(IllegalStateException.class, scope::close);
        assertEquals("cba", order.toString());
        scope.close();
    }
    @Test void evictionReleasesOwnershipExactlyOnce() {
        AtomicInteger disposed = new AtomicInteger();
        AutoCloseable resource = disposed::incrementAndGet;
        try (var scope = new ResourceScope("host")) {
            scope.accept(0, resource);
            assertTrue(scope.release(resource));
            assertFalse(scope.release(resource));
        }
        assertEquals(1, disposed.get());
    }
    @Test void reloadReleasesOutsideMonitorAndLateWorkerResultIsDisposed() throws Exception {
        java.util.concurrent.CountDownLatch disposing = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finish = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger disposed = new AtomicInteger();
        try (var scope = new ResourceScope("race")) {
            long ticket = scope.generation();
            scope.accept(ticket, () -> { disposing.countDown(); assertTrue(finish.await(3, java.util.concurrent.TimeUnit.SECONDS)); });
            var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                var reloading = worker.submit(scope::reload);
                assertTrue(disposing.await(3, java.util.concurrent.TimeUnit.SECONDS));
                assertFalse(scope.accept(ticket, disposed::incrementAndGet));
                assertEquals(1, disposed.get());
                finish.countDown(); reloading.get(3, java.util.concurrent.TimeUnit.SECONDS);
            } finally { finish.countDown(); worker.shutdownNow(); }
        }
    }
}
