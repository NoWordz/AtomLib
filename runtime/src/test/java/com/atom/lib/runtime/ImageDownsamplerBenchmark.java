// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.image.ImageDownsampler;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import java.nio.file.Files;
import java.util.*;

/** Opt-in CPU raster measurement; values are samples, not acceptance thresholds. */
public final class ImageDownsamplerBenchmark {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("output.csv samples");
        int samples=Integer.parseInt(args[1]);if(samples<1)throw new IllegalArgumentException("samples");
        var csv=new StringBuilder("mode,sample,nanoseconds,retained_pixels,derived\n");
        try(var sourceSurface=Surface.makeRasterN32Premul(1024,1024);var paint=new Paint()) {
            var canvas=sourceSurface.getCanvas();canvas.clear(0xff252535);
            for(int y=0;y<1024;y+=16){paint.setColor(0xff000000|((y/4)<<16)|((255-y/4)<<8)|0x80);canvas.drawRect(Rect.makeXYWH(0,y,1024,16),paint);}
            try(var source=sourceSurface.makeImageSnapshot();var cache=new ImageDownsampler(300000,4,2)) {
                for(String mode:List.of("cold","hot","pixel_art")) {
                    if(mode.equals("hot"))cache.select(source,128,128,false);
                    for(int i=0;i<samples;i++) {
                        if(mode.equals("cold"))cache.clear();
                        long start=System.nanoTime();var result=cache.select(source,128,128,mode.equals("pixel_art"));long elapsed=System.nanoTime()-start;
                        if(result.isClosed())throw new IllegalStateException("Returned closed image");
                        csv.append(mode).append(',').append(i).append(',').append(elapsed).append(',').append(cache.pixels()).append(',').append(result!=source).append('\n');
                    }
                }
            }
        }
        Files.writeString(java.nio.file.Path.of(args[0]),csv);
    }
}
