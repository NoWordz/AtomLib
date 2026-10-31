// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Each test owns its real hidden core GL context and resources. No skip path. */
abstract class HiddenGlFixture {
    private long window;
    private static GLFWErrorCallback errors;
    final List<Runnable> disposers = new ArrayList<>();

    // GLFW's global init/terminate cycle races on X11 when repeated per test
    // (intermittent glfwInit failure mid-suite), so the library initializes
    // once per class while every test still gets its own window and context.
    @BeforeAll static void initGlfwLibrary() {
        errors = GLFWErrorCallback.createPrint(System.err);
        errors.set();
        assertTrue(glfwInit(), "GLFW initialization failed; this test requires a graphics driver");
    }

    @AfterAll static void terminateGlfwLibrary() {
        glfwTerminate();
        glfwSetErrorCallback(null);
        if (errors != null) errors.free();
    }

    @BeforeEach void openHiddenWindow() {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(64, 64, "AtomLib hidden GL contract", 0, 0);
        assertNotEquals(0, window, "Hidden GLFW context creation failed");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GL vendor="+glGetString(GL_VENDOR)+" renderer="+glGetString(GL_RENDERER)+" version="+glGetString(GL_VERSION));
        assertEquals(GLFW_FALSE, glfwGetWindowAttrib(window, GLFW_VISIBLE));
    }

    @AfterEach void closeHiddenWindow() {
        if (window != 0) {
            for (int i=disposers.size()-1; i>=0; i--) disposers.get(i).run();
            GL.setCapabilities(null);
            glfwMakeContextCurrent(0);
            glfwDestroyWindow(window);
        }
    }

    FramebufferTarget framebuffer(int width, int height) {
        int texture=glGenTextures();
        glBindTexture(GL_TEXTURE_2D,texture);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        int depth=glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER,depth);
        glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH24_STENCIL8,width,height);
        int fbo=glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_STENCIL_ATTACHMENT,GL_RENDERBUFFER,depth);
        assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));
        disposers.add(() -> { glDeleteFramebuffers(fbo); glDeleteRenderbuffers(depth); glDeleteTextures(texture); });
        glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);
        glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
        return new FramebufferTarget(new Object(),fbo,texture,width,height);
    }

    int[] pixel(FramebufferTarget target, int x, int y) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER,target.framebuffer());
        glBindBuffer(GL_PIXEL_PACK_BUFFER,0);
        glPixelStorei(GL_PACK_ALIGNMENT,1);
        glPixelStorei(GL_PACK_ROW_LENGTH,0);
        glPixelStorei(GL_PACK_SKIP_PIXELS,0);
        glPixelStorei(GL_PACK_SKIP_ROWS,0);
        ByteBuffer b=org.lwjgl.BufferUtils.createByteBuffer(4);
        glReadPixels(x,y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,b);
        assertEquals(GL_NO_ERROR,glGetError());
        return new int[]{b.get(0)&255,b.get(1)&255,b.get(2)&255,b.get(3)&255};
    }

    int buffer() {
        int id=glGenBuffers();disposers.add(() -> glDeleteBuffers(id));return id;
    }
    int vao() {
        int id=glGenVertexArrays();disposers.add(() -> glDeleteVertexArrays(id));return id;
    }
    int texture(int target) {
        int id=glGenTextures();glBindTexture(target,id);disposers.add(() -> glDeleteTextures(id));return id;
    }
    int sampler() {
        int id=glGenSamplers();disposers.add(() -> glDeleteSamplers(id));return id;
    }
    int program() {
        int vs=glCreateShader(GL_VERTEX_SHADER),fs=glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(vs,"#version 150\nvoid main(){gl_Position=vec4(0,0,0,1);}");
        glShaderSource(fs,"#version 150\nout vec4 color;void main(){color=vec4(1);}");
        glCompileShader(vs);glCompileShader(fs);
        assertEquals(GL_TRUE,glGetShaderi(vs,GL_COMPILE_STATUS),glGetShaderInfoLog(vs));
        assertEquals(GL_TRUE,glGetShaderi(fs,GL_COMPILE_STATUS),glGetShaderInfoLog(fs));
        int p=glCreateProgram();glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);
        assertEquals(GL_TRUE,glGetProgrami(p,GL_LINK_STATUS),glGetProgramInfoLog(p));
        glDeleteShader(vs);glDeleteShader(fs);disposers.add(() -> glDeleteProgram(p));return p;
    }
}
