// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.RuntimeHandle;
import io.github.humbleui.skija.BackendState;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * TDD contract for the P0-1/P0-2 fast-state branch.
 *
 * <p>Everything here is observable without changing any public API: the tests
 * reach package-private probes by reflection, so the unmodified (legacy) build
 * compiles but fails these tests, while the fast-state build passes them.
 * Real GL assertions run in a hidden core context via {@link HiddenGlFixture}.
 */
final class FastStateOptimizationTest extends HiddenGlFixture {

    /** Remember the -D the suite was launched with, so @AfterEach never flips a real A/B run. */
    private static final boolean INITIAL_FLAG = Boolean.getBoolean("atomlib.bridge.fastState");

    /**
     * Hard-coded from skija-shared 0.143.18 BackendState.java (NOT derived from
     * the code under test): the 11 effective GL bits 1..1024. This is the
     * I-2 whitelist evidence, independent of FAST_RESET_STATES itself.
     */
    private static final Set<BackendState> ALL_EFFECTIVE_GL_BITS = EnumSet.of(
            BackendState.GL_RENDER_TARGET, BackendState.GL_TEXTURE_BINDING,
            BackendState.GL_VIEW, BackendState.GL_BLEND, BackendState.GL_MSAA_ENABLE,
            BackendState.GL_VERTEX, BackendState.GL_STENCIL, BackendState.GL_PIXEL_STORE,
            BackendState.GL_PROGRAM, BackendState.GL_FIXED_FUNCTION, BackendState.GL_MISC);
    /** 1|2|4|8|16|32|64|128|256|512|1024 = 2047 (effectively GL_ALL). */
    private static final int ALL_EFFECTIVE_GL_BITS_OR = 2047;

