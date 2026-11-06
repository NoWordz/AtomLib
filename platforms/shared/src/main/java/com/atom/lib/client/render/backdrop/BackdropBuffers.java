// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render.backdrop;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL33C.*;
/** Three exact-size surfaces; object names survive storage resizing. */
final class BackdropBuffers implements AutoCloseable {
    final int[] textures=new int[3],fbos=new int[3];int width,height;long allocations;
    void ensure(int w,int h){if(width==w&&height==h)return;if(w>glGetInteger(GL_MAX_TEXTURE_SIZE)||h>glGetInteger(GL_MAX_TEXTURE_SIZE))throw new BackdropPrograms.Unavailable("Capture exceeds GL_MAX_TEXTURE_SIZE");
        glActiveTexture(GL_TEXTURE0);glBindSampler(0,0);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
        try{for(int i=0;i<3;i++){if(textures[i]==0)textures[i]=glGenTextures();glBindTexture(GL_TEXTURE_2D,textures[i]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);if(fbos[i]==0)fbos[i]=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbos[i]);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[i],0);glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new BackdropPrograms.Unavailable("Private backdrop framebuffer incomplete");}width=w;height=h;allocations++;}catch(RuntimeException failure){close();throw failure;}
    }
    public void close(){for(int i=0;i<3;i++){if(fbos[i]!=0)glDeleteFramebuffers(fbos[i]);if(textures[i]!=0)glDeleteTextures(textures[i]);fbos[i]=textures[i]=0;}width=height=0;}
}
