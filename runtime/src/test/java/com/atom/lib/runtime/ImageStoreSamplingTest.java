// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.geometry.Rect;
import com.atom.lib.image.*;
import io.github.humbleui.skija.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageStoreSamplingTest {
    private static Method drawWithSampling() {
        return assertDoesNotThrow(()->ImageStore.class.getMethod("draw",String.class,Canvas.class,Rect.class,ImageFit.class,float.class,
                ImageDownsampler.class,float.class,SamplingMode.class,boolean.class),"host must opt in to a bounded downsampling cache");
    }
    @Test void storeUsesBorrowedRasterCacheAndSourceReloadInvalidatesDerivedImage() throws Exception {
        var method=drawWithSampling();
        try(var host=AtomLibRuntime.open("image-sampling");var store=new ImageStore(host);var cache=new ImageDownsampler(4096,4,2);
            var src=Surface.makeRasterN32Premul(64,64);var dest=Surface.makeRasterN32Premul(40,40);var bitmap=new Bitmap()) {
            src.getCanvas().clear(0xffff4020);var image=src.makeImageSnapshot();
            assertTrue(store.acceptDecoded("one",store.generation(),image));
            method.invoke(store,"one",dest.getCanvas(),new Rect(0,0,20,20),ImageFit.CONTAIN,0f,cache,1f,SamplingMode.LINEAR,false);
            assertEquals(1,cache.size());assertEquals(400,cache.pixels());
            try(var result=dest.makeImageSnapshot()){bitmap.allocN32Pixels(40,40);assertTrue(result.readPixels(bitmap));assertEquals(0xffff4020,bitmap.getColor(10,10));}
            store.reload();assertTrue(image.isClosed());assertEquals(0,cache.size());
        }
    }
    @Test void nearestPixelArtRetainsSharpPixelsAndNeverBuildsADerivative() throws Exception {
        var method=drawWithSampling();
        try(var host=AtomLibRuntime.open("pixel-art");var store=new ImageStore(host);var cache=new ImageDownsampler(4096,4,2);
            var src=Surface.makeRasterN32Premul(64,64);var dest=Surface.makeRasterN32Premul(12,12);var paint=new Paint().setColor(0xffffffff);var bitmap=new Bitmap()) {
            src.getCanvas().clear(0xff000000);
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)if((x+y)%2==0)src.getCanvas().drawRect(io.github.humbleui.types.Rect.makeXYWH(x,y,1,1),paint);
            assertTrue(store.acceptDecoded("pixel",store.generation(),src.makeImageSnapshot()));
            method.invoke(store,"pixel",dest.getCanvas(),new Rect(0,0,10,10),ImageFit.CONTAIN,0f,cache,1f,SamplingMode.DEFAULT,true);
            assertEquals(0,cache.size());
            try(var result=dest.makeImageSnapshot()){bitmap.allocN32Pixels(12,12);assertTrue(result.readPixels(bitmap));
                for(int y=0;y<10;y++)for(int x=0;x<10;x++){int c=bitmap.getColor(x,y);assertTrue(c==0xff000000||c==0xffffffff,"nearest must not invent colors");}}
        }
    }
    @Test void coverUsesFullScaledImageAtPhysicalDensityAndClipsToBounds() throws Exception {
        var method=drawWithSampling();
        try(var host=AtomLibRuntime.open("cover-density");var store=new ImageStore(host);var cache=new ImageDownsampler(10000,4,2);
            var src=Surface.makeRasterN32Premul(128,64);var dest=Surface.makeRasterN32Premul(40,40);var paint=new Paint().setColor(0xff0000ff);var bitmap=new Bitmap()) {
            src.getCanvas().clear(0xffff0000);src.getCanvas().drawRect(io.github.humbleui.types.Rect.makeXYWH(64,0,64,64),paint);
            assertTrue(store.acceptDecoded("cover",store.generation(),src.makeImageSnapshot()));
            dest.getCanvas().clear(0xffffffff);int saved=dest.getCanvas().getSaveCount();
            method.invoke(store,"cover",dest.getCanvas(),new Rect(10,10,20,20),ImageFit.COVER,0f,cache,1.5f,SamplingMode.LINEAR,false);
            assertEquals(saved,dest.getCanvas().getSaveCount());assertEquals(1800,cache.pixels(),"40x20 displayed units become 60x30 pixels");
            try(var result=dest.makeImageSnapshot()){bitmap.allocN32Pixels(40,40);assertTrue(result.readPixels(bitmap));
                assertEquals(0xffffffff,bitmap.getColor(5,15));assertEquals(0xffff0000,bitmap.getColor(12,20));assertEquals(0xff0000ff,bitmap.getColor(27,20));}
        }
    }
    @Test void oversizedPhysicalDestinationStillChecksBorrowedCacheLifecycle() {
        try(var host=AtomLibRuntime.open("cache-lifetime");var store=new ImageStore(host);var cache=new ImageDownsampler(10000,4,2);
            var src=Surface.makeRasterN32Premul(64,64);var dest=Surface.makeRasterN32Premul(4,4)) {
            src.getCanvas().clear(0xffff0000);assertTrue(store.acceptDecoded("image",store.generation(),src.makeImageSnapshot()));
            cache.close();
            assertThrows(IllegalStateException.class,()->store.draw("image",dest.getCanvas(),new Rect(0,0,2_000_000_000f,2_000_000_000f),ImageFit.CONTAIN,0,cache,2,SamplingMode.LINEAR,false));
        }
    }
    @Test void encodedInputStillDrawsWhenItsPixelsCannotBeBorrowedForPreprocessing() {
        try(var host=AtomLibRuntime.open("encoded-fallback");var store=new ImageStore(host);var cache=new ImageDownsampler(4096,4,2);
            var source=Surface.makeRasterN32Premul(64,64);var target=Surface.makeRasterN32Premul(20,20);var bitmap=new Bitmap()) {
            source.getCanvas().clear(0xff234567);
            try(var image=source.makeImageSnapshot();var encoded=image.encodeToData()){store.put("encoded",encoded.getBytes());}
            store.draw("encoded",target.getCanvas(),new Rect(0,0,10,10),ImageFit.CONTAIN,0,cache,1,SamplingMode.LINEAR,false);
            try(var result=target.makeImageSnapshot()) {bitmap.allocN32Pixels(20,20);assertTrue(result.readPixels(bitmap));assertEquals(0xff234567,bitmap.getColor(5,5));}
            // Skija's encoded-image laziness is platform-dependent. Either a valid
            // derivative exists or the original is borrowed; the rendered pixels are authoritative.
            assertTrue(cache.size()<=1);assertTrue(cache.pixels()<=100);
        }
    }
}