    private static Method method(Class<?> owner, String name, Class<?>... args) throws Exception {
        Method m = owner.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    private static void setFlag(boolean on) throws Exception {
        method(SkijaRenderBridge.class, "setFastState", boolean.class).invoke(null, on);
    }

    private static boolean flag() throws Exception {
        return (Boolean) method(SkijaRenderBridge.class, "fastState").invoke(null);
    }

    private static int lastResetFlags() throws Exception {
        return (Integer) method(SkijaRenderBridge.class, "lastResetFlags").invoke(null);
    }

    private static long limitQueryCount() throws Exception {
        return (Long) method(GlStateScope.class, "limitQueryCount").invoke(null);
    }

    private static void invalidateLimits() throws Exception {
        method(GlStateScope.class, "invalidateLimits").invoke(null);
    }

    private static BackendState[] fastResetStates() throws Exception {
        Field f = SkijaRenderBridge.class.getDeclaredField("FAST_RESET_STATES");
        f.setAccessible(true);
        return (BackendState[]) f.get(null);
    }

    @AfterEach
    void restoreLegacyDefault() throws Exception {
        setFlag(INITIAL_FLAG);
        invalidateLimits();
    }

    @Test
    void fastStateFlagDefaultsToFalse() throws Exception {
        Assumptions.assumeTrue(System.getProperty("atomlib.bridge.fastState") == null,
                "default only verifiable when no -Datomlib.bridge.fastState is supplied");
        assertFalse(flag(), "atomlib.bridge.fastState must default to false (legacy path)");
    }

    /**
     * I-2: assert against an INDEPENDENT whitelist (the 11 effective bits read
     * from BackendState.java), not against the constant under test. Evidence:
     * logs/i1-evidence-7bit.log — the earlier 7-bit whitelist was unsafe in
     * real GL (differing_pixels=9216), so the reset must cover every effective
     * bit. OR=2047 means P0-1 is equivalent to resetGLAll() and has no benefit.
     */
    @Test
    void fastResetStatesCoverEveryEffectiveGlBit() throws Exception {
        BackendState[] states = fastResetStates();
        Set<BackendState> actual = EnumSet.copyOf(Arrays.asList(states));
        assertEquals(ALL_EFFECTIVE_GL_BITS, actual,
                "reset must cover all 11 effective BackendState bits (I-1 evidence: 7 bits unsafe)");
        assertEquals(ALL_EFFECTIVE_GL_BITS.size(), states.length, "no duplicate reset bits");
        assertFalse(actual.contains(BackendState.GL_ALL));
        assertFalse(actual.contains(BackendState.ALL));
        int or = 0;
        for (BackendState state : states) {
            or |= state.getValue();
        }
        assertEquals(ALL_EFFECTIVE_GL_BITS_OR, or,
                "OR must be 2047 = every effective bit = resetGLAll() equivalent (no P0-1 benefit)");
    }

    @Test
    void flagOffDoesNotWriteFastStateProbe() throws Exception {
        setFlag(false);
        Field probe = SkijaRenderBridge.class.getDeclaredField("lastResetFlags");
        probe.setAccessible(true);
        probe.setInt(null, -1);
        FramebufferTarget target = framebuffer(16, 16);
        try (RuntimeHandle runtime = AtomLibRuntime.open("fast-state-off");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            bridge.draw(target, c -> c.clear(0xff010203));
            assertEquals(-1, lastResetFlags(),
                    "flag off must not write the probe: the legacy path stays byte-for-byte identical");
        }
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void flagOnUsesAllEffectiveGlBits() throws Exception {
        setFlag(true);
        FramebufferTarget target = framebuffer(16, 16);
        try (RuntimeHandle runtime = AtomLibRuntime.open("fast-state-on");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            bridge.draw(target, c -> c.clear(0xff010203));
            assertEquals(ALL_EFFECTIVE_GL_BITS_OR, lastResetFlags(),
                    "flag on must OR to 2047 (all 11 effective bits), not a smaller whitelist");
        }
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void limitQueriesAreCachedUntilGenerationChanges() throws Exception {
        setFlag(true);
        invalidateLimits();
        long before = limitQueryCount();
        try (GlStateScope first = GlStateScope.capture()) {
            assertNotNull(first);
        }
        long afterFirst = limitQueryCount();
        assertTrue(afterFirst > before, "a cache miss must re-query the GL limits");
        try (GlStateScope second = GlStateScope.capture()) {
            assertNotNull(second);
        }
        assertEquals(afterFirst, limitQueryCount(),
                "same context and size must reuse cached limits without extra queries");
        invalidateLimits();
        try (GlStateScope third = GlStateScope.capture()) {
            assertNotNull(third);
        }
        assertTrue(limitQueryCount() > afterFirst,
                "a generation change (context / size) must invalidate the cache");
    }

    @Test
    void limitCacheInvalidatesOnRealContextSwitch() throws Exception {
        setFlag(true);
        invalidateLimits();
        try (GlStateScope first = GlStateScope.capture()) {
            assertNotNull(first);
        }
        long before = limitQueryCount();
        long firstContext = glfwGetCurrentContext();
        var firstCaps = GL.getCapabilities();
        long other = glfwCreateWindow(16, 16, "fast-state limit context", 0, 0);
        assertNotEquals(0, other, "a second hidden GL context is required");
        try {
            glfwMakeContextCurrent(other);
            GL.createCapabilities();
            try (GlStateScope second = GlStateScope.capture()) {
                assertNotNull(second);
            }
            assertTrue(limitQueryCount() > before,
                    "moving to another GL context must re-read the limits");
        } finally {
            glfwMakeContextCurrent(firstContext);
            GL.setCapabilities(firstCaps);
            glfwDestroyWindow(other);
        }
    }

    @Test
    void bothFlagsProduceIdenticalPixelsAcrossVanillaAndSkijaPasses() throws Exception {
        FramebufferTarget legacy = framebuffer(32, 32);
        FramebufferTarget fast = framebuffer(32, 32);
        byte[] legacyPixels;
        byte[] fastPixels;
        try (RuntimeHandle runtime = AtomLibRuntime.open("fast-state-ab");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            legacyPixels = sequence(bridge, legacy, false);
            fastPixels = sequence(bridge, fast, true);
        }
        assertArrayEquals(legacyPixels, fastPixels,
                "the fast path must not change a single pixel of the frame");
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void fastPathRestoresHostGlStateAndCloseIsIdempotent() throws Exception {
        setFlag(true);
        int vao = vao(), buffer = buffer();
        // framebuffer() disables GL_SCISSOR_TEST internally, so dirty the host
        // state only after the fixture has created its framebuffer.
        FramebufferTarget target = framebuffer(32, 32);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glViewport(2, 3, 19, 20);
        glEnable(GL_SCISSOR_TEST);
        glScissor(1, 2, 3, 4);
        glPixelStorei(GL_PACK_ALIGNMENT, 8);
        try (RuntimeHandle runtime = AtomLibRuntime.open("fast-state-restore");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            bridge.draw(target, c -> c.clear(0xff112233));
            assertArrayEquals(new int[]{2, 3, 19, 20}, GlStateScopeTest.ints(GL_VIEWPORT, 4));
            assertTrue(glIsEnabled(GL_SCISSOR_TEST));
            assertEquals(vao, glGetInteger(GL_VERTEX_ARRAY_BINDING));
            assertEquals(buffer, glGetInteger(GL_ARRAY_BUFFER_BINDING));
            assertEquals(8, glGetInteger(GL_PACK_ALIGNMENT));
            bridge.close();
            bridge.close();
        }
        assertEquals(GL_NO_ERROR, glGetError());
    }

    /**
     * I-1 evidence (real GL, one Skia context across 6 vanilla<->Skija rounds).
     * The vanilla pass dirties the four state families a 7-bit whitelist omits:
     * stencil func/op, GL_MULTISAMPLE, dither/sRGB, depth/cull/polygon/line.
     * The Skia content uses a non-rect clip + translucent stacking (Ganesh
     * implements complex clips through the stencil buffer). Compares on vs off
     * (GL_ALL) pixel by pixel and prints the measured evidence to the log.
     */
    @Test
    void dirtyStateEvidenceAcrossVanillaAndSkijaPasses() throws Exception {
        byte[] legacy = dirtySequence(false);
        byte[] fast = dirtySequence(true);
        int[] diff = pixelDiff(legacy, fast);
        System.out.println("I1_EVIDENCE reset_bits=" + Arrays.toString(fastResetStates())
                + " differing_pixels=" + diff[0] + " max_channel_delta=" + diff[1]
                + " legacy_bytes=" + legacy.length + " fast_bytes=" + fast.length);
        assertArrayEquals(legacy, fast,
                "fastState=on must be pixel-identical to GL_ALL under dirty stencil/MSAA/fixed-function state");
    }

    private byte[] dirtySequence(boolean on) throws Exception {
        setFlag(on);
        resetGlBaseline();
        FramebufferTarget target = framebuffer(48, 48);
        byte[] pixels;
        try (RuntimeHandle runtime = AtomLibRuntime.open("i1-evidence-" + on);
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            for (int round = 0; round < 6; round++) {
                final int r = round;
                bridge.vanillaPass(() -> dirtyFourStateFamilies(target, r));
                bridge.draw(target, c -> drawStencilSensitiveContent(c, r));
            }
            pixels = framePixels(target, 48, 48);
        }
        return pixels;
    }

    private static void dirtyFourStateFamilies(FramebufferTarget target, int round) {
        glBindFramebuffer(GL_FRAMEBUFFER, target.framebuffer());
        glDisable(GL_SCISSOR_TEST);
        glColorMask(true, true, true, true);
        // (1) GL_STENCIL: func / op / mask
        glEnable(GL_STENCIL_TEST);
        glStencilFuncSeparate(GL_FRONT, GL_NOTEQUAL, 3 + (round & 3), 0x3f);
        glStencilMaskSeparate(GL_FRONT, 0x2f);
        glStencilOpSeparate(GL_FRONT, GL_REPLACE, GL_INCR, GL_DECR);
        glStencilFuncSeparate(GL_BACK, GL_LESS, 7, 0x7f);
        glStencilOpSeparate(GL_BACK, GL_INVERT, GL_KEEP, GL_ZERO);
        // (2) GL_MSAA_ENABLE
        if ((round & 1) == 0) glDisable(GL_MULTISAMPLE); else glEnable(GL_MULTISAMPLE);
        // (3) GL_MISC / GL_FIXED_FUNCTION: dither, sRGB
        glEnable(GL_DITHER);
        glEnable(GL_FRAMEBUFFER_SRGB);
        // (4) GL_FIXED_FUNCTION: depth / cull / polygon mode / line width
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_GREATER);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
        glFrontFace(GL_CW);
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        glLineWidth(3.0F);
        glPixelStorei(GL_PACK_ALIGNMENT, 8);
    }

    private static void drawStencilSensitiveContent(Canvas canvas, int round) {
        canvas.clear(0xff101820);
        canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(6, 6, 36, 36, 9), ClipMode.INTERSECT, true);
            try (Paint paint = new Paint().setAntiAlias(true)) {
                paint.setColor(0x80ff0000);
                canvas.drawCircle(24, 24, 20, paint);
                paint.setColor(0x8000ff00);
                canvas.drawRect(Rect.makeXYWH(2, 2, 44, 44), paint);
                paint.setColor(0x800000ff);
                canvas.drawRRect(RRect.makeXYWH(10, 10, 28, 28, 6), paint);
            }
        } finally {
            canvas.restore();
        }
        try (Paint paint = new Paint().setAntiAlias(true).setColor(0x66ffffff)) {
            canvas.drawCircle(12 + round, 12, 7, paint);
        }
    }

    private static void resetGlBaseline() {
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DITHER);
        glDisable(GL_FRAMEBUFFER_SRGB);
        glEnable(GL_MULTISAMPLE);
        glColorMask(true, true, true, true);
        glDepthMask(true);
        glStencilMask(0xff);
        glStencilFunc(GL_ALWAYS, 0, 0xff);
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
        glDepthFunc(GL_LESS);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glLineWidth(1.0F);
        glPixelStorei(GL_PACK_ALIGNMENT, 4);
        glClearColor(0, 0, 0, 0);
        glClearDepth(1.0);
        glClearStencil(0);
    }

