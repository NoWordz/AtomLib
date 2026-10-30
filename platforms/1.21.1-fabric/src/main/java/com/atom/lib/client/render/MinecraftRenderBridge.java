// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.RuntimeHandle;
import com.atom.lib.render.CustomPainter;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import io.github.humbleui.skija.Image;
import java.util.function.Function;

/** Client-only explicit access; Fabric client entrypoint does not initialize it. */
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
    /** Physical framebuffer coordinates; caller flushes pending DrawContext first. */
    public void draw(CustomPainter painter) {
        RenderSystem.assertOnRenderThread();
        Framebuffer target=MinecraftClient.getInstance().getFramebuffer();
        if(target.textureWidth<=0 || target.textureHeight<=0) return;
        bridge.draw(new FramebufferTarget(target,target.fbo,target.getColorAttachment(),target.textureWidth,target.textureHeight),painter);
    }
    public void drawFrame(BorrowedFramePainter painter) { RenderSystem.assertOnRenderThread();bridge.drawFrame(target(),painter); }
    public <T> T withFrameAccess(Function<BorrowedFrame,T> action) { RenderSystem.assertOnRenderThread();return bridge.withFrameAccess(target(),action); }
    /** Host-side raw context lease; never execute Minecraft rendering inside a Skija pass. */
    public <T> T withContext(Function<io.github.humbleui.skija.DirectContext,T> action) { RenderSystem.assertOnRenderThread();return bridge.withContext(action); }
    public void prepare() { RenderSystem.assertOnRenderThread();bridge.prepare(target()); }
    public Image captureFrame() { RenderSystem.assertOnRenderThread();return bridge.captureFrame(target()); }
    private static FramebufferTarget target() {
        Framebuffer target=MinecraftClient.getInstance().getFramebuffer();
        return new FramebufferTarget(target,target.fbo,target.getColorAttachment(),target.textureWidth,target.textureHeight);
    }
    public void vanillaPass(Runnable painter) { RenderSystem.assertOnRenderThread();bridge.vanillaPass(painter); }
    public void invalidate() { RenderSystem.assertOnRenderThread();bridge.invalidate(); }
    /**
     * Client shutdown: release a bridge that was actually created. Never makes
     * one, so a session that never drew does not pay for native setup at exit.
     * Must run on the render thread while the GL context is still current.
     */
    public static void releaseIfOpen() {
        RenderSystem.assertOnRenderThread();
        MinecraftRenderBridge current=instance;
        if(current==null) return;
        try { current.close(); }
        catch(RuntimeException|Error failure) {
            instance=null;
            System.getLogger("atomlib.render").log(System.Logger.Level.WARNING,
                    "AtomLib render bridge failed to close during client shutdown",failure);
        }
    }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();bridge.close();runtime.close();if(instance==this) instance=null;
    }
}
