// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

final class GlStateScopeTest extends HiddenGlFixture {
    @Test void independentDrawBufferBlendAndColorMasksSurviveGlobalChanges() {
        framebuffer(16,16);
        glEnablei(GL_BLEND,0);glDisablei(GL_BLEND,1);
        glColorMaski(0,true,false,true,false);glColorMaski(1,false,true,false,true);
        var caps=org.lwjgl.opengl.GL.getCapabilities();
        boolean indexedFunctions=caps.OpenGL40 || caps.GL_ARB_draw_buffers_blend;
        glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
        if(indexedFunctions) {
            if(caps.OpenGL40) org.lwjgl.opengl.GL40C.glBlendFuncSeparatei(1,GL_ZERO,GL_ONE,GL_DST_ALPHA,GL_ONE_MINUS_DST_ALPHA);
            else org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(1,GL_ZERO,GL_ONE,GL_DST_ALPHA,GL_ONE_MINUS_DST_ALPHA);
        }
        try(GlStateScope scope=GlStateScope.capture()) {
            glDisable(GL_BLEND);glColorMask(true,true,true,true);
        }
        assertTrue(glIsEnabledi(GL_BLEND,0));assertFalse(glIsEnabledi(GL_BLEND,1));
        int[] first=new int[4],second=new int[4];
        glGetIntegeri_v(GL_COLOR_WRITEMASK,0,first);glGetIntegeri_v(GL_COLOR_WRITEMASK,1,second);
        assertArrayEquals(new int[]{1,0,1,0},first);assertArrayEquals(new int[]{0,1,0,1},second);
        if(indexedFunctions) {
            assertEquals(GL_SRC_ALPHA,glGetIntegeri(GL_BLEND_SRC_RGB,0));
            assertEquals(GL_ZERO,glGetIntegeri(GL_BLEND_SRC_RGB,1));
            assertEquals(GL_DST_ALPHA,glGetIntegeri(GL_BLEND_SRC_ALPHA,1));
        }
        assertEquals(GL_NO_ERROR,glGetError());
    }

