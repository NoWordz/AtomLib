// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.RuntimeHandle;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Contract for the slim Skia-only snapshot.
 *
 * <p>The slim path may omit state families that Ganesh never writes (see
 * {@link SlimSnapshotEvidenceTest}). Everything else — including per-draw-buffer
 * state, which a global {@code glBlendFuncSeparate} demonstrably clobbers — has
 * to come back exactly as the host left it. The pixel A/B is the primary
 * evidence; the call counters exist so a device run can see the real boundary.
 */
final class SlimSnapshotTest extends HiddenGlFixture {

    private static final boolean INITIAL_SLIM = GlStateScope.slimSnapshot();
    private static final boolean INITIAL_VERIFY = GlStateScope.verifySlimEnabled();

    @AfterEach
    void restoreDefaults() {
        GlStateScope.setSlimSnapshot(INITIAL_SLIM);
        GlStateScope.setVerifySlim(INITIAL_VERIFY);
        GlStateScope.setSlimTextureUnits(0);
    }

    @Test
    void slimIsTheDefaultBoundary() {
        Assumptions.assumeTrue(System.getProperty("atomlib.glscope.slim") == null,
            "default only verifiable when no -Datomlib.glscope.slim is supplied");
        assertTrue(INITIAL_SLIM, "the slim boundary must be the default for the Skia-only path");
    }

    @Test
    void slimSnapshotIssuesFewerNativeCallsThanTheFullBoundary() {
        FramebufferTarget target = framebuffer(48, 48);
        int ubo = glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS);
        int clips = glGetInteger(GL_MAX_CLIP_DISTANCES);
        int words = glGetInteger(GL_MAX_SAMPLE_MASK_WORDS);
        int[] full = new int[2];
        int[] slim = new int[2];

        GlStateScope.setSlimSnapshot(false);
        try (RuntimeHandle runtime = AtomLibRuntime.open("slim-count-full");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            bridge.draw(target, c -> c.clear(0xff010203));
            full[0] = GlStateScope.lastCaptureCalls();
            full[1] = GlStateScope.lastRestoreCalls();
        }
        GlStateScope.setSlimSnapshot(true);
        try (RuntimeHandle runtime = AtomLibRuntime.open("slim-count-slim");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            bridge.draw(target, c -> c.clear(0xff010203));
            slim[0] = GlStateScope.lastCaptureCalls();
            slim[1] = GlStateScope.lastRestoreCalls();
        }

        System.out.println("SLIM_COUNTS full_capture=" + full[0] + " full_restore=" + full[1]
            + " slim_capture=" + slim[0] + " slim_restore=" + slim[1]
            + " ubo_bindings=" + ubo + " clip_distances=" + clips + " sample_mask_words=" + words);
        assertTrue(slim[0] < full[0], "slim capture must issue fewer calls");
        assertTrue(slim[1] < full[1], "slim restore must issue fewer calls");
        // The dropped families are the only difference on capture (limit probes
        // are cached after the first call, so they do not show up here).
        assertTrue(full[0] - slim[0] >= ubo * 3 + clips + words,
            "capture must drop at least the UBO/clip/mask queries: " + (full[0] - slim[0]));
        assertTrue(full[1] - slim[1] >= ubo,
            "restore must drop at least the per-UBO binds: " + (full[1] - slim[1]));
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void slimAndFullProduceIdenticalPixelsAcrossAlternatingPasses() {
        FramebufferTarget fullTarget = framebuffer(40, 40);
        FramebufferTarget slimTarget = framebuffer(40, 40);
        byte[] fullPixels;
        byte[] slimPixels;
        GlStateScope.setSlimSnapshot(false);
        try (RuntimeHandle runtime = AtomLibRuntime.open("slim-pixels-full");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            fullPixels = sequence(bridge, fullTarget, true);
        }
        GlStateScope.setSlimSnapshot(true);
        try (RuntimeHandle runtime = AtomLibRuntime.open("slim-pixels-slim");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            slimPixels = sequence(bridge, slimTarget, false);
        }
        assertArrayEquals(fullPixels, slimPixels, "slim must not change a single pixel");
        assertEquals(GL_NO_ERROR, glGetError());
    }

