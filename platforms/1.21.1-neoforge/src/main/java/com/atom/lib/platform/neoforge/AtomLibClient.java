package com.atom.lib.platform.neoforge;
import com.atom.lib.client.render.MinecraftRenderBridge;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
/**
 * Client-only shutdown wiring. Loaded only through the dist guard in
 * {@link AtomLibMod}, so a dedicated server never touches it.
 */
final class AtomLibClient {
    private AtomLibClient() { }
    static void init() {
        // Fired from Minecraft#stop() on the render thread, before the window
        // and its GL context are destroyed. Only an existing bridge is released.
        NeoForge.EVENT_BUS.addListener(AtomLibClient::onShuttingDown);
    }
    private static void onShuttingDown(GameShuttingDownEvent event) {
        MinecraftRenderBridge.releaseIfOpen();
    }
}
