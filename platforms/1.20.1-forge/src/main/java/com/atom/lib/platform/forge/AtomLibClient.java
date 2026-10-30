package com.atom.lib.platform.forge;
import com.atom.lib.client.render.MinecraftRenderBridge;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
/**
 * Client-only shutdown wiring. Loaded only through the dist guard in
 * {@link AtomLibMod}, so a dedicated server never touches it.
 */
final class AtomLibClient {
    private AtomLibClient() { }
    static void init() {
        // Fired from Minecraft#stop() on the render thread, before the window
        // and its GL context are destroyed. Only an existing bridge is released.
        MinecraftForge.EVENT_BUS.addListener(AtomLibClient::onShuttingDown);
    }
    private static void onShuttingDown(GameShuttingDownEvent event) {
        MinecraftRenderBridge.releaseIfOpen();
    }
}
