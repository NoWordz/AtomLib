package com.atom.lib.design.atomchat;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkiaDrawShadowCacheTest {
    @AfterEach
    void restore() {
        SkiaDraw.setDirectShadowForTest(false);
        SkiaDraw.releaseShadowFilters();
    }

    @Test
    void cachedShadowPreservesTheDirectPrimitive() {
        byte[] direct = render(true);
        SkiaDraw.releaseShadowFilters();
        byte[] cached = render(false);
        int maxDelta = 0;
        int changed = 0;
        for (int i = 0; i < direct.length; i++) {
            int delta = Math.abs((direct[i] & 0xFF) - (cached[i] & 0xFF));
            if (delta != 0) changed++;
            maxDelta = Math.max(maxDelta, delta);
        }
        System.out.printf("SHADOW_PIXEL_DELTA changed=%d/%d max=%d%n", changed, direct.length, maxDelta);
        assertTrue(maxDelta <= 1,
                "pre-rasterising one shadow must stay within one channel level");
    }

    @Test
    void repeatedDrawsReuseTheRasterShadow() {
        try (Surface surface = Surface.makeRasterN32Premul(180, 100)) {
            SkiaDraw.drawRoundedShadow(surface.getCanvas(), 20, 20, 120, 60, 12, 6, 0x60000000);
            long first = allocated();
            for (int i = 0; i < 100; i++) {
                SkiaDraw.drawRoundedShadow(surface.getCanvas(), 20, 20, 120, 60, 12, 6, 0x60000000);
            }
            long subsequent = allocated() - first;
            assertTrue(subsequent < 100 * 4L * 1024L,
                    "repeated shadow draws must not rebuild the raster image");
        }
    }

    private static byte[] render(boolean direct) {
        SkiaDraw.setDirectShadowForTest(direct);
        try (Surface surface = Surface.makeRasterN32Premul(180, 100); Bitmap bitmap = new Bitmap()) {
            surface.getCanvas().clear(0xFF1F2937);
            SkiaDraw.drawRoundedShadow(surface.getCanvas(), 20, 20, 120, 60, 12, 6, 0x60000000);
            bitmap.allocN32Pixels(180, 100);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        }
    }

    private static long allocated() {
        var mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        return mx.getCurrentThreadAllocatedBytes();
    }
}
