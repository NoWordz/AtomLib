// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.image.ImageDownsampler;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import java.lang.ref.WeakReference;
import static org.junit.jupiter.api.Assertions.*;

class ImageDownsamplerTest {
    private static Image image(int color) {
        try (var s=Surface.makeRasterN32Premul(64,64)) { s.getCanvas().clear(color); return s.makeImageSnapshot(); }
    }
    private static int pixel(Image image,int x,int y) {
        try(var b=new Bitmap()) { b.allocN32Pixels(image.getWidth(),image.getHeight()); assertTrue(image.readPixels(b)); return b.getColor(x,y); }
    }
    @Test void largeReductionCachesLiveImageButSmallAndPixelArtBorrowSource() {
        try(var source=image(0x80ff0000);var cache=new ImageDownsampler(1024,4,2)) {
            assertSame(source,cache.select(source,48,48,false));
            assertSame(source,cache.select(source,8,8,true));
            var scaled=cache.select(source,16,16,false);
            assertNotSame(source,scaled); assertEquals(16,scaled.getWidth()); assertEquals(16,scaled.getHeight());
            assertSame(scaled,cache.select(source,16,16,false)); assertEquals(0x80ff0000,pixel(scaled,8,8));
            assertEquals(256,cache.pixels()); assertEquals(1,cache.size());
        }
    }
    @Test void oversizedBypassesCacheAndEvictionClosesOnlyOwnedImages() {
        try(var a=image(0xffff0000);var b=image(0xff00ff00);var cache=new ImageDownsampler(300,4,2)) {
            assertSame(a,cache.select(a,20,20,false));
            var first=cache.select(a,16,16,false); var second=cache.select(b,16,16,false);
            assertTrue(first.isClosed()); assertFalse(second.isClosed()); assertEquals(0xff00ff00,pixel(second,3,3));
            assertFalse(a.isClosed()); assertFalse(b.isClosed());
            cache.clear(); assertTrue(second.isClosed()); assertEquals(0,cache.pixels());
            assertEquals(0xffff0000,pixel(a,1,1));
        }
    }
    @Test void closedSourceAndReloadReleaseDerivedHandles() {
        var source=image(0xff112233);
        try(var cache=new ImageDownsampler(1024,4,2)) {
            var derived=cache.select(source,8,8,false); source.close();
            assertEquals(0,cache.size()); assertTrue(derived.isClosed());
            try(var next=image(0xffabcdef)) {
                var after=cache.select(next,8,8,false);cache.clear();assertTrue(after.isClosed());
                var fresh=cache.select(next,8,8,false);assertFalse(fresh.isClosed());cache.close();assertTrue(fresh.isClosed());assertFalse(next.isClosed());
            }
        }
    }
    @Test void entryBudgetEvictsLeastRecentlyUsedAndNeverReturnsClosedImage() {
        try(var a=image(0xff010203);var b=image(0xff040506);var c=image(0xff070809);var cache=new ImageDownsampler(10000,2,2)) {
            var x=cache.select(a,8,8,false);var y=cache.select(b,8,8,false);assertSame(x,cache.select(a,8,8,false));
            var z=cache.select(c,8,8,false);assertTrue(y.isClosed());assertFalse(x.isClosed());assertFalse(z.isClosed());
            assertEquals(0xff070809,pixel(z,3,3));
        }
    }
    private static WeakReference<Image> forgottenSource(ImageDownsampler cache,Image[] derivative) {
        var source=image(0xff143c78);derivative[0]=cache.select(source,8,8,false);return new WeakReference<>(source);
    }
    @Test void collectedSourcePurgesDerivedHandleOnMaintenance() throws Exception {
        try(var cache=new ImageDownsampler(1000,4,2)) {
            var derived=new Image[1];var ref=forgottenSource(cache,derived);
            for(int i=0;i<100&&ref.get()!=null;i++){System.gc();Thread.sleep(20);}
            assertNull(ref.get(),"source must not be strongly retained by cache");
            assertEquals(0,cache.size());assertTrue(derived[0].isClosed());
        }
    }
    @Test void strongReductionAveragesFineAlternatingDetailRatherThanAliasingToSolid() {
        try(var surface=Surface.makeRasterN32Premul(64,64);var paint=new Paint();var cache=new ImageDownsampler(4096,4,2)) {
            var canvas=surface.getCanvas();canvas.clear(0xff000000);paint.setColor(0xffffffff);
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)if((x+y)%2==0)
                canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(x,y,1,1),paint);
            try(var source=surface.makeImageSnapshot()) {
                var scaled=cache.select(source,10,10,false);
                for(int y=1;y<9;y++)for(int x=1;x<9;x++) {
                    int c=pixel(scaled,x,y)&255;
                    assertTrue(c>=110&&c<=145,"alternating detail should average near middle gray: "+c);
                }
            }
        }
    }
    @Test void deferredEncodedSourceWithoutAccessiblePixelsIsBorrowedUnchanged() {
        try(var original=image(0xff345678);var data=original.encodeToData();var source=Image.makeDeferredFromEncodedBytes(data.getBytes());
            var cache=new ImageDownsampler(4096,4,2)) {
            assertNull(source.peekPixels(),"fixture must be lazy, without directly accessible raster pixels");
            assertSame(source,cache.select(source,8,8,false));
            assertEquals(0,cache.size());
        }
    }
    @Test void scratchBudgetAndInvalidArgumentsCannotReturnBrokenImages() {
        assertThrows(IllegalArgumentException.class,()->new ImageDownsampler(0,1,2));
        assertThrows(IllegalArgumentException.class,()->new ImageDownsampler(100,1,Float.NaN));
        try(var source=image(0xff102030);var cache=new ImageDownsampler(64,2,2)) {
            assertSame(source,cache.select(source,4,4,false),"first intermediate exceeds four times the pixel budget");
            assertEquals(0xff102030,pixel(source,2,2));assertEquals(0,cache.size());
            assertThrows(IllegalArgumentException.class,()->cache.select(source,0,1,false));
            cache.close();assertThrows(IllegalStateException.class,()->cache.select(source,4,4,false));
        }
    }
    @Test void crossThreadAccessCannotCloseAnImageWhileOwnerDraws() throws Exception {
        try(var source=image(0xff102030);var cache=new ImageDownsampler(1024,2,2)) {
            var derived=cache.select(source,8,8,false);var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var other=new Thread(()->{try{cache.clear();}catch(Throwable failure){error.set(failure);}});
            other.start();other.join();
            assertInstanceOf(IllegalStateException.class,error.get());assertFalse(derived.isClosed());assertEquals(0xff102030,pixel(derived,1,1));
        }
    }
    @Test void transparentEdgeRetainsAlphaAndDoesNotIntroduceBlackFringes() {
        try(var surface=Surface.makeRasterN32Premul(64,64);var paint=new Paint().setColor(0xffff0000);var cache=new ImageDownsampler(4096,2,2)) {
            surface.getCanvas().clear(0);surface.getCanvas().drawRect(io.github.humbleui.types.Rect.makeXYWH(0,0,32,64),paint);
            try(var source=surface.makeImageSnapshot()) {
                var reduced=cache.select(source,10,10,false);
                assertEquals(0xffff0000,pixel(reduced,1,5));assertEquals(0,pixel(reduced,9,5));
                for(int x=0;x<10;x++) {int c=pixel(reduced,x,5),a=c>>>24;if(a>0)assertTrue((c>>16&255)>245,"red must not darken at transparent edge");}
            }
        }
    }
    @Test void sourceLargerThanBudgetIsReturnedUsableWhenTargetAloneExceedsBudget() {
        try(var large=Surface.makeRasterN32Premul(4096,4096);var dest=Surface.makeRasterN32Premul(4,4);
            var cache=new ImageDownsampler(4194304,4,1.5f)) {
            large.getCanvas().clear(0xff123456);
            try(var source=large.makeImageSnapshot()) {
                var result=cache.select(source,2200,2200,false);
                assertSame(source,result);assertFalse(result.isClosed());assertEquals(0,cache.size());
                dest.getCanvas().drawImageRect(result,io.github.humbleui.types.Rect.makeXYWH(0,0,4,4));
                try(var drawn=dest.makeImageSnapshot()){assertEquals(0xff123456,pixel(drawn,2,2));}
            }
        }
    }
    @Test void differentlyShapedTargetsOfTheSameSourceHaveIndependentCacheEntries() {
        try(var source=image(0xff336699);var cache=new ImageDownsampler(4096,4,2)) {
            var landscape=cache.select(source,16,8,false);var portrait=cache.select(source,8,16,false);
            assertNotSame(landscape,portrait);assertEquals(16,landscape.getWidth());assertEquals(8,landscape.getHeight());
            assertEquals(8,portrait.getWidth());assertEquals(16,portrait.getHeight());
            assertSame(landscape,cache.select(source,16,8,false));assertEquals(256,cache.pixels());
            cache.clear();assertTrue(landscape.isClosed());assertTrue(portrait.isClosed());assertFalse(source.isClosed());
        }
    }
}
