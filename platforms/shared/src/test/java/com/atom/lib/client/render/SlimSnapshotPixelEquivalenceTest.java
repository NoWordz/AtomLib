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
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * The decisive evidence for the slim snapshot boundary: <b>pixel equivalence</b>.
 *
 * <p>Unlike {@link SlimSnapshotTest}, which compares Skija's own output, this
 * probe drives the frame with a <em>real GLSL shader</em> and reads the result
 * back from the framebuffer. The shader samples a texture and blends, so any
 * difference in the host's GL state between the full and the slim boundary
 * changes the pixels rather than merely a state query.
 *
 * <p>Shape mirrors the production frame of AC: a vanilla pass that dirties the
 * GL state, then the Skija bridge pass, then another vanilla pass that draws
 * with the real shader. The bridge is asked for the full boundary and for the
 * slim boundary in the same context and the readback of every frame must be
 * byte-identical.
 *
 * <p><b>The shader draw inherits whatever the scope restored.</b> It sets no
 * blend function, no colour mask, no stencil/depth/cull state, no viewport and
 * no texture binding of its own: every one of those is decided by the state
 * the scope handed back. A family the slim boundary forgets to restore
 * therefore changes the pixels instead of silently matching.
 *
 * <p>Each round starts from a cleared host framebuffer. The clear happens
 * <em>before</em> the bridge pass, so it cannot paper over a post-scope leak.
 */
final class SlimSnapshotPixelEquivalenceTest extends HiddenGlFixture {

    private static final boolean INITIAL_SLIM = GlStateScope.slimSnapshot();
    private static final int SIZE = 64;

    @AfterEach void restoreDefaults() { GlStateScope.setSlimSnapshot(INITIAL_SLIM); }

    /** Real GLSL: samples the bound texture and modulates it. */
    private static final String VERTEX = """
        #version 330 core
        layout(location=0) in vec2 inPos;
        layout(location=1) in vec2 inUv;
        out vec2 vUv;
        void main() { vUv = inUv; gl_Position = vec4(inPos, 0.0, 1.0); }
        """;
    private static final String FRAGMENT = """
        #version 330 core
        uniform sampler2D uTex;
        uniform float uScale;
        in vec2 vUv;
        out vec4 outColor;
        void main() {
            vec4 t = texture(uTex, vUv);
            outColor = vec4(t.rgb * uScale, t.a);
        }
        """;

    @Test void slimAndFullBoundariesProduceByteIdenticalShaderFrames() {
        FramebufferTarget target = framebuffer(SIZE, SIZE);
        FramebufferTarget host = framebuffer(SIZE, SIZE);

        // Host-side texture the real shader samples, on unit 0.
        int hostTexture = texture(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, hostTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 4, 4, 0, GL_RGBA, GL_UNSIGNED_BYTE, checkerboard());

        int program = linkProgram(VERTEX, FRAGMENT);
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        float[] quad = {
            -0.9f, -0.9f, 0f, 0f,
             0.9f, -0.9f, 1f, 0f,
             0.9f,  0.9f, 1f, 1f,
            -0.9f, -0.9f, 0f, 0f,
             0.9f,  0.9f, 1f, 1f,
            -0.9f,  0.9f, 0f, 1f
        };
        FloatBuffer fb = BufferUtils.createFloatBuffer(quad.length);
        fb.put(quad).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 16, 8);
        glBindVertexArray(0);

