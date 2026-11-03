// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render.backdrop;
import com.atom.lib.client.render.*;
import com.atom.lib.geometry.Rect;
import org.lwjgl.opengl.GL;
import java.util.*;
import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.opengl.GL33C.*;
/** Per-instance, explicit-target Raw GL blur; must be called after host batches flush and outside Skija. */
public final class BackdropRenderer implements AutoCloseable {
    private final Thread thread=Thread.currentThread();private final long context=glfwGetCurrentContext();
    private long generation,lastFrame=Long.MIN_VALUE,lastDrawGeneration;private boolean closed;
    private final BackdropBuffers buffers=new BackdropBuffers();private final BackdropPrograms programs=new BackdropPrograms();
    private FramebufferTarget lastDestination;
    public BackdropRenderer(){if(context==0||!GL.getCapabilities().OpenGL33)throw new IllegalStateException("OpenGL 3.3 context required");}
    private void check(){if(Thread.currentThread()!=thread||glfwGetCurrentContext()!=context)throw new IllegalStateException("Backdrop thread/context mismatch");if(closed)throw new IllegalStateException("Backdrop renderer closed");SkijaRenderBridge.assertOutsideSkijaPass();}
    public BackdropSnapshot capture(FramebufferTarget source,Rect paddedRegion,long frameId){
        check();Objects.requireNonNull(source);Objects.requireNonNull(paddedRegion);if(frameId<lastFrame)throw new IllegalArgumentException("Nonmonotonic backdrop frame");lastFrame=frameId;generation++;
        var clamped=paddedRegion.intersect(new Rect(0,0,source.width(),source.height()));
        int x=(int)Math.floor(clamped.x()),y=(int)Math.floor(clamped.y()),right=(int)Math.ceil(clamped.right()),bottom=(int)Math.ceil(clamped.bottom());var region=new Rect(x,y,right-x,bottom-y);
        if(region.width()==0||region.height()==0)return new BackdropSnapshot(this,generation,region,"Empty capture region");
        try(var scope=GlStateScope.capture()){
            String invalid=validate(source);if(invalid!=null)return new BackdropSnapshot(this,generation,region,invalid);
            buffers.ensure(right-x,bottom-y);glDisable(GL_SCISSOR_TEST);glBindFramebuffer(GL_READ_FRAMEBUFFER,source.framebuffer());int readBuffer=glGetInteger(GL_READ_BUFFER);
            try{glReadBuffer(GL_COLOR_ATTACHMENT0);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,buffers.fbos[0]);glBlitFramebuffer(x,source.height()-bottom,right,source.height()-y,0,0,right-x,bottom-y,GL_COLOR_BUFFER_BIT,GL_NEAREST);}finally{glBindFramebuffer(GL_READ_FRAMEBUFFER,source.framebuffer());glReadBuffer(readBuffer);}
            return new BackdropSnapshot(this,generation,region,"");
        }catch(BackdropPrograms.Unavailable unavailable){return new BackdropSnapshot(this,generation,region,unavailable.getMessage());}
    }
    public BackdropOutcome draw(BackdropSnapshot input,FramebufferTarget destination,Rect output,BackdropStyle style){
        return draw(input,destination,output,style,1);
    }
    /** Optional maximum encoded-RGB luminance, preserving hue. One disables the limit. */
    public BackdropOutcome draw(BackdropSnapshot input,FramebufferTarget destination,Rect output,BackdropStyle style,float maxLuminance){
        check();Objects.requireNonNull(input);Objects.requireNonNull(destination);Objects.requireNonNull(output);Objects.requireNonNull(style);
        requireLuminance(maxLuminance);
        if(input.owner!=this||input.generation!=generation)throw new IllegalStateException("Expired or foreign backdrop snapshot");
        if(!input.available())return skipped(input.diagnostic);
        if(output.width()==0||output.height()==0)return skipped("Empty output region");
        if(!output.equals(output.intersect(new Rect(0,0,destination.width(),destination.height())))||!output.equals(output.intersect(input.region)))throw new IllegalArgumentException("Output must fit destination and captured region");
        if(lastDestination!=null&&!same(lastDestination,destination)&&lastDrawGeneration==generation)throw new IllegalStateException("Destination generation changed; recapture first");
        try(var scope=GlStateScope.capture()){
            String invalid=validate(destination);if(invalid!=null)return skipped(invalid);programs.prepare();normalize();
            int texture=buffers.textures[0];
            if(style.blurRadius()>0){for(int pass=0;pass<4;pass++){int slot=1+pass%2;glBindFramebuffer(GL_DRAW_FRAMEBUFFER,buffers.fbos[slot]);glViewport(0,0,buffers.width,buffers.height);glColorMask(true,true,true,true);glDisable(GL_BLEND);programs.use(programs.filter,texture);float step=(style.blurRadius()/8)*(pass+.5f);glUniform2f(glGetUniformLocation(programs.filter,"displacement"),step/buffers.width,step/buffers.height);glDrawArrays(GL_TRIANGLES,0,3);texture=buffers.textures[slot];}}
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,destination.framebuffer());int count=glGetInteger(GL_MAX_DRAW_BUFFERS);int[] prior=new int[count];for(int i=0;i<count;i++)prior[i]=glGetInteger(GL_DRAW_BUFFER0+i);
            try{
                glDrawBuffer(GL_COLOR_ATTACHMENT0);glViewport((int)output.x(),destination.height()-(int)output.bottom(),(int)output.width(),(int)output.height());
                glEnable(GL_BLEND);glBlendEquation(GL_FUNC_ADD);glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
                glColorMask(true,true,true,false);
                programs.use(programs.composite,texture);int p=programs.composite;
                glUniform2f(glGetUniformLocation(p,"origin"),(output.x()-input.region.x())/buffers.width,(input.region.bottom()-output.bottom())/buffers.height);
                glUniform2f(glGetUniformLocation(p,"span"),output.width()/buffers.width,output.height()/buffers.height);
                glUniform2f(glGetUniformLocation(p,"outputSize"),output.width(),output.height());glUniform1f(glGetUniformLocation(p,"cornerRadius"),style.cornerRadius());glUniform1f(glGetUniformLocation(p,"opacity"),style.opacity());glUniform1f(glGetUniformLocation(p,"maxLuminance"),maxLuminance);glDrawArrays(GL_TRIANGLES,0,3);
            }finally{glDrawBuffers(prior);}
            lastDestination=destination;lastDrawGeneration=generation;return new BackdropOutcome(BackdropOutcome.Status.DRAWN,"");
        }catch(BackdropPrograms.Unavailable unavailable){return skipped(unavailable.getMessage());}
    }
    /** Convenience path computes sampling padding; the low-level capture argument is already padded. */
    public BackdropOutcome render(FramebufferTarget source,FramebufferTarget destination,Rect output,BackdropStyle style,long frameId){return render(source,destination,output,style,frameId,1);}
    public BackdropOutcome render(FramebufferTarget source,FramebufferTarget destination,Rect output,BackdropStyle style,long frameId,float maxLuminance){check();Objects.requireNonNull(source);Objects.requireNonNull(style);requireLuminance(maxLuminance);double pad=Math.ceil((double)style.blurRadius()*2+2);float x=(float)Math.max(0,output.x()-pad),y=(float)Math.max(0,output.y()-pad),right=(float)Math.min(source.width(),output.right()+pad),bottom=(float)Math.min(source.height(),output.bottom()+pad);var padded=new Rect(x,y,Math.max(0,right-x),Math.max(0,bottom-y));return draw(capture(source,padded,frameId),destination,output,style,maxLuminance);}
    private static void requireLuminance(float value){if(!Float.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("Invalid maximum luminance");}
    private static BackdropOutcome skipped(String diagnostic){return new BackdropOutcome(BackdropOutcome.Status.SKIPPED,diagnostic);}
    private static boolean same(FramebufferTarget a,FramebufferTarget b){return a.identity()==b.identity()&&a.framebuffer()==b.framebuffer()&&a.colorTexture()==b.colorTexture()&&a.width()==b.width()&&a.height()==b.height();}
    private static String validate(FramebufferTarget target){
        glBindFramebuffer(GL_FRAMEBUFFER,target.framebuffer());if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)return "Host framebuffer incomplete";
        if(glGetInteger(GL_SAMPLES)!=0)return "Multisample framebuffer unsupported";
        if(glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)!=GL_TEXTURE||glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)!=target.colorTexture())return "Framebuffer attachment identity mismatch";
        if(glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE)!=0||glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_FRAMEBUFFER_ATTACHMENT_LAYERED)!=0)return "Layered/cube attachment unsupported";
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,target.colorTexture());if(glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_INTERNAL_FORMAT)!=GL_RGBA8||glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH)!=target.width()||glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT)!=target.height())return "Expected reported-size RGBA8 2D texture";return null;
    }
    private static void normalize(){for(int state:new int[]{GL_DEPTH_TEST,GL_STENCIL_TEST,GL_CULL_FACE,GL_SCISSOR_TEST,GL_RASTERIZER_DISCARD,GL_FRAMEBUFFER_SRGB,GL_DITHER,GL_SAMPLE_ALPHA_TO_COVERAGE,GL_SAMPLE_COVERAGE,GL_SAMPLE_MASK,GL_COLOR_LOGIC_OP})glDisable(state);int n=glGetInteger(GL_MAX_CLIP_DISTANCES);for(int i=0;i<n;i++)glDisable(GL_CLIP_DISTANCE0+i);glDepthMask(false);glPolygonMode(GL_FRONT_AND_BACK,GL_FILL);}
    public void invalidate(){check();try(var scope=GlStateScope.capture()){programs.close();buffers.close();generation++;lastDestination=null;}}
    public long allocationCount(){check();return buffers.allocations;}
    public void close(){if(closed){if(Thread.currentThread()!=thread)throw new IllegalStateException("Backdrop close thread mismatch");return;}check();try(var scope=GlStateScope.capture()){programs.close();buffers.close();closed=true;generation++;}}
}
