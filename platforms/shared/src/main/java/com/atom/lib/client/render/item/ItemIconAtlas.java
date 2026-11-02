package com.atom.lib.client.render.item;
import com.atom.lib.client.render.*;
import io.github.humbleui.skija.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.opengl.GL33C.*;
/** Library-owned offscreen storage. Contents redraw every prepare; capacity alone is reused. */
public final class ItemIconAtlas implements AutoCloseable {
    public record Entry(String key,int pixelSize,boolean empty){public Entry{if(key==null||key.isBlank()||pixelSize<=0)throw new IllegalArgumentException("Invalid item icon request");}}
    public record Tile(String key,int x,int y,int size,boolean empty){}
    @FunctionalInterface public interface Pass {void draw(List<Tile> tiles,int width,int height);}
    private final SkijaRenderBridge bridge;
    private final Thread thread=Thread.currentThread();private final long window=glfwGetCurrentContext();private final GLCapabilities capabilities=GL.getCapabilities();
    private int texture,fbo,depth,width,height,allocations;private long generation=1,lastFrame=Long.MIN_VALUE;private boolean closed;private ItemIconFrame active;
    public ItemIconAtlas(SkijaRenderBridge bridge){this.bridge=Objects.requireNonNull(bridge);check();}
    public static int pixels(float logicalSize,float pixelsPerUnit){if(!Float.isFinite(logicalSize)||!Float.isFinite(pixelsPerUnit)||logicalSize<=0||pixelsPerUnit<=0||logicalSize*pixelsPerUnit>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid icon density");return Math.max(1,(int)Math.ceil(logicalSize*pixelsPerUnit));}
    public int allocationCount(){check();return allocations;}
    long generation(){return generation;}
    int textureId(){return texture;}int framebufferId(){return fbo;}int depthId(){return depth;}
    void check(){if(closed||Thread.currentThread()!=thread||window==0||glfwGetCurrentContext()!=window||GL.getCapabilities()!=capabilities)throw new IllegalStateException("Item icon atlas requires its live render context and thread");SkijaRenderBridge.assertOutsideSkijaPass();}
    // Painting a handle occurs inside a Skija pass; only owner/context validity applies there.
    void checkHandle(){if(closed||Thread.currentThread()!=thread||glfwGetCurrentContext()!=window||GL.getCapabilities()!=capabilities)throw new IllegalStateException("Item icon frame belongs to another or closed context");}
    public ItemIconFrame prepare(List<Entry> requests,long frameId,Pass pass){
        check();Objects.requireNonNull(pass);requests=List.copyOf(requests);if(active!=null)throw new IllegalStateException("Close the previous ItemIconFrame before preparing another");if(frameId<=lastFrame)throw new IllegalArgumentException("Item icon frame IDs must increase");
        var keys=new HashSet<String>();int size=1,count=0;for(var request:requests){if(!keys.add(request.key()))throw new IllegalArgumentException("Duplicate item icon key: "+request.key());if(!request.empty()){size=Math.max(size,request.pixelSize());count++;}}
        int max=glGetInteger(GL_MAX_TEXTURE_SIZE);if(size>max-4)throw new IllegalArgumentException("Item icon exceeds GL_MAX_TEXTURE_SIZE");int cell=size+4,cols=Math.max(1,(int)Math.ceil(Math.sqrt(count))),rows=Math.max(1,(count+cols-1)/cols);
        if((long)cols*cell>max||(long)rows*cell>max)throw new IllegalArgumentException("Visible item icon batch exceeds texture capacity");
        var tiles=new ArrayList<Tile>();int index=0;for(var request:requests){int n=request.empty()?0:index++;tiles.add(new Tile(request.key(),n%cols*cell+2,n/cols*cell+2,request.pixelSize(),request.empty()));}
        // All previous bridge work must be submitted before GL writes storage again.
        bridge.withContext(context->{context.flushAndSubmit(false);return null;});
        Image imported=null;
        try(GlStateScope ignored=GlStateScope.capture()){
            if(count>0){
                ensureCapacity(cols*cell,rows*cell);
                glBindFramebuffer(GL_FRAMEBUFFER,fbo);glViewport(0,0,width,height);glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);glDepthMask(true);glClearColor(0,0,0,0);glClearDepth(1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
                glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
                for(int state:new int[]{GL_PACK_ROW_LENGTH,GL_PACK_IMAGE_HEIGHT,GL_PACK_SKIP_PIXELS,GL_PACK_SKIP_ROWS,GL_PACK_SKIP_IMAGES,GL_PACK_SWAP_BYTES,GL_PACK_LSB_FIRST,GL_UNPACK_ROW_LENGTH,GL_UNPACK_IMAGE_HEIGHT,GL_UNPACK_SKIP_PIXELS,GL_UNPACK_SKIP_ROWS,GL_UNPACK_SKIP_IMAGES,GL_UNPACK_SWAP_BYTES,GL_UNPACK_LSB_FIRST})glPixelStorei(state,0);
                glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_UNPACK_ALIGNMENT,1);
                pass.draw(List.copyOf(tiles),width,height);
                if(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)!=fbo)throw new IllegalStateException("Item renderer changed the owned framebuffer");
                imported=bridge.withContext(context->{context.resetGLAll();try(var backend=BackendTexture.makeGL(width,height,false,new GLTextureInfo(GL_TEXTURE_2D,texture,GL_RGBA8))){return Image.borrowTextureFrom(context,backend,SurfaceOrigin.BOTTOM_LEFT,ColorType.RGBA_8888,ColorAlphaType.PREMUL,null,()->{});}});
            }
            active=new ItemIconFrame(this,generation,imported,tiles);lastFrame=frameId;return active;
        }catch(RuntimeException|Error e){if(imported!=null)imported.close();throw e;}
    }
    private static int capacity(int need){int n=1;while(n<need)n*=2;return n;}
    private void ensureCapacity(int w,int h){if(texture!=0&&width>=w&&height>=h)return;
        int max=glGetInteger(GL_MAX_TEXTURE_SIZE),nw=Math.min(max,capacity(w)),nh=Math.min(max,capacity(h));
        try(GlStateScope ignored=GlStateScope.capture()){
            int nt=0,nf=0,nd=0;
            try{
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);glPixelStorei(GL_UNPACK_ROW_LENGTH,0);glPixelStorei(GL_UNPACK_SKIP_ROWS,0);glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);glPixelStorei(GL_UNPACK_ALIGNMENT,1);
                nt=glGenTextures();glBindTexture(GL_TEXTURE_2D,nt);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,nw,nh,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
                nd=glGenRenderbuffers();glBindRenderbuffer(GL_RENDERBUFFER,nd);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,nw,nh);
                nf=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,nf);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,nt,0);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,nd);glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);
                if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Item icon framebuffer is incomplete");
                drop();texture=nt;fbo=nf;depth=nd;width=nw;height=nh;nt=nf=nd=0;allocations++;generation++;
            }finally{if(nf!=0)glDeleteFramebuffers(nf);if(nd!=0)glDeleteRenderbuffers(nd);if(nt!=0)glDeleteTextures(nt);}
        }
    }
    void release(ItemIconFrame frame){check();if(frame!=active)throw new IllegalStateException("Item icon frame is not active");try{bridge.withContext(context->{try{context.flushAndSubmit(false);}finally{if(frame.image!=null)frame.image.close();}return null;});}finally{frame.closed=true;active=null;}}
    private void drop(){if(fbo!=0)glDeleteFramebuffers(fbo);if(depth!=0)glDeleteRenderbuffers(depth);if(texture!=0)glDeleteTextures(texture);texture=fbo=depth=width=height=0;}
    public void invalidate(){check();try{if(active!=null)release(active);}finally{try(GlStateScope ignored=GlStateScope.capture()){drop();generation++;}}}
    public void close(){if(closed)return;check();try{invalidate();}finally{closed=true;}}
}
