// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render.backdrop;

import com.atom.lib.client.render.HiddenGlFixture;
import com.atom.lib.geometry.Rect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

final class BackdropPerformanceTest extends HiddenGlFixture {
    @Test void resizeKeepsObjectNamesWhileStorageHasExactRequestedDimensions() {
        try(var buffers=new BackdropBuffers()) {
            buffers.ensure(32,24);int[] textures=buffers.textures.clone(),fbos=buffers.fbos.clone();
            // Deleted object names may immediately be recycled by the driver.
            // A non-sampling texture parameter distinguishes reuse from delete/gen.
            for(int texture:textures){glBindTexture(GL_TEXTURE_2D,texture);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_COMPARE_FUNC,GL_GREATER);}
            for(int[] size:new int[][]{{48,35},{19,13},{32,24}}) {
                buffers.ensure(size[0],size[1]);
                assertArrayEquals(textures,buffers.textures,"Resize must reuse owned texture objects");
                assertArrayEquals(fbos,buffers.fbos,"Resize must reuse owned framebuffer objects");
                for(int texture:buffers.textures) {
                    glBindTexture(GL_TEXTURE_2D,texture);
                    assertEquals(GL_GREATER,glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_COMPARE_FUNC),"The original object survives resize");
                    assertEquals(size[0],glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH));
                    assertEquals(size[1],glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT));
                }
            }
        }
    }

    @Test void sameTargetConvenienceRenderValidatesOnceButPublicDrawAlwaysRevalidates() throws Exception {
        var target=framebuffer(48,48);var region=new Rect(8,8,32,32);var style=new BackdropStyle(4,3,1,0);
        var count=BackdropRenderer.class.getDeclaredField("validations");count.setAccessible(true);
        try(var renderer=new BackdropRenderer()) {
            assertTrue(renderer.render(target,target,region,style,1).drawn());
            assertEquals(1,count.getLong(renderer));
            var snapshot=renderer.capture(target,region,2);
            renderer.draw(snapshot,target,region,style);assertEquals(3,count.getLong(renderer));
            glBindFramebuffer(GL_FRAMEBUFFER,target.framebuffer());
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,0,0);
            assertFalse(renderer.draw(snapshot,target,region,style).drawn(),"Public calls must detect external attachment mutation");
            assertEquals(4,count.getLong(renderer));
            assertEquals(GL_NO_ERROR,glGetError());
        }
    }

    @Test void uniformLocationsBelongToLinkedProgramAndAreRebuiltAfterClose() throws Exception {
        var displacement=BackdropPrograms.class.getDeclaredField("displacement");displacement.setAccessible(true);
        try(var programs=new BackdropPrograms()) {
            programs.prepare();int filter=programs.filter;
            assertEquals(glGetUniformLocation(filter,"displacement"),displacement.getInt(programs));
            programs.prepare();assertEquals(filter,programs.filter);
            programs.close();assertEquals(-1,displacement.getInt(programs));
            programs.prepare();assertEquals(glGetUniformLocation(programs.filter,"displacement"),displacement.getInt(programs));
        }
    }
}
