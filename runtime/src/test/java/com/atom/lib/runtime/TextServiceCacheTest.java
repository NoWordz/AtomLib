// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.*;
import com.atom.lib.layout.Constraints;
import com.atom.lib.text.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.impl.Stats;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TextServiceCacheTest {
    private static Constraints unbounded() { return new Constraints(Constraints.Axis.undefined(),Constraints.Axis.undefined()); }
    private static int live(String name) { return Stats.allocated.getOrDefault(name,0); }

    @Test void warmDrawRetainsBoundedShapingAndCloseReleasesNatives() {
        boolean enabled=Stats.enabled;Stats.enabled=true;
        int lines=live("TextLine"),shapers=live("Shaper");
        try(var runtime=AtomLibRuntime.open();var fonts=new FontRegistry(runtime);
            var surface=Surface.makeRasterN32Premul(240,80)) {
            var id=new FontId("cache");fonts.register(id,FontSource.file(FontIsolationTest.fontPath()));
            var text=new TextService(fonts,2,40);
            try {
                var request=new TextRequest("AV中文",id,20,0xff2468ac);
                var layout=text.layout(request,unbounded());
                text.draw(surface.getCanvas(),layout,1,1);
                assertTrue(live("TextLine")>lines,"Warm draw must retain shaped runs instead of reshaping every frame");
                assertEquals(shapers+1,live("Shaper"),"One shaper belongs to this service");
                for(int i=0;i<8;i++)text.layout(new TextRequest("text"+i,id,20,0xff000000),unbounded());
                assertTrue(live("TextLine")<=lines+2,"Native shaping cache must obey entry budget");
                fonts.reload();
                assertThrows(IllegalStateException.class,()->text.draw(surface.getCanvas(),layout,1,1));
                var fresh=text.layout(request,unbounded());
                text.draw(surface.getCanvas(),fresh,1,1);
            } finally { text.close(); }
            assertEquals(lines,live("TextLine"));assertEquals(shapers,live("Shaper"));
            text.close();
        } finally { Stats.enabled=enabled; }
    }

    @Test void drawCacheSharesColorsButSeparatesSizeAndProducesSamePixels() {
        try(var runtime=AtomLibRuntime.open();var fonts=new FontRegistry(runtime);
            var cached=new TextService(fonts);var uncached=new TextService(fonts,1,1);
            var first=Surface.makeRasterN32Premul(240,90);var second=Surface.makeRasterN32Premul(240,90)) {
            var id=new FontId("cache");fonts.register(id,FontSource.file(FontIsolationTest.fontPath()));
            for(float size:new float[]{17,24,17})for(int color:new int[]{0xff2468ac,0x80c02060}) {
                var request=new TextRequest("AV中e\u0301文",id,size,color);
                first.getCanvas().clear(0xffe0e0e0);second.getCanvas().clear(0xffe0e0e0);
                cached.draw(first.getCanvas(),cached.layout(request,unbounded()),2,3);
                uncached.draw(second.getCanvas(),uncached.layout(request,unbounded()),2,3);
                try(var a=first.makeImageSnapshot();var b=second.makeImageSnapshot();var ab=new Bitmap();var bb=new Bitmap()) {
                    ab.allocN32Pixels(240,90);bb.allocN32Pixels(240,90);assertTrue(a.readPixels(ab));assertTrue(b.readPixels(bb));
                    for(int y=0;y<90;y++)for(int x=0;x<240;x++)assertEquals(ab.getColor(x,y),bb.getColor(x,y));
                }
            }
        }
    }

    @Test void warmDrawUsesFewerNativeCallsThanUncachedShaping() {
        boolean enabled=Stats.enabled;Stats.enabled=true;
        try(var runtime=AtomLibRuntime.open();var fonts=new FontRegistry(runtime);
            var cached=new TextService(fonts);var uncached=new TextService(fonts,1,1);
            var surface=Surface.makeRasterN32Premul(240,90)) {
            var id=new FontId("calls");fonts.register(id,FontSource.file(FontIsolationTest.fontPath()));
            var request=new TextRequest("AV中文",id,20,0xff2468ac);
            var a=cached.layout(request,unbounded());var b=uncached.layout(request,unbounded());
            cached.draw(surface.getCanvas(),a,0,0);uncached.draw(surface.getCanvas(),b,0,0);
            long start=Stats.nativeCalls;
            for(int i=0;i<100;i++)cached.draw(surface.getCanvas(),a,0,0);
            long cachedCalls=Stats.nativeCalls-start;start=Stats.nativeCalls;
            for(int i=0;i<100;i++)uncached.draw(surface.getCanvas(),b,0,0);
            long uncachedCalls=Stats.nativeCalls-start;
            System.out.println("TEXT_DRAW_NATIVE_CALLS cached="+cachedCalls+" uncached="+uncachedCalls+" draws=100");
            // Skija 0.143.18: Font construction + 3 FontRegistry setters +
            // Shaper.shapeLine + Font/TextLine finalizers = 7 calls per run.
            assertEquals(7*100,uncachedCalls-cachedCalls,"Every cached run avoids font allocation/configuration, shaping and both finalizers");
        } finally { Stats.enabled=enabled; }
    }
}
