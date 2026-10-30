// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.RuntimeHandle;
import io.github.humbleui.skija.BackendTexture;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.GLTextureInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

final class SkijaRenderBridgeTest extends HiddenGlFixture {
    @Test void borrowedFrameSharesOneContextAndExpiresAfterCallbackWithoutTransferringOwnership() {
        FramebufferTarget target=framebuffer(16,16);
        try(RuntimeHandle runtime=AtomLibRuntime.open("gl-borrow");SkijaRenderBridge bridge=new SkijaRenderBridge(runtime)) {
            bridge.prepare(target);
            var leasedContext=bridge.withContext(context -> context);
            assertNotNull(leasedContext);
            var firstContext=bridge.withFrameAccess(target,frame -> frame.context());
            assertSame(leasedContext,firstContext);
            BorrowedFrame[] escaped=new BorrowedFrame[1];boolean[] painted={false};
            bridge.drawFrame(target,(canvas,frame) -> {
                painted[0]=true;escaped[0]=frame;
                assertSame(firstContext,frame.context());assertNotNull(frame.backendRenderTarget());
                assertSame(canvas,frame.surface().getCanvas());
                assertThrows(IllegalStateException.class,() -> bridge.prepare(target));
                assertThrows(IllegalStateException.class,() -> bridge.withFrameAccess(target,f -> null));
                assertThrows(IllegalStateException.class,() -> bridge.withContext(context -> context));
                assertThrows(IllegalStateException.class,bridge::invalidate);
                CompletableFuture.runAsync(() -> assertThrows(IllegalStateException.class,frame::context)).join();
                canvas.clear(0xff123456);
            });
            assertTrue(painted[0]);
            assertThrows(IllegalStateException.class,escaped[0]::context);
            assertThrows(IllegalStateException.class,escaped[0]::backendRenderTarget);
            assertThrows(IllegalStateException.class,escaped[0]::surface);
            assertFalse(firstContext.isClosed());
            assertArrayEquals(new int[]{18,52,86,255},pixel(target,4,4));
        }
    }

    @Test void captureReturnsAnOwnedImageAndFrameAccessExceptionsExpireTheBorrow() {
        FramebufferTarget target=framebuffer(16,16);
        try(RuntimeHandle runtime=AtomLibRuntime.open("gl-capture");SkijaRenderBridge bridge=new SkijaRenderBridge(runtime)) {
            bridge.draw(target,c -> c.clear(0xff102030));
            Image snapshot=bridge.captureFrame(target);
            assertNotNull(snapshot);
            try(snapshot) {
                BorrowedFrame[] escaped=new BorrowedFrame[1];
                assertThrows(IllegalArgumentException.class,() -> bridge.withFrameAccess(target,frame -> {
                    escaped[0]=frame;throw new IllegalArgumentException("borrow callback failed");
                }));
                assertThrows(IllegalStateException.class,escaped[0]::surface);
                bridge.draw(target,c -> { c.clear(0xff000000);c.drawImage(snapshot,0,0); });
                assertArrayEquals(new int[]{16,32,48,255},pixel(target,4,4));
            }
            bridge.draw(target,c -> c.clear(0xff405060));
            assertArrayEquals(new int[]{64,80,96,255},pixel(target,4,4));
        }
        assertEquals(GL_NO_ERROR,glGetError());
    }