        // A host uniform buffer binding the slim boundary drops.
        int ubo = buffer();
        int uboSlots = glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS);
        glBindBuffer(GL_UNIFORM_BUFFER, ubo);
        glBufferData(GL_UNIFORM_BUFFER, Math.max(1, uboSlots) * 256, GL_STATIC_DRAW);
        for (int i = 0; i < uboSlots; i++) glBindBufferRange(GL_UNIFORM_BUFFER, i, ubo, (long) i * 256, 16);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        // A high texture unit the host keeps active; the bridge switches away.
        int highUnit = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) - 1;
        int highTexture = texture(GL_TEXTURE_2D);
        glActiveTexture(GL_TEXTURE0 + highUnit);
        glBindTexture(GL_TEXTURE_2D, highTexture);
        glActiveTexture(GL_TEXTURE0);

        List<byte[]> fullFrames = runFrames(false, target, host, program, vao, vbo, ubo, highUnit, highTexture, hostTexture);
        List<byte[]> slimFrames = runFrames(true, target, host, program, vao, vbo, ubo, highUnit, highTexture, hostTexture);

        assertEquals(fullFrames.size(), slimFrames.size());
        for (int i = 0; i < fullFrames.size(); i++) {
            int[] diff = diff(fullFrames.get(i), slimFrames.get(i));
            System.out.println("PIXEL_EQ round=" + i + " differing_bytes=" + diff[0] + " max_delta=" + diff[1]);
            assertEquals(0, diff[0], "slim boundary changed the host's shader output in round " + i);
        }
        // The shader must actually have drawn something, otherwise the comparison
        // would be vacuous (two identical black frames).
        byte[] first = fullFrames.get(0);
        assertTrue(hasNonBlackPixel(first), "the real shader produced no visible pixels");
        // The host's colour mask (green off) must survive the scope, so the
        // green channel of the shader output is zero. If the scope lost the
        // mask, green would appear — a visible proof that the frame is not
        // just "two empty buffers that happen to match".
        assertEquals(0, greenOf(first), "the host colour mask did not survive the scope");
        assertEquals(GL_NO_ERROR, glGetError());
    }

    /**
     * Runs the vanilla / bridge / vanilla frame sequence under one boundary.
     * The host framebuffer is cleared before the bridge pass, so the shader
     * draw of every round composites onto a known background.
     */
    private static List<byte[]> runFrames(boolean slim, FramebufferTarget target, FramebufferTarget host,
        int program, int vao, int vbo, int ubo, int highUnit, int highTexture, int hostTexture) {
        List<byte[]> frames = new ArrayList<>();
        GlStateScope.setSlimSnapshot(slim);
        try (RuntimeHandle runtime = AtomLibRuntime.open(slim ? "pixel-eq-slim" : "pixel-eq-full");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            // Warm-up: the first pass creates the context and the surface.
            bridge.drawFrame(target, (canvas, frame) -> canvas.clear(0xff102030));
            for (int round = 0; round < 3; round++) {
                final int r = round;
                bridge.vanillaPass(() -> {
                    prepareHostTarget(host);
                    hostileHostState(program, vao, vbo, ubo, highUnit, highTexture, hostTexture, r);
                });
                bridge.drawFrame(target, (canvas, frame) -> paintSkija(canvas, r));
                // No state of its own: everything below is what the scope gave back.
                bridge.vanillaPass(() -> drawWithRealShader(host, program, 0.5f + r * 0.25f));
                frames.add(readPixels(host, SIZE, SIZE));
            }
        }
        return frames;
    }

    /**
     * Clears the host framebuffer to a known background. Runs before the
     * bridge pass, so it cannot hide a leak that happens after the scope.
     * The clear itself needs an explicit mask/blend state because
     * {@code glClear} honours the colour mask and the stencil/depth masks.
     */
    private static void prepareHostTarget(FramebufferTarget host) {
        glBindFramebuffer(GL_FRAMEBUFFER, host.framebuffer());
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_BLEND);
        glColorMask(true, true, true, true);
        glDepthMask(true);
        glStencilMask(0xff);
        glClearColor(0f, 0f, 0f, 1f);
        glClearDepth(1.0);
        glClearStencil(0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    }

    /**
     * Host state that a real MC frame could plausibly carry and that a Skia
     * pass would disturb: stencil, depth, cull, blend, per-draw-buffer masks,
     * a UBO binding, and a high texture unit.
     */
    private static void hostileHostState(int program, int vao, int vbo,
        int ubo, int highUnit, int highTexture, int hostTexture, int round) {
        glViewport(0, 0, SIZE, SIZE);
        glDisable(GL_SCISSOR_TEST);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_REVERSE_SUBTRACT);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
        glFrontFace(GL_CW);
        glEnable(GL_STENCIL_TEST);
        glStencilFuncSeparate(GL_FRONT, GL_NOTEQUAL, 3 + round, 0xff);
        glStencilOpSeparate(GL_FRONT, GL_REPLACE, GL_INCR, GL_DECR);
        glStencilMaskSeparate(GL_FRONT, 0x2f);
        // The green channel is switched off: if the scope loses this mask the
        // shader output turns green, which the pixel comparison must catch.
        glColorMask(true, false, true, true);
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindBuffer(GL_UNIFORM_BUFFER, ubo);
        glActiveTexture(GL_TEXTURE0 + highUnit);
        glBindTexture(GL_TEXTURE_2D, highTexture);
        glActiveTexture(GL_TEXTURE0);
        // Unit 0 carries the texture the shader samples. The fixture's
        // texture() helper binds to whatever unit is active, so the binding is
        // restated here instead of relying on creation order.
        glBindTexture(GL_TEXTURE_2D, hostTexture);
    }

    /**
     * The vanilla draw that produces the readback. It deliberately sets no
     * blend function, colour mask, stencil/depth/cull state, viewport or
     * texture binding: the program, the VAO, the unit-0 texture and the
     * viewport all come from what the scope restored.
     */
    private static void drawWithRealShader(FramebufferTarget host, int program, float scale) {
        glBindFramebuffer(GL_FRAMEBUFFER, host.framebuffer());
        glUniform1i(glGetUniformLocation(program, "uTex"), 0);
        glUniform1f(glGetUniformLocation(program, "uScale"), scale);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private static void paintSkija(Canvas canvas, int round) {
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

    private static ByteBuffer checkerboard() {
        ByteBuffer data = BufferUtils.createByteBuffer(4 * 4 * 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int v = ((x + y) & 1) == 0 ? 220 : 40;
                data.put((byte) v).put((byte) (v / 2)).put((byte) (255 - v)).put((byte) 255);
            }
        }
        data.flip();
        return data;
    }

    private static int linkProgram(String vertex, String fragment) {
        int vs = compile(GL_VERTEX_SHADER, vertex);
        int fs = compile(GL_FRAGMENT_SHADER, fragment);
        int p = glCreateProgram();
        glAttachShader(p, vs);
        glAttachShader(p, fs);
        glLinkProgram(p);
        assertEquals(GL_TRUE, glGetProgrami(p, GL_LINK_STATUS), glGetProgramInfoLog(p));
        glDeleteShader(vs);
        glDeleteShader(fs);
        return p;
    }

    private static int compile(int type, String source) {
        int s = glCreateShader(type);
        glShaderSource(s, source);
        glCompileShader(s);
        assertEquals(GL_TRUE, glGetShaderi(s, GL_COMPILE_STATUS), glGetShaderInfoLog(s));
        return s;
    }

    private static byte[] readPixels(FramebufferTarget target, int width, int height) {
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

    private static int[] diff(byte[] a, byte[] b) {
        int differing = 0, max = 0;
        for (int i = 0; i < a.length; i++) {
            int d = Math.abs((a[i] & 0xff) - (b[i] & 0xff));
            if (d != 0) { differing++; if (d > max) max = d; }
        }
        return new int[] { differing, max };
    }

    private static boolean hasNonBlackPixel(byte[] pixels) {
        for (int i = 0; i < pixels.length; i += 4) {
            if ((pixels[i] & 0xff) + (pixels[i + 1] & 0xff) + (pixels[i + 2] & 0xff) > 24) return true;
        }
        return false;
    }

    /** The largest green channel value in the frame; zero when the mask survived. */
    private static int greenOf(byte[] pixels) {
        int max = 0;
        for (int i = 1; i < pixels.length; i += 4) max = Math.max(max, pixels[i] & 0xff);
        return max;
    }
}
