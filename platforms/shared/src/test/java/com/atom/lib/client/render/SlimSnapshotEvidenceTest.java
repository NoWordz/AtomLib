// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.RuntimeHandle;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Evidence for the slim snapshot boundary, not a performance test.
 *
 * <p>Records every GL state family the slim path plans to stop snapshotting,
 * then runs real Skija work <em>inside</em> a bridge scope and reads the same
 * families back before the scope restores anything. If Ganesh writes one of
 * them the delta shows up here; if it writes none, "not snapshotting" is
 * provably safe for that family (a state nobody writes cannot leak).
 *
 * <p>Also prints the highest texture unit index Skia dirtied, so the "scan 12
 * units like Muonium" question is answered with data instead of assumption.
 */
final class SlimSnapshotEvidenceTest extends HiddenGlFixture {

    /** Host state deliberately made non-default so any driver write is visible. */
    private static final class HostProbe {
        final int uniformBufferCount = glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS);
        final int[] uniformBinding, uniformStart, uniformSize;
        final int drawBufferCount = glGetInteger(GL_MAX_DRAW_BUFFERS);
        final boolean[] indexedBlend;
        final int[][] indexedMask, indexedBlendState;
        final boolean[] clip;
        final int[] sampleMask;
        final int units = Math.min(32, glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS));
        final int[] binding2D, bindingRect, sampler;
        final int activeTexture;

        HostProbe() {
            uniformBinding = new int[uniformBufferCount];
            uniformStart = new int[uniformBufferCount];
            uniformSize = new int[uniformBufferCount];
            for (int i = 0; i < uniformBufferCount; i++) {
                uniformBinding[i] = glGetIntegeri(GL_UNIFORM_BUFFER_BINDING, i);
                uniformStart[i] = (int) glGetInteger64i(GL_UNIFORM_BUFFER_START, i);
                uniformSize[i] = (int) glGetInteger64i(GL_UNIFORM_BUFFER_SIZE, i);
            }
            indexedBlend = new boolean[drawBufferCount];
            indexedMask = new int[drawBufferCount][4];
            indexedBlendState = new int[drawBufferCount][6];
            int[] names = {GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB, GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA,
                GL_BLEND_EQUATION_RGB, GL_BLEND_EQUATION_ALPHA};
            for (int i = 0; i < drawBufferCount; i++) {
                indexedBlend[i] = glIsEnabledi(GL_BLEND, i);
                glGetIntegeri_v(GL_COLOR_WRITEMASK, i, indexedMask[i]);
                for (int j = 0; j < names.length; j++) indexedBlendState[i][j] = glGetIntegeri(names[j], i);
            }
            int clips = glGetInteger(GL_MAX_CLIP_DISTANCES);
            clip = new boolean[clips];
            for (int i = 0; i < clips; i++) clip[i] = glIsEnabled(GL_CLIP_DISTANCE0 + i);
            int words = glGetInteger(GL_MAX_SAMPLE_MASK_WORDS);
            sampleMask = new int[words];
            for (int i = 0; i < words; i++) sampleMask[i] = glGetIntegeri(GL_SAMPLE_MASK_VALUE, i);
            activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
            binding2D = new int[units];
            bindingRect = new int[units];
            sampler = new int[units];
            for (int unit = 0; unit < units; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit);
                binding2D[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
                bindingRect[unit] = glGetInteger(GL_TEXTURE_BINDING_RECTANGLE);
                sampler[unit] = glGetIntegeri(GL_SAMPLER_BINDING, unit);
            }
            glActiveTexture(activeTexture);
        }

        List<String> diff(HostProbe after) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < uniformBinding.length; i++) {
                if (uniformBinding[i] != after.uniformBinding[i]
                    || uniformStart[i] != after.uniformStart[i]
                    || uniformSize[i] != after.uniformSize[i]) {
                    out.add("UBO[" + i + "] " + uniformBinding[i] + "@" + uniformStart[i] + "+" + uniformSize[i]
                        + " -> " + after.uniformBinding[i] + "@" + after.uniformStart[i] + "+" + after.uniformSize[i]);
                }
            }
            for (int i = 0; i < indexedBlend.length; i++) {
                if (indexedBlend[i] != after.indexedBlend[i]) out.add("indexedBlend[" + i + "]");
                if (!java.util.Arrays.equals(indexedMask[i], after.indexedMask[i])) out.add("indexedColorMask[" + i + "]");
                if (!java.util.Arrays.equals(indexedBlendState[i], after.indexedBlendState[i])) out.add("indexedBlendFunc[" + i + "]");
            }
            for (int i = 0; i < clip.length; i++) if (clip[i] != after.clip[i]) out.add("clipDistance[" + i + "]");
            for (int i = 0; i < sampleMask.length; i++) if (sampleMask[i] != after.sampleMask[i]) out.add("sampleMask[" + i + "]");
            for (int i = 0; i < units; i++) {
                if (binding2D[i] != after.binding2D[i]) out.add("texture2D[" + i + "] " + binding2D[i] + " -> " + after.binding2D[i]);
                if (bindingRect[i] != after.bindingRect[i]) out.add("textureRect[" + i + "] " + bindingRect[i] + " -> " + after.bindingRect[i]);
                if (sampler[i] != after.sampler[i]) out.add("sampler[" + i + "] " + sampler[i] + " -> " + after.sampler[i]);
            }
            return out;
        }

        int highestDirtyTextureUnit(HostProbe after) {
            int highest = -1;
            for (int i = 0; i < units; i++) {
                if (binding2D[i] != after.binding2D[i] || bindingRect[i] != after.bindingRect[i] || sampler[i] != after.sampler[i]) highest = i;
            }
            return highest;
        }
    }

    @Test
    void skiaWritesStayInsideTheFamilySetTheSlimSnapshotKeeps() {
        FramebufferTarget target = framebuffer(48, 48);
        int hostTexture = texture(GL_TEXTURE_2D);
        int hostRect = texture(GL_TEXTURE_RECTANGLE);
        int hostSampler = sampler();
        int[] hostTextures = new int[32];
        for (int i = 0; i < hostTextures.length; i++) hostTextures[i] = texture(GL_TEXTURE_2D);
        int[] hostSamplers = new int[32];
        for (int i = 0; i < hostSamplers.length; i++) hostSamplers[i] = sampler();
        int uniformBuffer = buffer();

        // Distinct, non-default host state in every family the slim path drops.
        int uboCount = glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS);
        glBindBuffer(GL_UNIFORM_BUFFER, uniformBuffer);
        glBufferData(GL_UNIFORM_BUFFER, Math.max(1, uboCount) * 256, GL_STATIC_DRAW);
        for (int i = 0; i < uboCount; i++) glBindBufferRange(GL_UNIFORM_BUFFER, i, uniformBuffer, i * 256, 16);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        glDisablei(GL_BLEND, 0);
        if (uboCount > 1) glEnablei(GL_BLEND, 1);
        for (int i = 0; i < glGetInteger(GL_MAX_DRAW_BUFFERS); i++) {
            glColorMaski(i, (i & 1) != 0, (i & 2) != 0, true, false);
        }
        glEnable(GL_CLIP_DISTANCE0);
        if (glGetInteger(GL_MAX_CLIP_DISTANCES) > 4) glEnable(GL_CLIP_DISTANCE4);
        glSampleMaski(0, 0x5);
        for (int unit = 0; unit < Math.min(32, glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)); unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, hostTextures[unit]);
            glBindSampler(unit, hostSamplers[unit]);
        }
        int last = Math.min(32, glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)) - 1;
        glActiveTexture(GL_TEXTURE0 + last);
        glBindTexture(GL_TEXTURE_RECTANGLE, hostRect);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, hostTexture);
        glBindSampler(0, hostSampler);
        glEnable(GL_DITHER);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);

        // Per-buffer blend functions only matter when they differ from the
        // global value, so make them differ (Skia's global glBlendFunc* then
        // clobbers them, which is why the scope restores them separately).
        var caps = org.lwjgl.opengl.GL.getCapabilities();
        boolean indexed = caps.OpenGL40 || caps.GL_ARB_draw_buffers_blend;
        if (indexed) {
            for (int i = 0; i < glGetInteger(GL_MAX_DRAW_BUFFERS); i++) {
                int src = i == 0 ? GL_ONE : GL_ZERO;
                int dst = i == 0 ? GL_ZERO : GL_ONE;
                if (caps.OpenGL40) {
                    org.lwjgl.opengl.GL40C.glBlendFuncSeparatei(i, src, dst, GL_DST_ALPHA, GL_ONE_MINUS_DST_ALPHA);
                    org.lwjgl.opengl.GL40C.glBlendEquationSeparatei(i, GL_FUNC_ADD, GL_FUNC_ADD);
                } else {
                    org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(i, src, dst, GL_DST_ALPHA, GL_ONE_MINUS_DST_ALPHA);
                    org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendEquationSeparateiARB(i, GL_FUNC_ADD, GL_FUNC_ADD);
                }
            }
        }

        AtomicReference<HostProbe> after = new AtomicReference<>();
        AtomicReference<List<String>> dirty = new AtomicReference<>();
        HostProbe[] before = new HostProbe[1];

        try (RuntimeHandle runtime = AtomLibRuntime.open("slim-evidence");
             SkijaRenderBridge bridge = new SkijaRenderBridge(runtime)) {
            // Warm-up pass: context creation and one-frame initialisation are
            // not what the snapshot has to survive, the steady state is.
            bridge.draw(target, c -> c.clear(0xff000000));
            before[0] = new HostProbe();
            final HostProbe start = before[0];
            bridge.withContext(context -> {
                try (BackendRenderTarget backend = BackendRenderTarget.makeGL(48, 48, 0, 0, target.framebuffer(), GL_RGBA8);
                     Surface surface = Surface.wrapBackendRenderTarget(context, backend,
                         SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888, ColorSpace.getSRGB())) {
                    paintScene(surface.getCanvas());
                    context.flushAndSubmit(surface);
                }
                HostProbe end = new HostProbe();
                dirty.set(start.diff(end));
                after.set(end);
                return null;
            });
        }

        HostProbe base = before[0];
        int highest = base.highestDirtyTextureUnit(after.get());
        System.out.println("SLIM_EVIDENCE dirty=" + dirty.get() + " highest_texture_unit=" + highest
            + " units=" + base.units + " ubo_bindings=" + base.uniformBufferCount
            + " draw_buffers=" + base.drawBufferCount);
        List<String> uboDirty = dirty.get().stream().filter(s -> s.startsWith("UBO[")).toList();
        List<String> indexedDirty = dirty.get().stream().filter(s -> s.startsWith("indexed")).toList();
        List<String> clipDirty = dirty.get().stream().filter(s -> s.startsWith("clipDistance")).toList();
        List<String> maskDirty = dirty.get().stream().filter(s -> s.startsWith("sampleMask")).toList();
        assertAll(
            () -> assertTrue(uboDirty.isEmpty(), "Skija wrote uniform buffer bindings: " + uboDirty),
            () -> assertTrue(clipDirty.isEmpty(), "Skija wrote clip distances: " + clipDirty),
            () -> assertTrue(maskDirty.isEmpty(), "Skija wrote the sample mask: " + maskDirty),
            // Per-draw-buffer *blend functions* must stay in the snapshot: a
            // global glBlendFuncSeparate rewrites every buffer index, which is
            // exactly what Ganesh does. (The host values were made distinct.)
            () -> assertTrue(indexedDirty.isEmpty() || indexedDirty.stream().allMatch(s -> s.startsWith("indexedBlendFunc")),
                "Ganesh should only clobber per-buffer blend funcs via the global call: " + indexedDirty),
            // Texture units above 11 may only be the documented scratch/upload
            // unit (the last one). A write anywhere else would invalidate the
            // "12 units like Muonium" idea in a way we must see.
            () -> assertTrue(highest < 12 || highest == base.units - 1,
                "Skija dirtied an unexpected texture unit: " + highest)
        );
        assertEquals(GL_NO_ERROR, glGetError());
    }

    private static void paintScene(Canvas canvas) {
        canvas.clear(0xff101820);
        try (Bitmap bitmap = new Bitmap()) {
            assertTrue(bitmap.allocN32Pixels(8, 8));
            bitmap.erase(0x80ff00ff);
            try (Image image = Image.makeRasterFromBitmap(bitmap)) {
                canvas.drawImageRect(image, Rect.makeXYWH(2, 2, 20, 20));
                canvas.save();
                try {
                    canvas.clipRRect(RRect.makeXYWH(6, 6, 36, 36, 9), ClipMode.INTERSECT, true);
                    try (Paint paint = new Paint().setAntiAlias(true)) {
                        paint.setColor(0x80ff0000);
                        canvas.drawCircle(24, 24, 20, paint);
                        paint.setColor(0x8000ff00);
                        canvas.drawRect(Rect.makeXYWH(2, 2, 44, 44), paint);
                    }
                } finally {
                    canvas.restore();
                }
                try (Paint layerPaint = new Paint().setColor(0x40ffffff);
                     Paint inner = new Paint().setColor(0x400000ff)) {
                    canvas.saveLayer(null, layerPaint);
                    try {
                        canvas.drawRRect(RRect.makeXYWH(10, 10, 28, 28, 6), inner);
                    } finally {
                        canvas.restore();
                    }
                }
                canvas.drawImageRect(image, Rect.makeXYWH(24, 24, 16, 16));
            }
        }
    }
}