    @Test void realImageUploadIsProtectedFromHostPboAndPixelStoreSettings() {
        FramebufferTarget target=framebuffer(16,16);
        int pack=buffer(),unpack=buffer();
        try(RuntimeHandle runtime=AtomLibRuntime.open("gl-upload");Bitmap bitmap=new Bitmap();SkijaRenderBridge bridge=new SkijaRenderBridge(runtime)) {
            assertTrue(bitmap.allocN32Pixels(2,2));bitmap.erase(0xffff0000);
            try(Image image=Image.makeRasterFromBitmap(bitmap)) {
                glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);glBufferData(GL_PIXEL_PACK_BUFFER,4096,GL_STREAM_READ);
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack);glBufferData(GL_PIXEL_UNPACK_BUFFER,4096,GL_STREAM_DRAW);
                glPixelStorei(GL_PACK_ROW_LENGTH,31);glPixelStorei(GL_UNPACK_ROW_LENGTH,37);
                glPixelStorei(GL_UNPACK_SKIP_ROWS,5);glPixelStorei(GL_UNPACK_ALIGNMENT,8);
                bridge.draw(target,c -> c.drawImageRect(image,Rect.makeXYWH(0,0,16,16)));
                assertEquals(pack,glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING));assertEquals(unpack,glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));
                assertEquals(31,glGetInteger(GL_PACK_ROW_LENGTH));assertEquals(37,glGetInteger(GL_UNPACK_ROW_LENGTH));
                assertEquals(5,glGetInteger(GL_UNPACK_SKIP_ROWS));assertEquals(8,glGetInteger(GL_UNPACK_ALIGNMENT));
                assertArrayEquals(new int[]{255,0,0,255},pixel(target,8,8));
            }
        }
        assertTrue(glIsFramebuffer(target.framebuffer()));assertTrue(glIsTexture(target.colorTexture()));
        assertEquals(GL_NO_ERROR,glGetError());
    }

    /**
     * The consumer shape AC uses for item icons: render into a host FBO outside
     * any Skija pass with dirty PACK/UNPACK/PBO state, then import the finished
     * texture inside one borrowed context callback. The borrowed phase must
     * normalize pixel transfer and restore exactly what the host left behind.
     */
    @Test void hostOffscreenTextureImportsInsideTheBorrowAfterAPollutedVanillaPass() {
        FramebufferTarget target=framebuffer(16,16);
        FramebufferTarget icon=framebuffer(16,16);
        int pack=buffer(),unpack=buffer();
        try(RuntimeHandle runtime=AtomLibRuntime.open("gl-host-import");SkijaRenderBridge bridge=new SkijaRenderBridge(runtime)) {
            bridge.prepare(target);
            glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);glBufferData(GL_PIXEL_PACK_BUFFER,4096,GL_STREAM_READ);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack);glBufferData(GL_PIXEL_UNPACK_BUFFER,4096,GL_STREAM_DRAW);
            glPixelStorei(GL_UNPACK_ROW_LENGTH,37);glPixelStorei(GL_UNPACK_SKIP_ROWS,5);glPixelStorei(GL_UNPACK_ALIGNMENT,8);
            bridge.vanillaPass(() -> {
                glBindFramebuffer(GL_FRAMEBUFFER,icon.framebuffer());
                glViewport(0,0,16,16);glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);
                glClearColor(1f,0f,0f,1f);glClear(GL_COLOR_BUFFER_BIT);
            });
            Image imported=bridge.withContext(context -> {
                assertEquals(0,glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING),"the borrow normalizes PACK PBO");
                assertEquals(0,glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING),"the borrow normalizes UNPACK PBO");
                assertEquals(0,glGetInteger(GL_UNPACK_ROW_LENGTH));
                try(BackendTexture backend=BackendTexture.makeGL(16,16,false,
                        new GLTextureInfo(GL_TEXTURE_2D,icon.colorTexture(),GL_RGBA8))) {
                    return Image.borrowTextureFrom(context,backend,SurfaceOrigin.BOTTOM_LEFT,
                            ColorType.RGBA_8888,ColorAlphaType.PREMUL,null,() -> {});
                }
            });
            // Checked before any readback helper, which binds its own PACK PBO.
            assertEquals(pack,glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING));
            assertEquals(unpack,glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(37,glGetInteger(GL_UNPACK_ROW_LENGTH));
            assertEquals(5,glGetInteger(GL_UNPACK_SKIP_ROWS));
            assertEquals(8,glGetInteger(GL_UNPACK_ALIGNMENT));
            try(imported) {
                bridge.draw(target,c -> c.drawImageRect(imported,Rect.makeXYWH(0,0,16,16)));
                assertArrayEquals(new int[]{255,0,0,255},pixel(target,8,8));
            }
            bridge.draw(target,c -> c.clear(0xff00ff00));
            assertArrayEquals(new int[]{0,255,0,255},pixel(target,6,6));
        }
        assertEquals(GL_NO_ERROR,glGetError());
    }

    @Test void aFailedBorrowImportExpiresAndRestoresTheHostPixelState() {
        FramebufferTarget target=framebuffer(16,16);
        int unpack=buffer();
        try(RuntimeHandle runtime=AtomLibRuntime.open("gl-host-import-failure");SkijaRenderBridge bridge=new SkijaRenderBridge(runtime)) {
            bridge.prepare(target);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack);glBufferData(GL_PIXEL_UNPACK_BUFFER,4096,GL_STREAM_DRAW);
            glPixelStorei(GL_UNPACK_ALIGNMENT,8);
            assertThrows(IllegalStateException.class,() -> bridge.withContext(context -> {
                assertEquals(1,glGetInteger(GL_UNPACK_ALIGNMENT),"the borrow normalizes UNPACK alignment");
                throw new IllegalStateException("import failed");
            }));
            assertEquals(unpack,glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(8,glGetInteger(GL_UNPACK_ALIGNMENT));
            bridge.draw(target,c -> c.clear(0xff0000ff));
            assertArrayEquals(new int[]{0,0,255,255},pixel(target,4,4));
        }
        assertEquals(GL_NO_ERROR,glGetError());
    }

    @Test void noStencilFramebufferWorksAndInvalidAttachmentDoesNotPoisonTheNextPass() {
        FramebufferTarget target=framebuffer(16,16);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_STENCIL_ATTACHMENT,GL_RENDERBUFFER,0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));
        try(RuntimeHandle runtime=AtomLibRuntime.open("gl-no-stencil");SkijaRenderBridge bridge=new SkijaRenderBridge(runtime)) {
            FramebufferTarget bad=new FramebufferTarget(target.identity(),target.framebuffer(),target.colorTexture(),17,16);
            assertThrows(IllegalArgumentException.class,() -> bridge.draw(bad,c -> {}));
            bridge.draw(target,c -> c.clear(0xff112233));
            assertArrayEquals(new int[]{17,34,51,255},pixel(target,10,10));
            bridge.invalidate();bridge.draw(target,c -> c.clear(0xff445566));
            assertArrayEquals(new int[]{68,85,102,255},pixel(target,10,10));
        }
        assertEquals(GL_NO_ERROR,glGetError());
    }

    @Test void skijaVanillaSkijaPaintsRealPixelsAndRebuildsForSameSizeNewFboAndResize() {
        FramebufferTarget first=framebuffer(32,32),second=framebuffer(32,32),large=framebuffer(48,24);
        try(RuntimeHandle runtime=AtomLibRuntime.open("gl-roundtrip");SkijaRenderBridge bridge=new SkijaRenderBridge(runtime)) {
            bridge.draw(first,c -> c.clear(0xffff0000));
            assertArrayEquals(new int[]{255,0,0,255},pixel(first,4,4));
            bridge.vanillaPass(() -> {
                glBindFramebuffer(GL_FRAMEBUFFER,first.framebuffer());glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);
                glClearColor(0,1,0,1);glClear(GL_COLOR_BUFFER_BIT);
            });
            bridge.draw(first,c -> { try(Paint p=new Paint().setColor(0xff0000ff)) { c.drawRect(Rect.makeXYWH(0,0,8,8),p); } });
            assertArrayEquals(new int[]{0,0,255,255},pixel(first,4,27));
            assertArrayEquals(new int[]{0,255,0,255},pixel(first,20,20));
            bridge.draw(second,c -> c.clear(0xffffff00));
            assertArrayEquals(new int[]{255,255,0,255},pixel(second,4,4));
            assertArrayEquals(new int[]{0,255,0,255},pixel(first,20,20));
            bridge.draw(large,c -> c.clear(0xffff00ff));
            assertArrayEquals(new int[]{255,0,255,255},pixel(large,47,23));
        }
        assertEquals(GL_NO_ERROR,glGetError());
    }

    @Test void exceptionRestoresStateAndRejectsNestedDrawVanillaAndCloseInsidePass() {
        FramebufferTarget target=framebuffer(32,32);
        int vao=vao(),buffer=buffer();
        glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buffer);glViewport(2,3,19,20);
        glEnable(GL_SCISSOR_TEST);glScissor(1,2,3,4);
        try(RuntimeHandle runtime=AtomLibRuntime.open("gl-failure");SkijaRenderBridge bridge=new SkijaRenderBridge(runtime)) {
            IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,() -> bridge.draw(target,c -> {
                assertThrows(IllegalStateException.class,() -> bridge.draw(target,nested -> nested.clear(0xffffffff)));
                assertThrows(IllegalStateException.class,() -> bridge.vanillaPass(() -> fail("must not run")));
                assertThrows(IllegalStateException.class,bridge::close);
                c.save();c.translate(100,100);
                throw new IllegalArgumentException("callback failure");
            }));
            assertEquals("callback failure",failure.getMessage());
            assertArrayEquals(new int[]{2,3,19,20},GlStateScopeTest.ints(GL_VIEWPORT,4));
            assertTrue(glIsEnabled(GL_SCISSOR_TEST));assertEquals(vao,glGetInteger(GL_VERTEX_ARRAY_BINDING));assertEquals(buffer,glGetInteger(GL_ARRAY_BUFFER_BINDING));
            bridge.draw(target,c -> c.clear(0xff00ffff));
            assertArrayEquals(new int[]{0,255,255,255},pixel(target,4,4));
        }
        assertEquals(GL_NO_ERROR,glGetError());
    }

    @Test void bridgeHasOneContextOwnerRejectsWrongThreadAndClosesExplicitly() {
        FramebufferTarget target=framebuffer(16,16);
        try(RuntimeHandle runtime=AtomLibRuntime.open("gl-owner")) {
            SkijaRenderBridge first=new SkijaRenderBridge(runtime),second=new SkijaRenderBridge(runtime);
            try {
                first.draw(target,c -> c.clear(0xff112233));
                assertThrows(IllegalStateException.class,() -> second.draw(target,c -> {}));
                CompletableFuture.runAsync(() -> assertThrows(IllegalStateException.class,() -> first.draw(target,c -> {}))).join();
                first.close();first.close();
                assertThrows(IllegalStateException.class,() -> first.draw(target,c -> {}));
                second.draw(target,c -> c.clear(0xff334455));
                assertArrayEquals(new int[]{51,68,85,255},pixel(target,2,2));
            } finally { second.close();first.close(); }
        }
    }
}
