package com.atom.lib.client.render;

import com.atom.lib.client.render.backdrop.*;
import com.atom.lib.geometry.Rect;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

/** Checks the shared-service clock boundary and records warm submission cost. */
class BackdropIndependentReviewTest extends HiddenGlFixture {
    @Test void screenLocalCounterCannotRestartTheSharedRendererButClientClockCan() throws Exception {
        var source=framebuffer(64,64);var destination=framebuffer(64,64);
        glBindFramebuffer(GL_FRAMEBUFFER,source.framebuffer());
        glClearColor(0,1,0,0);glClear(GL_COLOR_BUFFER_BIT);
        var region=new Rect(0,0,64,64);var style=new BackdropStyle(8,0,1,0);
        try(var renderer=new BackdropRenderer()) {
            assertTrue(renderer.render(source,destination,region,style,5).drawn());
            assertThrows(IllegalArgumentException.class,
                ()->renderer.render(source,destination,region,style,1),
                "A new Screen's local frame counter cannot restart a bridge-owned capture service");
            long base=Math.max(6,System.nanoTime());
            long[] submit=new long[32];
            for(int i=0;i<submit.length;i++) {
                long start=System.nanoTime();
                assertTrue(renderer.render(source,destination,region,style,base+i).drawn());
                submit[i]=System.nanoTime()-start;
            }
            assertArrayEquals(new int[]{0,255,0,0},pixel(destination,32,32));
            Arrays.sort(submit);
            var dir=Path.of(System.getProperty("atomlib.backdropEvidence","build/backdrop-evidence"));
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("independent-warm-timing.json"),
                "{\"samples\":32,\"size\":[64,64],\"cpuSubmitMedianNanos\":"+submit[16]+
                ",\"cpuSubmitP95Nanos\":"+submit[30]+",\"gameBenchmark\":false}");
        }
    }
}
