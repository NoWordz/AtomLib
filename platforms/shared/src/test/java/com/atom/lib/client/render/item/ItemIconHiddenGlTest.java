package com.atom.lib.client.render.item;
import com.atom.lib.*;
import com.atom.lib.client.render.*;
import com.atom.lib.geometry.Rect;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;
/** Pure GL patterns only. These tests never create Minecraft or render ItemStack. */
class ItemIconHiddenGlTest extends HiddenGlFixture {
    private static void pattern(List<ItemIconAtlas.Tile> tiles,int w,int h){
        for(var tile:tiles){if(tile.empty())continue;glEnable(GL_SCISSOR_TEST);int x=tile.x(),y=h-tile.y()-tile.size(),s=tile.size();
            glScissor(x,y+s/2,s,s-s/2);glClearColor(1,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
            glScissor(x,y,s,s/2);glClearColor(0,0,1,1);glClear(GL_COLOR_BUFFER_BIT);
            glScissor(x,y,1,s);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
            glScissor(x+1,y,1,s);glClearColor(.5f,0,0,.5f);glClear(GL_COLOR_BUFFER_BIT);
        }
    }
    @Test void orientationTransparentEdgeAndExpiryAreRealGpuSamples()throws Exception{
        var target=framebuffer(32,32);
        try(var runtime=AtomLibRuntime.open("item-gl");var bridge=new SkijaRenderBridge(runtime);var atlas=new ItemIconAtlas(bridge)){
            try(var frame=atlas.prepare(List.of(new ItemIconAtlas.Entry("pattern",16,false)),1,ItemIconHiddenGlTest::pattern)){
                var icon=frame.icon("pattern");bridge.draw(target,c->{c.clear(0xff00ff00);icon.paint(c,new Rect(0,0,16,16),1);});
                assertArrayEquals(new int[]{255,0,0,255},pixel(target,8,28),"top is red");assertArrayEquals(new int[]{0,0,255,255},pixel(target,8,19),"bottom is blue");assertArrayEquals(new int[]{0,255,0,255},pixel(target,0,24),"transparent edge exposes green");
                var semi=pixel(target,1,24);assertTrue(Math.abs(semi[0]-128)<=1&&Math.abs(semi[1]-127)<=1&&semi[2]==0,"premultiplied translucent edge");
                var dir=java.nio.file.Path.of(System.getProperty("atomlib.itemIconEvidence"));Files.createDirectories(dir);
                var png=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<32;y++)for(int x=0;x<32;x++){var rgba=pixel(target,x,y);png.setRGB(x,31-y,rgba[3]<<24|rgba[0]<<16|rgba[1]<<8|rgba[2]);}
                javax.imageio.ImageIO.write(png,"png",dir.resolve("pipeline-pattern-not-minecraft.png").toFile());
                frame.close();assertThrows(IllegalStateException.class,()->bridge.draw(target,c->icon.paint(c,new Rect(0,0,16,16),1)));
            }
        }
    }
    @Test void capacityReusesAndInvalidationReleasesStorageAndBorrow(){
        var target=framebuffer(16,16);
        try(var runtime=AtomLibRuntime.open("item-reuse");var bridge=new SkijaRenderBridge(runtime);var atlas=new ItemIconAtlas(bridge)){
            var requests=List.of(new ItemIconAtlas.Entry("a",16,false));
            try(var f=atlas.prepare(requests,1,ItemIconHiddenGlTest::pattern)){assertThrows(IllegalStateException.class,()->atlas.prepare(requests,2,ItemIconHiddenGlTest::pattern));bridge.draw(target,c->f.icon("a").paint(c,new Rect(0,0,16,16),1));assertArrayEquals(new int[]{255,0,0,255},pixel(target,8,12));}
            int texture=atlas.textureId();assertEquals(1,atlas.allocationCount());
            try(var f=atlas.prepare(requests,2,(tiles,w,h)->{glDisable(GL_SCISSOR_TEST);glClearColor(0,1,0,1);glClear(GL_COLOR_BUFFER_BIT);})){bridge.draw(target,c->f.icon("a").paint(c,new Rect(0,0,16,16),1));assertArrayEquals(new int[]{0,255,0,255},pixel(target,8,8),"same storage samples freshly drawn contents");}assertEquals(texture,atlas.textureId());assertEquals(1,atlas.allocationCount());
            try(var f=atlas.prepare(List.of(new ItemIconAtlas.Entry("b",100,false)),3,ItemIconHiddenGlTest::pattern)){}assertEquals(2,atlas.allocationCount());assertFalse(glIsTexture(texture));
            var f=atlas.prepare(requests,4,ItemIconHiddenGlTest::pattern);var icon=f.icon("a");texture=atlas.textureId();int depth=atlas.depthId(),fbo=atlas.framebufferId();atlas.invalidate();assertFalse(glIsTexture(texture));assertFalse(glIsRenderbuffer(depth));assertFalse(glIsFramebuffer(fbo));assertThrows(IllegalStateException.class,()->icon.paint(null,new Rect(0,0,1,1),1));f.close();
        }
    }
    @Test void failureRestoresFramebufferAndPollutedPixelTransfersAndCanRetry(){
        var target=framebuffer(16,16);int pack=glGenBuffers(),unpack=glGenBuffers();
        try(var runtime=AtomLibRuntime.open("item-failure");var bridge=new SkijaRenderBridge(runtime);var atlas=new ItemIconAtlas(bridge)){
            glBindFramebuffer(GL_FRAMEBUFFER,target.framebuffer());glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);glBufferData(GL_PIXEL_PACK_BUFFER,4096,GL_STREAM_READ);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack);glBufferData(GL_PIXEL_UNPACK_BUFFER,4096,GL_STREAM_DRAW);glPixelStorei(GL_UNPACK_ROW_LENGTH,31);glPixelStorei(GL_UNPACK_SKIP_PIXELS,3);
            var request=List.of(new ItemIconAtlas.Entry("a",16,false));assertThrows(IllegalStateException.class,()->atlas.prepare(request,1,(t,w,h)->{assertEquals(0,glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING),"native pass sees no host PBO");assertEquals(0,glGetInteger(GL_UNPACK_ROW_LENGTH));glViewport(1,2,3,4);throw new IllegalStateException("native failed");}));
            assertEquals(target.framebuffer(),glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));assertEquals(pack,glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING));assertEquals(unpack,glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));assertEquals(31,glGetInteger(GL_UNPACK_ROW_LENGTH));assertEquals(3,glGetInteger(GL_UNPACK_SKIP_PIXELS));
            try(var f=atlas.prepare(request,1,ItemIconHiddenGlTest::pattern)){}
            assertThrows(IllegalArgumentException.class,()->atlas.prepare(List.of(request.get(0),request.get(0)),2,ItemIconHiddenGlTest::pattern));assertEquals(GL_NO_ERROR,glGetError());
        }finally{glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);glDeleteBuffers(pack);glDeleteBuffers(unpack);}
    }
    @Test void nestedSkijaPrepareIsRejectedAndEmptyStackIsTransparent(){
        var target=framebuffer(16,16);try(var runtime=AtomLibRuntime.open("item-empty");var bridge=new SkijaRenderBridge(runtime);var atlas=new ItemIconAtlas(bridge)){
            assertThrows(IllegalStateException.class,()->bridge.draw(target,c->atlas.prepare(List.of(),1,(t,w,h)->{})));
            try(var f=atlas.prepare(List.of(new ItemIconAtlas.Entry("empty",16,true)),1,(t,w,h)->fail("empty draws no native pass"))){bridge.draw(target,c->{c.clear(0xff00ff00);f.icon("empty").paint(c,new Rect(0,0,16,16),1);});assertArrayEquals(new int[]{0,255,0,255},pixel(target,8,8));}
        }
    }

    @Test void recordsPureGlColdAndWarmCostsWithoutClaimingMinecraftPerformance()throws Exception{
        try(var runtime=AtomLibRuntime.open("item-timing");var bridge=new SkijaRenderBridge(runtime);var atlas=new ItemIconAtlas(bridge)){
            var entries=new ArrayList<ItemIconAtlas.Entry>();for(int i=0;i<16;i++)entries.add(new ItemIconAtlas.Entry("pattern/"+i,32,false));
            var cpu=new ArrayList<Long>();var gpu=new ArrayList<Long>();int query=glGenQueries();long cold;
            try{
                for(int i=0;i<33;i++){long start=System.nanoTime();try(var frame=atlas.prepare(entries,i+1,(tiles,w,h)->{glBeginQuery(GL_TIME_ELAPSED,query);try{pattern(tiles,w,h);}finally{glEndQuery(GL_TIME_ELAPSED);}})){}long elapsed=System.nanoTime()-start;long nanos=glGetQueryObjectui64(query,GL_QUERY_RESULT);cpu.add(elapsed);gpu.add(nanos);}
                cold=cpu.get(0);var warm=new ArrayList<>(cpu.subList(1,cpu.size()));Collections.sort(warm);
                var dir=java.nio.file.Path.of(System.getProperty("atomlib.itemIconEvidence"));Files.createDirectories(dir);
                Files.writeString(dir.resolve("pure-gl-timing.json"),"{\"scope\":\"16 synthetic GL patterns; NOT Minecraft item rendering\",\"renderer\":\""+glGetString(GL_RENDERER)+"\",\"vendor\":\""+glGetString(GL_VENDOR)+"\",\"version\":\""+glGetString(GL_VERSION)+"\",\"warmSamples\":32,\"coldCpuNanos\":"+cold+",\"warmCpuMedianNanos\":"+warm.get(16)+",\"warmCpuP95Nanos\":"+warm.get(30)+",\"cpuPrepareAndCloseNanos\":"+cpu+",\"gpuClearPatternOnlyNanos\":"+gpu+",\"allocations\":"+atlas.allocationCount()+",\"minecraftRun\":false}");
            }finally{glDeleteQueries(query);}
        }
    }

    @Test void depthIsOwnedAndHandlesRejectWrongThreadsAndContexts()throws Exception{
        var host=framebuffer(16,16);glBindFramebuffer(GL_FRAMEBUFFER,host.framebuffer());glDepthMask(true);glClearDepth(.25);glClear(GL_DEPTH_BUFFER_BIT);
        try(var runtime=AtomLibRuntime.open("item-context");var bridge=new SkijaRenderBridge(runtime);var atlas=new ItemIconAtlas(bridge)){
            try(var frame=atlas.prepare(List.of(new ItemIconAtlas.Entry("a",16,false)),1,ItemIconHiddenGlTest::pattern)){
                glBindFramebuffer(GL_READ_FRAMEBUFFER,host.framebuffer());var value=org.lwjgl.BufferUtils.createFloatBuffer(1);glReadPixels(8,8,1,1,GL_DEPTH_COMPONENT,GL_FLOAT,value);assertEquals(.25f,value.get(0),.0001f,"host depth preserved");
                glBindFramebuffer(GL_READ_FRAMEBUFFER,atlas.framebufferId());glReadPixels(8,8,1,1,GL_DEPTH_COMPONENT,GL_FLOAT,value);assertEquals(1,value.get(0),.0001f,"owned depth cleared");
                glBindFramebuffer(GL_READ_FRAMEBUFFER,host.framebuffer());
                var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var icon=frame.icon("a");var thread=new Thread(()->{try{icon.paint(null,new Rect(0,0,1,1),1);}catch(Throwable e){failure.set(e);}});thread.start();thread.join();assertInstanceOf(IllegalStateException.class,failure.get());
                long original=org.lwjgl.glfw.GLFW.glfwGetCurrentContext();var caps=org.lwjgl.opengl.GL.getCapabilities();long other=org.lwjgl.glfw.GLFW.glfwCreateWindow(16,16,"item context rejection",0,0);assertNotEquals(0,other);
                try{org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(other);org.lwjgl.opengl.GL.createCapabilities();assertThrows(IllegalStateException.class,()->icon.paint(null,new Rect(0,0,1,1),1));}
                finally{org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(original);org.lwjgl.opengl.GL.setCapabilities(caps);org.lwjgl.glfw.GLFW.glfwDestroyWindow(other);}
            }
        }
    }
}
