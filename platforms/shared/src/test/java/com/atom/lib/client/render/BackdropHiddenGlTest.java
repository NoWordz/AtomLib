// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;
import com.atom.lib.client.render.backdrop.*;
import com.atom.lib.geometry.Rect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.util.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.lwjgl.glfw.GLFW.*;
class BackdropHiddenGlTest extends HiddenGlFixture {
    @Test void minecraftBridgeExposesTheLuminanceLimitForBothTargetModes() throws Exception {
        Class<?> bridge=Class.forName("com.atom.lib.client.render.MinecraftRenderBridge",false,getClass().getClassLoader());
        assertDoesNotThrow(()->bridge.getMethod("drawBackdrop",Rect.class,float.class,BackdropStyle.class,long.class,float.class));
        assertDoesNotThrow(()->bridge.getMethod("drawBackdrop",FramebufferTarget.class,FramebufferTarget.class,Rect.class,BackdropStyle.class,long.class,float.class));
    }
    @Test void luminanceLimitPreservesDarkPixelsAlphaAndUnrelatedTargets() throws Exception {
        var source=framebuffer(64,64);var destination=framebuffer(64,64);var unrelated=framebuffer(64,64);
        var rect=new Rect(8,8,48,48);var style=new BackdropStyle(0,0,1,0);
        var method=assertDoesNotThrow(()->BackdropRenderer.class.getMethod("render",FramebufferTarget.class,FramebufferTarget.class,Rect.class,BackdropStyle.class,long.class,float.class));
        try(var renderer=new BackdropRenderer()){
            fill(destination,0,0,1,.25f);fill(unrelated,0,1,0,.5f);var unrelatedBefore=pixel(unrelated,32,32);fill(source,1,1,1,0);
            assertTrue(((BackdropOutcome)method.invoke(renderer,source,destination,rect,style,1L,.5f)).drawn());
            var bright=pixel(destination,32,32);
            assertEquals(128,bright[0],1);assertEquals(128,bright[1],1);assertEquals(128,bright[2],1);assertEquals(64,bright[3]);
            assertArrayEquals(new int[]{0,0,255,64},pixel(destination,0,0));
            assertArrayEquals(unrelatedBefore,pixel(unrelated,32,32));
            fill(destination,0,0,1,.25f);fill(source,1,1,1,1);
            assertTrue(((BackdropOutcome)method.invoke(renderer,source,destination,rect,style,2L,.5f)).drawn());
            assertArrayEquals(bright,pixel(destination,32,32),"Source alpha must not affect the result");
            fill(source,.1f,.1f,.1f,1);fill(destination,0,0,1,.25f);
            assertTrue(((BackdropOutcome)method.invoke(renderer,source,destination,rect,style,3L,.5f)).drawn());
            var dark=pixel(destination,32,32);assertEquals(26,dark[0],1);assertEquals(26,dark[1],1);assertEquals(26,dark[2],1);assertEquals(64,dark[3]);
            fill(source,1,.2f,.1f,1);fill(destination,0,0,1,.25f);
            assertTrue(renderer.render(source,destination,rect,new BackdropStyle(8,0,1,0),4,.25f).drawn());
            var tinted=pixel(destination,32,32);assertEquals(176,tinted[0],3);assertEquals(35,tinted[1],3);assertEquals(18,tinted[2],3);assertEquals(64,tinted[3]);
            assertThrows(IllegalArgumentException.class,()->renderer.render(source,destination,rect,style,5,Float.NaN));
            assertThrows(IllegalArgumentException.class,()->renderer.render(source,destination,rect,style,5,-.1f));
            assertThrows(IllegalArgumentException.class,()->renderer.render(source,destination,rect,style,5,1.1f));
        }
    }
    void fill(FramebufferTarget t,float r,float g,float b,float a){glBindFramebuffer(GL_FRAMEBUFFER,t.framebuffer());glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);glClearColor(r,g,b,a);glClear(GL_COLOR_BUFFER_BIT);}
    ByteBuffer read(FramebufferTarget t){glBindFramebuffer(GL_READ_FRAMEBUFFER,t.framebuffer());glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_PACK_ROW_LENGTH,0);glPixelStorei(GL_PACK_SKIP_PIXELS,0);glPixelStorei(GL_PACK_SKIP_ROWS,0);var bytes=BufferUtils.createByteBuffer(t.width()*t.height()*4);glReadPixels(0,0,t.width(),t.height(),GL_RGBA,GL_UNSIGNED_BYTE,bytes);assertEquals(GL_NO_ERROR,glGetError());return bytes;}
    void upload(FramebufferTarget t,boolean opaque,boolean sentinel){var bytes=BufferUtils.createByteBuffer(t.width()*t.height()*4);for(int y=0;y<t.height();y++)for(int x=0;x<t.width();x++){boolean light=((x/8+y/8)%2)==0;bytes.put((byte)(sentinel?12:light?255:0)).put((byte)(sentinel?30:light?200:40)).put((byte)(sentinel?70:light?0:255)).put((byte)(sentinel?(x*3+y*5)%256:opaque?255:0));}bytes.flip();glActiveTexture(GL_TEXTURE0);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);glPixelStorei(GL_UNPACK_ALIGNMENT,1);glBindTexture(GL_TEXTURE_2D,t.colorTexture());glTexSubImage2D(GL_TEXTURE_2D,0,0,0,t.width(),t.height(),GL_RGBA,GL_UNSIGNED_BYTE,bytes);}
    @Test void allDestinationAlphaSentinelsAndAllPixelsOutsideRoundedRegionAreUnchanged(){
        var src=framebuffer(64,64);var dst=framebuffer(64,64);var box=new Rect(8,8,48,48);upload(src,false,false);upload(dst,false,true);var before=read(dst);
        try(var renderer=new BackdropRenderer()){
            assertTrue(renderer.render(src,dst,box,new BackdropStyle(10,12,.8f,0),1).drawn());var transparent=read(dst);
            for(int y=0;y<64;y++)for(int x=0;x<64;x++){int at=(y*64+x)*4;assertEquals(before.get(at+3),transparent.get(at+3),"alpha "+x+","+y);if(x<8||x>=56||y<8||y>=56)for(int c=0;c<3;c++)assertEquals(before.get(at+c),transparent.get(at+c));}
            upload(src,true,false);upload(dst,false,true);assertTrue(renderer.render(src,dst,box,new BackdropStyle(10,12,.8f,0),2).drawn());var opaque=read(dst);for(int i=0;i<opaque.capacity();i++)assertEquals(transparent.get(i),opaque.get(i),"Source alpha must not affect any pixel");
        }
    }
    @Test void dirtyStateIsRestoredIncludingLogicOpAndSampler(){
        var src=framebuffer(64,64);var dst=framebuffer(64,64);var third=framebuffer(32,32);fill(src,1,.3f,.1f,0);int sampler=sampler();
        glBindFramebuffer(GL_FRAMEBUFFER,third.framebuffer());glViewport(3,4,11,12);glEnable(GL_SCISSOR_TEST);glScissor(5,6,7,8);glEnable(GL_DEPTH_TEST);glDepthMask(true);glColorMask(false,true,false,true);glEnable(GL_BLEND);glBlendFunc(GL_ONE,GL_ZERO);glEnable(GL_COLOR_LOGIC_OP);glLogicOp(GL_XOR);glActiveTexture(GL_TEXTURE3);glBindSampler(0,sampler);int vao=vao();glBindVertexArray(vao);
        try(var renderer=new BackdropRenderer()){
            assertTrue(renderer.render(src,dst,new Rect(0,0,64,64),new BackdropStyle(3,0,1,0),1).drawn());
            assertTrue(glIsEnabled(GL_COLOR_LOGIC_OP));assertTrue(glIsEnabled(GL_SCISSOR_TEST));assertTrue(glIsEnabled(GL_DEPTH_TEST));assertEquals(third.framebuffer(),glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));assertEquals(third.framebuffer(),glGetInteger(GL_READ_FRAMEBUFFER_BINDING));assertEquals(GL_TEXTURE3,glGetInteger(GL_ACTIVE_TEXTURE));assertEquals(vao,glGetInteger(GL_VERTEX_ARRAY_BINDING));assertEquals(sampler,glGetIntegeri(GL_SAMPLER_BINDING,0));int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);assertArrayEquals(new int[]{3,4,11,12},viewport);int[] mask=new int[4];glGetIntegerv(GL_COLOR_WRITEMASK,mask);assertArrayEquals(new int[]{0,1,0,1},mask);assertEquals(GL_NO_ERROR,glGetError());
        }
    }
    @Test void captureDrawSameTargetHasNoFeedbackAndZeroRadiusIsUnblurredCopy(){
        var src=framebuffer(64,64);upload(src,false,false);var before=read(src);
        try(var renderer=new BackdropRenderer()){var token=renderer.capture(src,new Rect(0,0,64,64),1);assertTrue(renderer.draw(token,src,new Rect(0,0,64,64),new BackdropStyle(0,0,1,0)).drawn());var after=read(src);for(int i=0;i<before.capacity();i++)assertEquals(before.get(i),after.get(i));}
    }
    @Test void resizeReuseReloadUnsupportedTargetAndThreadContextFailures(){
        var src=framebuffer(64,64);var dst=framebuffer(64,64);fill(src,.3f,1,.5f,0);
        try(var renderer=new BackdropRenderer()){
            renderer.render(src,dst,new Rect(0,0,64,64),new BackdropStyle(2,4,1,0),1);long count=renderer.allocationCount();
            fill(src,1,0,0,0);renderer.render(src,dst,new Rect(0,0,64,64),new BackdropStyle(2,4,1,0),2);assertEquals(count,renderer.allocationCount());assertArrayEquals(new int[]{255,0,0,0},pixel(dst,32,32));
            var token=renderer.capture(src,new Rect(0,0,32,32),3);assertTrue(renderer.allocationCount()>count);renderer.invalidate();assertThrows(IllegalStateException.class,()->renderer.draw(token,dst,new Rect(0,0,32,32),new BackdropStyle(0,0,1,0)));
            var invalid=new FramebufferTarget(src.identity(),src.framebuffer(),src.colorTexture(),65,64);var skip=renderer.render(invalid,dst,new Rect(0,0,32,32),new BackdropStyle(0,0,1,0),4);assertEquals(BackdropOutcome.Status.SKIPPED,skip.status());assertFalse(skip.diagnostic().isBlank());
            Throwable[] error={null};var thread=new Thread(()->{try{renderer.capture(src,new Rect(0,0,1,1),5);}catch(Throwable e){error[0]=e;}});thread.start();try{thread.join();}catch(InterruptedException e){throw new AssertionError(e);}assertInstanceOf(IllegalStateException.class,error[0]);
            long current=glfwGetCurrentContext();glfwMakeContextCurrent(0);try{assertThrows(IllegalStateException.class,()->renderer.capture(src,new Rect(0,0,1,1),5));}finally{glfwMakeContextCurrent(current);}
            assertEquals(GL_NO_ERROR,glGetError());
        }
    }
    void save(FramebufferTarget target,Path path,boolean alphaOnly)throws Exception{var data=read(target);var image=new BufferedImage(target.width(),target.height(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<target.height();y++)for(int x=0;x<target.width();x++){int i=(y*target.width()+x)*4,r=data.get(i)&255,g=data.get(i+1)&255,b=data.get(i+2)&255;if(alphaOnly)r=g=b=data.get(i+3)&255;image.setRGB(x,target.height()-1-y,0xff000000|r<<16|g<<8|b);}ImageIO.write(image,"png",path.toFile());}
    @Test void recordsRealShaderSceneAndNonblockingTimingEvidence()throws Exception{
        var src=framebuffer(512,320);var dst=framebuffer(512,320);upload(src,false,false);upload(dst,false,true);var output=Path.of(System.getProperty("atomlib.backdropEvidence","build/backdrop-evidence"));Files.createDirectories(output);save(src,output.resolve("unblurred-rgb.png"),false);
        try(var renderer=new BackdropRenderer()){
            int query=glGenQueries();try{glBeginQuery(GL_TIME_ELAPSED,query);long start=System.nanoTime();var result=renderer.render(src,dst,new Rect(40,30,400,240),new BackdropStyle(12,24,1,0xff16191f),1);long cpu=System.nanoTime()-start;glEndQuery(GL_TIME_ELAPSED);assertTrue(result.drawn());save(dst,output.resolve("blurred-rgb.png"),false);save(dst,output.resolve("alpha-sentinels.png"),true);boolean available=glGetQueryObjecti(query,GL_QUERY_RESULT_AVAILABLE)!=0;String gpu=available?Long.toString(glGetQueryObjectui64(query,GL_QUERY_RESULT)):"null";Files.writeString(output.resolve("timing.json"),"{\"renderer\":\""+glGetString(GL_RENDERER)+"\",\"version\":\""+glGetString(GL_VERSION)+"\",\"size\":[512,320],\"region\":[40,30,400,240],\"passes\":4,\"cpuSubmitNanos\":"+cpu+",\"gpuNanos\":"+gpu+",\"gpuQueryAvailableAfterReadback\":"+available+",\"displayImagesForceOpaqueForViewing\":true,\"gameBenchmark\":false}");}finally{glDeleteQueries(query);}
            renderer.render(src,dst,new Rect(0,0,80,100),new BackdropStyle(18,16,1,0),2);save(dst,output.resolve("edge-rgb.png"),false);
        }
    }
    @Test void explicitDestinationIgnoresSourceAlphaAndPreservesDestinationAlphaAndCorners(){
        var source=framebuffer(64,64);var destination=framebuffer(64,64);var unrelated=framebuffer(64,64);var rect=new Rect(8,8,48,48);
        try(var blur=new BackdropRenderer()){
            fill(source,1,0,0,0);fill(destination,0,0,1,.25f);glBindFramebuffer(GL_FRAMEBUFFER,unrelated.framebuffer());
            assertTrue(blur.render(source,destination,rect,new BackdropStyle(8,12,1,0xff16191f),1).drawn());
            assertArrayEquals(new int[]{255,0,0,64},pixel(destination,32,32));assertArrayEquals(new int[]{0,0,255,64},pixel(destination,8,55));assertArrayEquals(new int[]{0,0,255,64},pixel(destination,0,0));assertArrayEquals(new int[]{0,0,0,0},pixel(unrelated,32,32));
            fill(source,1,0,0,1);assertTrue(blur.render(source,destination,rect,new BackdropStyle(8,12,1,0),2).drawn());assertArrayEquals(new int[]{255,0,0,64},pixel(destination,32,32));
        }
    }
    @Test void snapshotsExpireAndRendererInstancesDoNotShareBuffers(){
        var source=framebuffer(64,64);var destination=framebuffer(64,64);var rect=new Rect(0,0,64,64);
        try(var a=new BackdropRenderer();var b=new BackdropRenderer()){
            fill(source,.2f,.6f,1,0);var first=a.capture(source,rect,1);b.capture(source,new Rect(10,10,20,20),1);
            assertTrue(a.draw(first,destination,rect,new BackdropStyle(0,0,1,0)).drawn());assertArrayEquals(new int[]{51,153,255,0},pixel(destination,30,30));
            a.invalidate();assertThrows(IllegalStateException.class,()->a.draw(first,destination,rect,new BackdropStyle(0,0,1,0)));
            var next=a.capture(source,rect,2);assertThrows(IllegalStateException.class,()->b.draw(next,destination,rect,new BackdropStyle(0,0,1,0)));
            a.close();a.close();assertThrows(IllegalStateException.class,()->a.capture(source,rect,3));
        }
    }
}