    /** Same alternating Skija/vanilla shape the bridge sees on a real frame. */
    private static byte[] sequence(SkijaRenderBridge bridge, FramebufferTarget target, boolean full) {
        glBindFramebuffer(GL_FRAMEBUFFER, target.framebuffer());
        glViewport(0, 0, 40, 40);
        glDisable(GL_SCISSOR_TEST);
        glColorMask(true, true, true, true);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        for (int round = 0; round < 3; round++) {
            final int r = round;
            bridge.vanillaPass(() -> {
                glBindFramebuffer(GL_FRAMEBUFFER, target.framebuffer());
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_GREATER);
                glEnable(GL_CULL_FACE);
                glCullFace(GL_FRONT);
                glEnable(GL_STENCIL_TEST);
                glStencilFuncSeparate(GL_FRONT, GL_NOTEQUAL, 3 + r, 0x3f);
                glStencilOpSeparate(GL_FRONT, GL_REPLACE, GL_INCR, GL_DECR);
                glEnable(GL_DITHER);
                glDisable(GL_FRAMEBUFFER_SRGB);
            });
            bridge.drawFrame(target, (canvas, frame) -> paint(canvas, r), full);
        }
        return pixels(target, 40, 40);
    }

    private static void paint(Canvas canvas, int round) {
        canvas.clear(0xff102030);
        try (Bitmap bitmap = new Bitmap()) {
            assertTrue(bitmap.allocN32Pixels(8, 8));
            bitmap.erase(0xc0ff8000);
            try (Image image = Image.makeRasterFromBitmap(bitmap)) {
                canvas.drawImageRect(image, Rect.makeXYWH(4, 4, 20, 20));
                canvas.save();
                try {
                    canvas.clipRRect(RRect.makeXYWH(6, 6, 28, 28, 8), ClipMode.INTERSECT, true);
                    try (Paint paint = new Paint().setAntiAlias(true).setColor(0x80ff0000)) {
                        canvas.drawCircle(20 + round, 20, 15, paint);
                    }
                } finally {
                    canvas.restore();
                }
            }
        }
    }

    /**
     * Host state the slim boundary still owns, restored after a real bridge
     * pass. The dropped families are asserted separately: their values must be
     * untouched because nobody wrote them.
     */
    @Test
    void slimRestoresEveryFamilyItKeepsAndLeavesTheDroppedOnesAlone() {
        FramebufferTarget target = framebuffer(32, 32);
        FramebufferTarget draw = framebuffer(32, 32);
        FramebufferTarget read = framebuffer(16, 16);
        int vao = vao();
        int array = buffer();
        int element = buffer();
        int pack = buffer();
        int unpack = buffer();
        int ubo = buffer();
        int program = program();
        int highUnit = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) - 1;
        int highTexture = texture(GL_TEXTURE_2D);
        int highSampler = sampler();
        int uboSlots = glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS);
        int uboAlign = Math.max(16, glGetInteger(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT));

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw.framebuffer());
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read.framebuffer());
        glViewport(3, 4, 23, 24);
        glScissor(5, 6, 17, 18);
        glEnable(GL_SCISSOR_TEST);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_ADD);
        glBlendColor(.1f, .2f, .3f, .4f);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_GREATER);
        glDepthMask(false);
        glDepthRange(.2, .8);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
        glFrontFace(GL_CW);
        glColorMask(false, true, false, true);
        glEnable(GL_STENCIL_TEST);
        glStencilFuncSeparate(GL_FRONT, GL_NOTEQUAL, 3, 0x3f);
        glStencilMaskSeparate(GL_FRONT, 0x2f);
        glStencilOpSeparate(GL_FRONT, GL_REPLACE, GL_INCR, GL_DECR);
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, array);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, element);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pack);
        glBufferData(GL_PIXEL_PACK_BUFFER, 4096, GL_STREAM_READ);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
        glBufferData(GL_PIXEL_UNPACK_BUFFER, 4096, GL_STREAM_DRAW);
        glPixelStorei(GL_PACK_ALIGNMENT, 8);
        glPixelStorei(GL_PACK_ROW_LENGTH, 31);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 37);
        glActiveTexture(GL_TEXTURE0);
        glBindBuffer(GL_UNIFORM_BUFFER, ubo);
        glBufferData(GL_UNIFORM_BUFFER, Math.max(1, uboSlots) * uboAlign, GL_STATIC_DRAW);
        for (int i = 0; i < uboSlots; i++) glBindBufferRange(GL_UNIFORM_BUFFER, i, ubo, (long) i * uboAlign, 16);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        glEnable(GL_CLIP_DISTANCE0);
        glSampleMaski(0, 0x5);
        // Leave a high unit active on purpose: the bridge itself switches to
        // unit 0 inside the scope, so this is the value that must come back.
        glActiveTexture(GL_TEXTURE0 + highUnit);
        glBindTexture(GL_TEXTURE_2D, highTexture);
        glBindSampler(highUnit, highSampler);

        // Assert while the bridge is still open: closing it would run one more
        // full-state scope and hide a broken restore inside the draw itself.
        RuntimeHandle runtime = AtomLibRuntime.open("slim-restore");
        SkijaRenderBridge bridge = new SkijaRenderBridge(runtime);
        try {
            bridge.draw(target, c -> c.clear(0xff00ff00));
            assertAll(
            () -> assertEquals(draw.framebuffer(), glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)),
            () -> assertEquals(read.framebuffer(), glGetInteger(GL_READ_FRAMEBUFFER_BINDING)),
            () -> assertArrayEquals(new int[]{3, 4, 23, 24}, GlStateScopeTest.ints(GL_VIEWPORT, 4)),
            () -> assertArrayEquals(new int[]{5, 6, 17, 18}, GlStateScopeTest.ints(GL_SCISSOR_BOX, 4)),
            () -> assertTrue(glIsEnabled(GL_SCISSOR_TEST)),
            () -> assertTrue(glIsEnabled(GL_BLEND)),
            () -> assertEquals(GL_SRC_ALPHA, glGetInteger(GL_BLEND_SRC_RGB)),
            () -> assertEquals(GL_FUNC_REVERSE_SUBTRACT, glGetInteger(GL_BLEND_EQUATION_RGB)),
            () -> assertTrue(glIsEnabled(GL_DEPTH_TEST)),
            () -> assertFalse(glGetBoolean(GL_DEPTH_WRITEMASK)),
            () -> assertEquals(GL_GREATER, glGetInteger(GL_DEPTH_FUNC)),
            () -> assertTrue(glIsEnabled(GL_CULL_FACE)),
            () -> assertEquals(GL_FRONT, glGetInteger(GL_CULL_FACE_MODE)),
            () -> assertEquals(GL_CW, glGetInteger(GL_FRONT_FACE)),
            () -> assertTrue(glIsEnabled(GL_STENCIL_TEST)),
            () -> assertEquals(0x2f, glGetInteger(GL_STENCIL_WRITEMASK)),
            () -> assertEquals(GL_NOTEQUAL, glGetInteger(GL_STENCIL_FUNC)),
            () -> assertEquals(program, glGetInteger(GL_CURRENT_PROGRAM)),
            () -> assertEquals(vao, glGetInteger(GL_VERTEX_ARRAY_BINDING)),
            () -> assertEquals(array, glGetInteger(GL_ARRAY_BUFFER_BINDING)),
            () -> assertEquals(element, glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING)),
            () -> assertEquals(pack, glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING)),
            () -> assertEquals(unpack, glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING)),
            () -> assertEquals(8, glGetInteger(GL_PACK_ALIGNMENT)),
            () -> assertEquals(31, glGetInteger(GL_PACK_ROW_LENGTH)),
            () -> assertEquals(2, glGetInteger(GL_UNPACK_ALIGNMENT)),
            () -> assertEquals(37, glGetInteger(GL_UNPACK_ROW_LENGTH)),
            // Dropped from the slim snapshot on purpose: Skia never writes them,
            // so the host values must still be there without a restore.
            () -> assertEquals(GL_TEXTURE0 + highUnit, glGetInteger(GL_ACTIVE_TEXTURE)),
            () -> assertEquals(highTexture, glGetInteger(GL_TEXTURE_BINDING_2D)),
            () -> assertEquals(highSampler, glGetIntegeri(GL_SAMPLER_BINDING, highUnit)),
            () -> assertTrue(glIsEnabled(GL_CLIP_DISTANCE0)),
            () -> assertEquals(0x5, glGetIntegeri(GL_SAMPLE_MASK_VALUE, 0)),
            () -> assertEquals(ubo, glGetIntegeri(GL_UNIFORM_BUFFER_BINDING, 0)),
            () -> assertEquals(ubo, glGetIntegeri(GL_UNIFORM_BUFFER_BINDING, uboSlots - 1)),
            () -> assertEquals(16L, glGetInteger64i(GL_UNIFORM_BUFFER_SIZE, 0)),
            () -> assertEquals(GL_NO_ERROR, glGetError())
            );
        } finally {
            bridge.close();
            runtime.close();
        }
    }

    /**
     * The debug tripwire must stay silent on a real pass; otherwise the slim
     * boundary is dropping something Ganesh writes.
     */
    @Test
    void verifyTripwireReportsNoOmittedWrites() {
        GlStateScope.setSlimSnapshot(true);
        GlStateScope.setVerifySlim(true);
        FramebufferTarget target = framebuffer(32, 32);
        try (RuntimeHandle runtime = AtomLibRuntime.open("slim-verify");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            for (int round = 0; round < 3; round++) {
                final int r = round;
                bridge.draw(target, canvas -> paint(canvas, r));
            }
        }
        assertNull(GlStateScope.lastSlimViolation(), "slim boundary missed a write: " + GlStateScope.lastSlimViolation());
        assertEquals(GL_NO_ERROR, glGetError());
    }

    /** The optional 12-unit tier still covers Ganesh's scratch unit (the last one). */
    @Test
    void limitedUnitTierKeepsTheScratchUnitWatched() {
        GlStateScope.setSlimSnapshot(true);
        GlStateScope.setVerifySlim(true);
        GlStateScope.setSlimTextureUnits(12);
        FramebufferTarget target = framebuffer(32, 32);
        try (RuntimeHandle runtime = AtomLibRuntime.open("slim-verify-12");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            for (int round = 0; round < 3; round++) {
                final int r = round;
                bridge.draw(target, canvas -> paint(canvas, r));
            }
        }
        assertNull(GlStateScope.lastSlimViolation(),
            "the 12+last unit tier missed a texture unit: " + GlStateScope.lastSlimViolation());
        assertEquals(GL_NO_ERROR, glGetError());
    }

    private static byte[] pixels(FramebufferTarget target, int width, int height) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, target.framebuffer());
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        assertEquals(GL_NO_ERROR, glGetError());
        byte[] out = new byte[width * height * 4];
        buffer.get(out);
        return out;
    }
}