    @Test void exceptionRestoresDriverStateIncludingPixelBuffersAndHighTextureUnit() {
        FramebufferTarget draw=framebuffer(32,32),read=framebuffer(16,16);
        int originalVao=vao(),array=buffer(),element=buffer(),pack=buffer(),unpack=buffer(),prog=program();
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw.framebuffer());glBindFramebuffer(GL_READ_FRAMEBUFFER,read.framebuffer());
        glViewport(3,4,23,24);glScissor(5,6,17,18);glEnable(GL_SCISSOR_TEST);
        glEnable(GL_BLEND);glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
        glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT,GL_FUNC_ADD);glBlendColor(.1f,.2f,.3f,.4f);
        glEnable(GL_DEPTH_TEST);glDepthFunc(GL_GREATER);glDepthMask(false);glDepthRange(.2,.8);
        glEnable(GL_CULL_FACE);glCullFace(GL_FRONT);glFrontFace(GL_CW);glColorMask(false,true,false,true);
        glEnable(GL_STENCIL_TEST);glStencilFuncSeparate(GL_FRONT,GL_NOTEQUAL,3,0x3f);glStencilMaskSeparate(GL_FRONT,0x2f);
        glStencilOpSeparate(GL_FRONT,GL_REPLACE,GL_INCR,GL_DECR);
        glStencilFuncSeparate(GL_BACK,GL_LESS,7,0x7f);glStencilMaskSeparate(GL_BACK,0x5f);
        glStencilOpSeparate(GL_BACK,GL_INVERT,GL_KEEP,GL_ZERO);
        glUseProgram(prog);glBindVertexArray(originalVao);glBindBuffer(GL_ARRAY_BUFFER,array);glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,element);
        glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);glBufferData(GL_PIXEL_PACK_BUFFER,4096,GL_STREAM_READ);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack);glBufferData(GL_PIXEL_UNPACK_BUFFER,4096,GL_STREAM_DRAW);
        glPixelStorei(GL_PACK_ALIGNMENT,8);glPixelStorei(GL_PACK_ROW_LENGTH,31);glPixelStorei(GL_PACK_SKIP_PIXELS,2);glPixelStorei(GL_PACK_SKIP_ROWS,3);
        glPixelStorei(GL_UNPACK_ALIGNMENT,2);glPixelStorei(GL_UNPACK_ROW_LENGTH,37);glPixelStorei(GL_UNPACK_SKIP_PIXELS,4);glPixelStorei(GL_UNPACK_SKIP_ROWS,5);
        int high=glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)-1;
        glActiveTexture(GL_TEXTURE0+high);int tex=texture(GL_TEXTURE_2D),cube=texture(GL_TEXTURE_CUBE_MAP),sampler=sampler();glBindSampler(high,sampler);
        assertThrows(IllegalArgumentException.class,() -> {
            try(GlStateScope scope=GlStateScope.capture()) {
                glBindFramebuffer(GL_FRAMEBUFFER,0);glViewport(0,0,1,1);glScissor(0,0,1,1);
                glDisable(GL_SCISSOR_TEST);glDisable(GL_BLEND);glBlendFuncSeparate(GL_ZERO,GL_ONE,GL_ZERO,GL_ONE);glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_SUBTRACT);glBlendColor(0,0,0,0);
                glDisable(GL_DEPTH_TEST);glDepthFunc(GL_LESS);glDepthMask(true);glDepthRange(0,1);
                glDisable(GL_CULL_FACE);glCullFace(GL_BACK);glFrontFace(GL_CCW);glColorMask(true,true,true,true);
                glDisable(GL_STENCIL_TEST);glStencilMask(0xff);glStencilFunc(GL_ALWAYS,0,0xff);glStencilOp(GL_KEEP,GL_KEEP,GL_KEEP);
                glUseProgram(0);glBindVertexArray(vao());glBindBuffer(GL_ARRAY_BUFFER,0);glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,0);
                glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
                glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_PACK_ROW_LENGTH,0);glPixelStorei(GL_PACK_SKIP_PIXELS,0);glPixelStorei(GL_PACK_SKIP_ROWS,0);
                glPixelStorei(GL_UNPACK_ALIGNMENT,1);glPixelStorei(GL_UNPACK_ROW_LENGTH,0);glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);glPixelStorei(GL_UNPACK_SKIP_ROWS,0);
                glBindTexture(GL_TEXTURE_2D,0);glBindTexture(GL_TEXTURE_CUBE_MAP,0);glBindSampler(high,0);glActiveTexture(GL_TEXTURE0);
                throw new IllegalArgumentException("painter failed");
            }
        });
        assertAll(
            () -> assertEquals(draw.framebuffer(),glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)),
            () -> assertEquals(read.framebuffer(),glGetInteger(GL_READ_FRAMEBUFFER_BINDING)),
            () -> assertArrayEquals(new int[]{3,4,23,24},ints(GL_VIEWPORT,4)),
            () -> assertArrayEquals(new int[]{5,6,17,18},ints(GL_SCISSOR_BOX,4)),
            () -> assertTrue(glIsEnabled(GL_SCISSOR_TEST)), () -> assertTrue(glIsEnabled(GL_BLEND)),
            () -> assertEquals(GL_SRC_ALPHA,glGetInteger(GL_BLEND_SRC_RGB)), () -> assertEquals(GL_ONE_MINUS_SRC_ALPHA,glGetInteger(GL_BLEND_DST_RGB)),
            () -> assertEquals(GL_ONE,glGetInteger(GL_BLEND_SRC_ALPHA)), () -> assertEquals(GL_ZERO,glGetInteger(GL_BLEND_DST_ALPHA)),
            () -> assertEquals(GL_FUNC_REVERSE_SUBTRACT,glGetInteger(GL_BLEND_EQUATION_RGB)), () -> assertEquals(GL_FUNC_ADD,glGetInteger(GL_BLEND_EQUATION_ALPHA)),
            () -> assertTrue(glIsEnabled(GL_DEPTH_TEST)), () -> assertFalse(glGetBoolean(GL_DEPTH_WRITEMASK)), () -> assertEquals(GL_GREATER,glGetInteger(GL_DEPTH_FUNC)),
            () -> assertTrue(glIsEnabled(GL_CULL_FACE)), () -> assertEquals(GL_FRONT,glGetInteger(GL_CULL_FACE_MODE)), () -> assertEquals(GL_CW,glGetInteger(GL_FRONT_FACE)),
            () -> assertTrue(glIsEnabled(GL_STENCIL_TEST)), () -> assertEquals(0x2f,glGetInteger(GL_STENCIL_WRITEMASK)), () -> assertEquals(0x5f,glGetInteger(GL_STENCIL_BACK_WRITEMASK)),
            () -> assertEquals(GL_NOTEQUAL,glGetInteger(GL_STENCIL_FUNC)), () -> assertEquals(3,glGetInteger(GL_STENCIL_REF)), () -> assertEquals(GL_LESS,glGetInteger(GL_STENCIL_BACK_FUNC)),
            () -> assertEquals(GL_INVERT,glGetInteger(GL_STENCIL_BACK_FAIL)), () -> assertEquals(GL_DECR,glGetInteger(GL_STENCIL_PASS_DEPTH_PASS)),
            () -> assertEquals(prog,glGetInteger(GL_CURRENT_PROGRAM)), () -> assertEquals(originalVao,glGetInteger(GL_VERTEX_ARRAY_BINDING)),
            () -> assertEquals(array,glGetInteger(GL_ARRAY_BUFFER_BINDING)), () -> assertEquals(element,glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING)),
            () -> assertEquals(pack,glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING)), () -> assertEquals(unpack,glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING)),
            () -> assertEquals(8,glGetInteger(GL_PACK_ALIGNMENT)), () -> assertEquals(31,glGetInteger(GL_PACK_ROW_LENGTH)), () -> assertEquals(2,glGetInteger(GL_PACK_SKIP_PIXELS)), () -> assertEquals(3,glGetInteger(GL_PACK_SKIP_ROWS)),
            () -> assertEquals(2,glGetInteger(GL_UNPACK_ALIGNMENT)), () -> assertEquals(37,glGetInteger(GL_UNPACK_ROW_LENGTH)), () -> assertEquals(4,glGetInteger(GL_UNPACK_SKIP_PIXELS)), () -> assertEquals(5,glGetInteger(GL_UNPACK_SKIP_ROWS)),
            () -> assertEquals(GL_TEXTURE0+high,glGetInteger(GL_ACTIVE_TEXTURE)), () -> assertEquals(tex,glGetInteger(GL_TEXTURE_BINDING_2D)), () -> assertEquals(cube,glGetInteger(GL_TEXTURE_BINDING_CUBE_MAP)),
            () -> assertEquals(sampler,glGetIntegeri(GL_SAMPLER_BINDING,high)), () -> assertEquals(GL_NO_ERROR,glGetError())
        );
    }

    @Test void scopesAreIndependentLifoAndCloseIsThreadConfined() {
        glViewport(1,2,30,31);
        GlStateScope outer=GlStateScope.capture();glViewport(3,4,20,21);
        GlStateScope inner=GlStateScope.capture();glViewport(5,6,10,11);
        assertThrows(IllegalStateException.class,outer::close);
        CompletableFuture.runAsync(() -> assertThrows(IllegalStateException.class,inner::close)).join();
        inner.close();assertArrayEquals(new int[]{3,4,20,21},ints(GL_VIEWPORT,4));
        outer.close();outer.close();assertArrayEquals(new int[]{1,2,30,31},ints(GL_VIEWPORT,4));
        assertEquals(GL_NO_ERROR,glGetError());
    }

    @Test void scopeRejectsAnotherRealContextWithoutLosingItsOriginalSnapshot() {
        long first=org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        var caps=org.lwjgl.opengl.GL.getCapabilities();
        glViewport(9,10,11,12);
        GlStateScope scope=GlStateScope.capture();
        long other=org.lwjgl.glfw.GLFW.glfwCreateWindow(16,16,"Second hidden scope context",0,0);
        assertNotEquals(0,other);
        try {
            org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(other);org.lwjgl.opengl.GL.createCapabilities();
            assertThrows(IllegalStateException.class,scope::close);
        } finally {
            org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(first);org.lwjgl.opengl.GL.setCapabilities(caps);
            org.lwjgl.glfw.GLFW.glfwDestroyWindow(other);
            scope.close();
        }
        assertArrayEquals(new int[]{9,10,11,12},ints(GL_VIEWPORT,4));
        assertEquals(GL_NO_ERROR,glGetError());
    }

    static int[] ints(int name,int size) { int[] a=new int[size];glGetIntegerv(name,a);return a; }
}
