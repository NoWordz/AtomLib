// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.client.render.backdrop.*;
import com.atom.lib.geometry.Rect;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

/** Isolated instrumentation: CPU submit and nonblocking whole-draw GPU queries, not game timings. */
final class BackdropTimingGlTest extends HiddenGlFixture {
    private record Sample(int round,int sample,String mode,long stateCapture,long stateRestore,long capture,long draw,int query,long allocations) { }
    private static BackdropStyle timingStyle(int blur) { return new BackdropStyle(blur,4,1,0); }

    @Test void timingModesActuallyChangeBlurredPixels() {
        var src=framebuffer(64,64);var dst=framebuffer(64,64);var region=new Rect(0,0,64,64);
        glBindFramebuffer(GL_FRAMEBUFFER,src.framebuffer());
        glDisable(GL_SCISSOR_TEST);glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);glScissor(16,16,32,32);glClearColor(1,0,0,1);glClear(GL_COLOR_BUFFER_BIT);glDisable(GL_SCISSOR_TEST);
        try(var renderer=new BackdropRenderer()) {
            var sharp=renderer.capture(src,region,1);assertTrue(renderer.draw(sharp,dst,region,timingStyle(0)).drawn());
            int[] sharpPixel=pixel(dst,16,32);
            var blurred=renderer.capture(src,region,2);assertTrue(renderer.draw(blurred,dst,region,timingStyle(12)).drawn());
            int[] blurredPixel=pixel(dst,16,32);
            assertFalse(Arrays.equals(sharpPixel,blurredPixel),"Timing modes must differ in real filtered pixels");
        }
    }
    @Test void recordBackdropStagesAndNonblockingQueries() throws Exception {
        var src=framebuffer(128,128);var dst=framebuffer(128,128);var region=new Rect(0,0,128,128);
        glBindFramebuffer(GL_FRAMEBUFFER,src.framebuffer());glClearColor(.2f,.4f,.6f,1);glClear(GL_COLOR_BUFFER_BIT);
        var rows=new ArrayList<Sample>();var closeTimes=new ArrayList<Long>();long frame=0;
        for(int round=0;round<3;round++) {
            var queries=new ArrayList<Integer>();
            try(var renderer=new BackdropRenderer()) {
                for(int i=-8;i<16;i++)for(int blur:new int[]{0,12}) {
                    long t=System.nanoTime();var scope=GlStateScope.capture();long captureState=System.nanoTime()-t;
                    t=System.nanoTime();scope.close();long restoreState=System.nanoTime()-t;
                    t=System.nanoTime();var snapshot=renderer.capture(src,region,++frame);long capture=System.nanoTime()-t;
                    int query=glGenQueries();queries.add(query);glBeginQuery(GL_TIME_ELAPSED,query);
                    t=System.nanoTime();var outcome=renderer.draw(snapshot,dst,region,timingStyle(blur));long draw=System.nanoTime()-t;
                    glEndQuery(GL_TIME_ELAPSED);assertTrue(outcome.drawn(),outcome.diagnostic());
                    if(i>=0)rows.add(new Sample(round,i,blur==0?"no_filter":"four_filter_passes",captureState,restoreState,capture,draw,query,renderer.allocationCount()));
                }
                long start=System.nanoTime();renderer.close();closeTimes.add(System.nanoTime()-start);
            }
            // Query each recorded draw at most once, with no glFinish or blocking result request.
            var output=Path.of(System.getProperty("atomlib.backdropEvidence"));Files.createDirectories(output);
            var csv=new StringBuilder("round,sample,mode,state_capture_ns,state_restore_ns,capture_ns,draw_ns,gpu_draw_ns,allocations\n");
            for(var s:rows){boolean ready=glGetQueryObjecti(s.query(),GL_QUERY_RESULT_AVAILABLE)!=0;long gpu=ready?glGetQueryObjectui64(s.query(),GL_QUERY_RESULT):-1;
                csv.append(s.round()).append(',').append(s.sample()).append(',').append(s.mode()).append(',').append(s.stateCapture()).append(',').append(s.stateRestore()).append(',').append(s.capture()).append(',').append(s.draw()).append(',').append(gpu).append(',').append(s.allocations()).append('\n');}
            Files.writeString(output.resolve("backdrop-stage-samples.csv"),csv);
            // Prevent later rounds from querying deleted handles; each round appends its own results.
            if(round<2){Files.writeString(output.resolve("backdrop-stage-round-"+round+".csv"),csv);rows.clear();}
            for(int query:queries)glDeleteQueries(query);
            assertArrayEquals(new int[]{51,102,153,0},pixel(dst,64,64));
        }
        var output=Path.of(System.getProperty("atomlib.backdropEvidence"));
        Files.move(output.resolve("backdrop-stage-samples.csv"),output.resolve("backdrop-stage-round-2.csv"),StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(output.resolve("backdrop-stage-metadata.txt"),"vendor="+glGetString(GL_VENDOR)+"\nrenderer="+glGetString(GL_RENDERER)+"\nversion="+glGetString(GL_VERSION)+"\nregion=128x128\nwarmup=8 per mode\nsamples=16 per mode per round\norder=no_filter then four_filter_passes\nclose_ns="+closeTimes+"\n");
    }
}
