package com.atom.lib.design;

import com.atom.lib.design.atomchat.SkiaDraw;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShadowFilterLifecycleTest {
    @Test void explicitReleaseDetachesAndClosesSharedFiltersAndCanBeRepeated() throws Exception {
        try(var surface=Surface.makeRasterN32Premul(32,32)) {
            var release=assertDoesNotThrow(()->SkiaDraw.class.getMethod("releaseShadowFilters"));
            Field cacheField=SkiaDraw.class.getDeclaredField("BLUR_FILTERS");cacheField.setAccessible(true);
            @SuppressWarnings("unchecked") var cache=(Map<Float,MaskFilter>)cacheField.get(null);
            Field paintField=SkiaDraw.class.getDeclaredField("SHADOW_PAINT");paintField.setAccessible(true);
            Paint paint=(Paint)paintField.get(null);
            SkiaDraw.drawRoundedShadow(surface.getCanvas(),4,4,16,16,3,2.718f,0xff000000);
            var owned=cache.get(2.718f);assertNotNull(owned);assertTrue(owned.getRefCount()>=2);
            release.invoke(null);
            assertTrue(owned.isClosed());assertTrue(cache.isEmpty());assertNull(paint.getMaskFilter());
            release.invoke(null);
            SkiaDraw.drawRoundedShadow(surface.getCanvas(),4,4,16,16,3,2.718f,0xff000000);
            assertFalse(cache.get(2.718f).isClosed(),"Drawing after release creates a fresh usable filter");
        }
    }
    @Test void evictionReleasesCacheOwnedHandlesAfterDetachingPaint() throws Exception {
        try (var surface = Surface.makeRasterN32Premul(32,32)) {
            Field cacheField=SkiaDraw.class.getDeclaredField("BLUR_FILTERS");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked") var cache=(Map<Float,MaskFilter>)cacheField.get(null);
            Field paintField=SkiaDraw.class.getDeclaredField("SHADOW_PAINT");
            paintField.setAccessible(true);
            Paint paint=(Paint)paintField.get(null);
            float first=3.111f;
            SkiaDraw.drawRoundedShadow(surface.getCanvas(),4,4,16,16,3,first,0xff000000);
            MaskFilter owned=cache.get(first);
            assertNotNull(owned);
            assertTrue(owned.getRefCount()>=2,"Paint retains a separate native reference");
            try (MaskFilter borrowed=paint.getMaskFilter()) { assertNotNull(borrowed); }
            for(int i=1;i<=16;i++)
                SkiaDraw.drawRoundedShadow(surface.getCanvas(),4,4,16,16,3,first+i*.031f,0xff000000);
            assertTrue(owned.isClosed(),"Eviction must close the cache-owned native handle");
            assertTrue(cache.size()<=16);
            surface.getCanvas().clear(0);
            SkiaDraw.drawRoundedShadow(surface.getCanvas(),4,4,16,16,3,first+16*.031f,0xff000000);
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) {
                assertTrue((ImageIO.read(new ByteArrayInputStream(data.getBytes())).getRGB(12,12)>>>24)>0,
                        "Drawing must still work after eviction");
            }
        }
    }
}