    private static int[] pixelDiff(byte[] a, byte[] b) {
        int differing = 0, maxDelta = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int d = Math.abs((a[i] & 0xff) - (b[i] & 0xff));
            if (d != 0) {
                differing++;
                maxDelta = Math.max(maxDelta, d);
            }
        }
        differing += Math.abs(a.length - b.length);
        return new int[]{differing, maxDelta};
    }

    /**
     * Host dirties ALL FOUR state families between Skija passes (I-2): stencil
     * func/op, GL_MULTISAMPLE, dither/sRGB, depth/cull/polygon/line. This is
     * the exact case reset() guards, and the 7-bit whitelist failed it.
     */
    private static byte[] sequence(SkijaRenderBridge bridge, FramebufferTarget target, boolean on) throws Exception {
        setFlag(on);
        resetGlBaseline();
        glBindFramebuffer(GL_FRAMEBUFFER, target.framebuffer());
        glViewport(0, 0, 32, 32);
        glDisable(GL_SCISSOR_TEST);
        glColorMask(true, true, true, true);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);

        bridge.draw(target, c -> c.clear(0xff204060));
        bridge.vanillaPass(() -> {
            dirtyFourStateFamilies(target, 0);
            glClearColor(0.1F, 0.2F, 0.3F, 1F);
            glClear(GL_COLOR_BUFFER_BIT);
        });
        bridge.draw(target, c -> {
            c.clear(0xff102030);
            try (Paint paint = new Paint().setColor(0xff00ff00)) {
                c.drawRect(Rect.makeXYWH(4, 4, 12, 12), paint);
            }
        });
        bridge.vanillaPass(() -> {
            dirtyFourStateFamilies(target, 1);
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE, GL_ONE);
        });
        bridge.draw(target, c -> c.clear(0xffa0b0c0));
        return framePixels(target, 32, 32);
    }

    private static byte[] framePixels(FramebufferTarget target, int width, int height) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, target.framebuffer());
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        assertEquals(GL_NO_ERROR, glGetError());
        byte[] pixels = new byte[width * height * 4];
        buffer.get(pixels);
        return pixels;
    }
}
