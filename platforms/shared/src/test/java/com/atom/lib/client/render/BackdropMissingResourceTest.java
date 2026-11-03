// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.geometry.Rect;
import java.io.IOException;
import java.net.URL;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

class BackdropMissingResourceTest extends HiddenGlFixture {
    @Test void missingShaderIsReportedAndDoesNotTouchDestination() throws Exception {
        ClassLoader parent=getClass().getClassLoader();
        ClassLoader isolated=new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(!name.startsWith("com.atom.lib.client.render.backdrop."))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){
                    Class<?> loaded=findLoadedClass(name);
                    if(loaded==null){
                        String path=name.replace('.','/')+".class";
                        try(var input=parent.getResourceAsStream(path)){
                            if(input==null)throw new ClassNotFoundException(name);
                            byte[] bytes=input.readAllBytes();loaded=defineClass(name,bytes,0,bytes.length);
                        }catch(IOException e){throw new ClassNotFoundException(name,e);}
                    }
                    if(resolve)resolveClass(loaded);return loaded;
                }
            }
            @Override public URL getResource(String name){
                if(name.equals("assets/atomlib/shaders/backdrop/composite.frag"))return null;
                return super.getResource(name);
            }
        };
        var rendererType=isolated.loadClass("com.atom.lib.client.render.backdrop.BackdropRenderer");
        var styleType=isolated.loadClass("com.atom.lib.client.render.backdrop.BackdropStyle");
        var source=framebuffer(64,64);var destination=framebuffer(64,64);
        glBindFramebuffer(GL_FRAMEBUFFER,destination.framebuffer());glClearColor(.1f,.2f,.3f,.25f);glClear(GL_COLOR_BUFFER_BIT);
        int[] before=pixel(destination,32,32);
        var renderer=rendererType.getConstructor().newInstance();
        try {
            var style=styleType.getConstructor(float.class,float.class,float.class,int.class).newInstance(4f,0f,1f,0);
            var result=rendererType.getMethod("render",FramebufferTarget.class,FramebufferTarget.class,Rect.class,styleType,long.class,float.class)
                    .invoke(renderer,source,destination,new Rect(0,0,64,64),style,1L,.5f);
            assertEquals(false,result.getClass().getMethod("drawn").invoke(result));
            assertTrue(((String)result.getClass().getMethod("diagnostic").invoke(result)).contains("Missing shader resource"));
            assertArrayEquals(before,pixel(destination,32,32));
        } finally { rendererType.getMethod("close").invoke(renderer); }
    }
}
