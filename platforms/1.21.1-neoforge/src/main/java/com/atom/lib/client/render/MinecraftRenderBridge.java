// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.RuntimeHandle;
import com.atom.lib.render.CustomPainter;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import io.github.humbleui.skija.Image;
import java.util.function.Function;

/** Client-only explicit access; not referenced by the common mod entrypoint. */
public final class MinecraftRenderBridge implements AutoCloseable {
    private static MinecraftRenderBridge instance;
    private final RuntimeHandle runtime=AtomLibRuntime.open("atomlib-minecraft-render");
    private final SkijaRenderBridge bridge=new SkijaRenderBridge(runtime);
    private MinecraftRenderBridge() { }

    public static MinecraftRenderBridge get() {
        RenderSystem.assertOnRenderThread();
        if(instance==null) instance=new MinecraftRenderBridge();
        return instance;
    }
    /** Physical framebuffer coordinates; caller flushes pending GuiGraphics first. */
    public void draw(CustomPainter painter) {
        RenderSystem.assertOnRenderThread();
        RenderTarget target=Minecraft.getInstance().getMainRenderTarget();
        if(target.width<=0 || target.height<=0) return;
        bridge.draw(new FramebufferTarget(target,target.frameBufferId,target.getColorTextureId(),target.width,target.height),painter);
    }
    public void drawFrame(BorrowedFramePainter painter) { RenderSystem.assertOnRenderThread();bridge.drawFrame(target(),painter); }
    public <T> T withFrameAccess(Function<BorrowedFrame,T> action) { RenderSystem.assertOnRenderThread();return bridge.withFrameAccess(target(),action); }
    /** Host-side raw context lease; never execute Minecraft rendering inside a Skija pass. */
    public <T> T withContext(Function<io.github.humbleui.skija.DirectContext,T> action) { RenderSystem.assertOnRenderThread();return bridge.withContext(action); }
    public void prepare() { RenderSystem.assertOnRenderThread();bridge.prepare(target()); }
    public Image captureFrame() { RenderSystem.assertOnRenderThread();return bridge.captureFrame(target()); }
    private static FramebufferTarget target() {
        RenderTarget target=Minecraft.getInstance().getMainRenderTarget();
        return new FramebufferTarget(target,target.frameBufferId,target.getColorTextureId(),target.width,target.height);
    }
    public void vanillaPass(Runnable painter) { RenderSystem.assertOnRenderThread();bridge.vanillaPass(painter); }
    public void invalidate() { RenderSystem.assertOnRenderThread();bridge.invalidate(); }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();bridge.close();runtime.close();if(instance==this) instance=null;
    }
}
