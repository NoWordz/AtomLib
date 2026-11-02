// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render.backdrop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.lwjgl.opengl.GL33C.*;
/** Owns independently compiled programs; packaged resources are mandatory. */
final class BackdropPrograms implements AutoCloseable {
    int filter,composite,vao;
    static final class Unavailable extends RuntimeException {Unavailable(String message){super(message);}Unavailable(String message,Throwable cause){super(message,cause);}}
    void prepare(){if(filter!=0)return;try{filter=program("kawase.frag");composite=program("composite.frag");vao=glGenVertexArrays();}catch(RuntimeException failure){close();throw failure;}}
    private String source(String name){String path="/assets/atomlib/shaders/backdrop/"+name;try(var stream=BackdropPrograms.class.getResourceAsStream(path)){if(stream==null)throw new Unavailable("Missing shader resource: "+path);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}catch(IOException e){throw new Unavailable("Cannot read shader: "+path,e);}}
    private int shader(int type,String resource){String source=source(resource);int shader=glCreateShader(type);glShaderSource(shader,source);glCompileShader(shader);if(glGetShaderi(shader,GL_COMPILE_STATUS)!=GL_TRUE){String log=glGetShaderInfoLog(shader);glDeleteShader(shader);throw new Unavailable("Shader compile failed "+resource+": "+log);}return shader;}
    private int program(String fragment){int vertex=0,pixel=0,result=0;try{vertex=shader(GL_VERTEX_SHADER,"fullscreen.vert");pixel=shader(GL_FRAGMENT_SHADER,fragment);result=glCreateProgram();glAttachShader(result,vertex);glAttachShader(result,pixel);glLinkProgram(result);if(glGetProgrami(result,GL_LINK_STATUS)!=GL_TRUE)throw new Unavailable("Shader link failed: "+glGetProgramInfoLog(result));int linked=result;result=0;return linked;}finally{if(result!=0)glDeleteProgram(result);if(vertex!=0)glDeleteShader(vertex);if(pixel!=0)glDeleteShader(pixel);}}
    void use(int program,int texture){glUseProgram(program);glBindVertexArray(vao);glActiveTexture(GL_TEXTURE0);glBindSampler(0,0);glBindTexture(GL_TEXTURE_2D,texture);glUniform1i(glGetUniformLocation(program,"sourceImage"),0);}
    public void close(){if(filter!=0)glDeleteProgram(filter);if(composite!=0)glDeleteProgram(composite);if(vao!=0)glDeleteVertexArrays(vao);filter=composite=vao=0;}
}
