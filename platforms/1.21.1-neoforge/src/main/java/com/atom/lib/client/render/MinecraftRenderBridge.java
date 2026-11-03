// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.RuntimeHandle;
import com.atom.lib.render.CustomPainter;
import com.atom.lib.geometry.Rect;
import com.atom.lib.client.render.backdrop.*;
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
    private com.atom.lib.client.render.item.MinecraftItemIcons itemIcons;
    public com.atom.lib.client.render.item.MinecraftItemIcons itemIcons(){RenderSystem.assertOnRenderThread();SkijaRenderBridge.assertOutsideSkijaPass();if(itemIcons==null)itemIcons=new com.atom.lib.client.render.item.MinecraftItemIcons(bridge);return itemIcons;}
    public static void invalidateIfOpen(){RenderSystem.assertOnRenderThread();if(instance!=null)instance.invalidate();}
    private BackdropRenderer backdrop;
    private BackdropRenderer backdrop(){SkijaRenderBridge.assertOutsideSkijaPass();if(backdrop==null)backdrop=new BackdropRenderer();return backdrop;}
    /** GUI-unit convenience; flush host batches before calling. Padding and physical rounding belong to Lib. */
    public BackdropOutcome drawBackdrop(Rect bounds,float pixelsPerUnit,BackdropStyle style,long frameId){
        return drawBackdrop(bounds,pixelsPerUnit,style,frameId,1);
    }
    public BackdropOutcome drawBackdrop(Rect bounds,float pixelsPerUnit,BackdropStyle style,long frameId,float maxLuminance){
        RenderSystem.assertOnRenderThread();if(!Float.isFinite(pixelsPerUnit)||pixelsPerUnit<=0)throw new IllegalArgumentException("Invalid pixel density");
        float x=(float)Math.floor(bounds.x()*pixelsPerUnit),y=(float)Math.floor(bounds.y()*pixelsPerUnit);
        var physical=new Rect(x,y,(float)Math.ceil(bounds.right()*pixelsPerUnit)-x,(float)Math.ceil(bounds.bottom()*pixelsPerUnit)-y);
        var scaled=new BackdropStyle(style.blurRadius()*pixelsPerUnit,style.cornerRadius()*pixelsPerUnit,style.opacity(),style.fallbackColor());
        var target=target();return backdrop().render(target,target,physical,scaled,frameId,maxLuminance);
    }
    /** Explicit source and destination supports a host's own HUD/cache stage. */
    public BackdropOutcome drawBackdrop(FramebufferTarget source,FramebufferTarget destination,Rect physicalBounds,BackdropStyle style,long frameId){RenderSystem.assertOnRenderThread();return backdrop().render(source,destination,physicalBounds,style,frameId);}
    public BackdropOutcome drawBackdrop(FramebufferTarget source,FramebufferTarget destination,Rect physicalBounds,BackdropStyle style,long frameId,float maxLuminance){RenderSystem.assertOnRenderThread();return backdrop().render(source,destination,physicalBounds,style,frameId,maxLuminance);}
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
    public void invalidate() { RenderSystem.assertOnRenderThread();SkijaRenderBridge.assertOutsideSkijaPass();if(itemIcons!=null)itemIcons.invalidate();if(backdrop!=null)backdrop.invalidate();bridge.invalidate(); }
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
        RenderSystem.assertOnRenderThread();SkijaRenderBridge.assertOutsideSkijaPass();try{if(itemIcons!=null){itemIcons.close();itemIcons=null;}}finally{try{if(backdrop!=null){backdrop.close();backdrop=null;}}finally{try{bridge.close();}finally{runtime.close();if(instance==this) instance=null;}}}
    }
}
