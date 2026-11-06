// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.AtomLibRuntime;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

final class SkiaScopePerformanceTest extends HiddenGlFixture {
    @TempDir Path directory;

    @Test void onlyDrawFrameUsesFragmentTextureSnapshotAndKeepsJfr() throws Exception {
        var target=framebuffer(24,24);
        int combined=glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        int fragment=glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
        try(var runtime=AtomLibRuntime.open();var bridge=new SkijaRenderBridge(runtime)) {
            Path file=directory.resolve("capture.jfr");
            try(var recording=new Recording()) {
                recording.enable("com.atomlib.GlStateCapture");recording.start();
                bridge.drawFrame(target,(canvas,frame)->canvas.clear(0xff2367ab));
                bridge.withContext(context->null);
                try(var scope=GlStateScope.capture()) { }
                recording.stop();recording.dump(file);
            }
            var events=RecordingFile.readAllEvents(file).stream()
                .filter(e->e.getEventType().getName().equals("com.atomlib.GlStateCapture"))
                .sorted(java.util.Comparator.comparingLong(e->e.getLong("startedAtNanos"))).toList();
            assertEquals(3,events.size());
            assertEquals(Math.min(32,fragment),events.get(0).getInt("textureUnits"),"Pinned Skia caps fragment samplers at 32");
            assertTrue(events.get(0).getInt("textureTargets")<=3,"Skia binds 2D/rectangle/external targets");
            for(int i=1;i<3;i++) {
                assertEquals(combined,events.get(i).getInt("textureUnits"));
                assertEquals(10,events.get(i).getInt("textureTargets"));
            }
            assertArrayEquals(new int[]{0x23,0x67,0xab,255},pixel(target,5,5));
            assertFalse(SkijaRenderBridge.fastState());
        }
    }

    @Test void coldRebuildAndThrowPreserveHighestHostTextureAndLowScratchUnit() {
        var target=framebuffer(24,24);
        int high=glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)-1;
        int scratch=glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)-1;
        glActiveTexture(GL_TEXTURE0+scratch);int lowTexture=texture(GL_TEXTURE_2D);
        glActiveTexture(GL_TEXTURE0+high);int highTexture=texture(GL_TEXTURE_2D);
        try(var runtime=AtomLibRuntime.open();var bridge=new SkijaRenderBridge(runtime)) {
            assertThrows(IllegalArgumentException.class,()->bridge.drawFrame(target,(canvas,frame)->{
                canvas.clear(0xff2468ac);throw new IllegalArgumentException("paint");
            }));
            assertEquals(GL_TEXTURE0+high,glGetInteger(GL_ACTIVE_TEXTURE));
            assertEquals(highTexture,glGetInteger(GL_TEXTURE_BINDING_2D));
            glActiveTexture(GL_TEXTURE0+scratch);assertEquals(lowTexture,glGetInteger(GL_TEXTURE_BINDING_2D));
            assertArrayEquals(new int[]{0x24,0x68,0xac,255},pixel(target,5,5));
            assertEquals(GL_NO_ERROR,glGetError());
        }
    }

    @Test void fullAndSkiaRestoreCubeArraySamplerAndHighestUnitOnSuccessAndThrow() {
        var target=framebuffer(24,24);
        int high=glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)-1;
        int scratch=glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)-1;
        glActiveTexture(GL_TEXTURE0+scratch);int cube=texture(GL_TEXTURE_CUBE_MAP),array=texture(GL_TEXTURE_2D_ARRAY),low=texture(GL_TEXTURE_2D),sampler=sampler();glBindSampler(scratch,sampler);
        glActiveTexture(GL_TEXTURE0+high);int highCube=texture(GL_TEXTURE_CUBE_MAP),highArray=texture(GL_TEXTURE_2D_ARRAY),high2d=texture(GL_TEXTURE_2D),highSampler=sampler();glBindSampler(high,highSampler);
        try(var runtime=AtomLibRuntime.open();var bridge=new SkijaRenderBridge(runtime)) {
            for(boolean full:new boolean[]{true,false})for(boolean fail:new boolean[]{false,true}) {
                bridge.invalidate();
                BorrowedFramePainter painter=(canvas,frame)->{canvas.clear(0xff2367ab);if(fail)throw new IllegalArgumentException("paint");};
                if(fail)assertThrows(IllegalArgumentException.class,()->bridge.drawFrame(target,painter,full));else bridge.drawFrame(target,painter,full);
                assertEquals(GL_TEXTURE0+high,glGetInteger(GL_ACTIVE_TEXTURE));
                assertEquals(highCube,glGetInteger(GL_TEXTURE_BINDING_CUBE_MAP));assertEquals(highArray,glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY));assertEquals(high2d,glGetInteger(GL_TEXTURE_BINDING_2D));assertEquals(highSampler,glGetIntegeri(GL_SAMPLER_BINDING,high));
                glActiveTexture(GL_TEXTURE0+scratch);
                assertEquals(cube,glGetInteger(GL_TEXTURE_BINDING_CUBE_MAP));assertEquals(array,glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY));assertEquals(low,glGetInteger(GL_TEXTURE_BINDING_2D));assertEquals(sampler,glGetIntegeri(GL_SAMPLER_BINDING,scratch));
                glActiveTexture(GL_TEXTURE0+high);assertEquals(GL_NO_ERROR,glGetError());
            }
        }
    }

    @Test void alternatingIdenticalDrawFullVersusSkiaTimingAndPixels() throws Exception {
        var target=framebuffer(64,64);
        var csv=new StringBuilder("round,order,mode,sample,nanos\n");
        try(var runtime=AtomLibRuntime.open();var bridge=new SkijaRenderBridge(runtime);
            var raster=Surface.makeRasterN32Premul(16,16);var paint=new Paint().setAntiAlias(true).setColor(0xa0f05a20)) {
            raster.getCanvas().clear(0xff6496c8);
            try(var image=raster.makeImageSnapshot()) {
                BorrowedFramePainter scene=(canvas,frame)->{
                    canvas.clear(0xff132537);canvas.save();
                    try {
                        canvas.clipRRect(RRect.makeXYWH(3,4,54,52,11),true);
                        canvas.drawImageRect(image,io.github.humbleui.types.Rect.makeXYWH(2,5,47,45));
                        canvas.drawCircle(38,32,19,paint);
                    } finally { canvas.restore(); }
                };
                for(int i=0;i<60;i++)bridge.drawFrame(target,scene,i%2==0);
                bridge.drawFrame(target,scene,true);byte[] expected=readFrame(target);
                bridge.drawFrame(target,scene,false);assertArrayEquals(expected,readFrame(target),"Identical image/clip/AA scene");
                for(int round=0;round<3;round++)for(int order=0;order<2;order++) {
                    boolean full=(round+order)%2==0;
                    for(int sample=0;sample<80;sample++) {
                        glFinish();long start=System.nanoTime();bridge.drawFrame(target,scene,full);glFinish();
                        csv.append(round).append(',').append(order).append(',').append(full?"FULL":"SKIA").append(',').append(sample).append(',').append(System.nanoTime()-start).append('\n');
                    }
                }
            }
        }
        Path root=Path.of(System.getProperty("atomlib.backdropEvidence"));Files.createDirectories(root);
        Files.writeString(root.resolve("scope-full-skia.csv"),csv);
    }

    private byte[] readFrame(FramebufferTarget target) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER,target.framebuffer());glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glPixelStorei(GL_PACK_ALIGNMENT,1);
        var data=org.lwjgl.BufferUtils.createByteBuffer(target.width()*target.height()*4);
        glReadPixels(0,0,target.width(),target.height(),GL_RGBA,GL_UNSIGNED_BYTE,data);
        byte[] bytes=new byte[data.remaining()];data.get(bytes);assertEquals(GL_NO_ERROR,glGetError());return bytes;
    }
}
