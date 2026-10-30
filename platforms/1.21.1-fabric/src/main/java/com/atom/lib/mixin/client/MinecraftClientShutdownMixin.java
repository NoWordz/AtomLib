package com.atom.lib.mixin.client;

import com.atom.lib.client.render.MinecraftRenderBridge;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric has no loader event for "the client is closing", so the shared bridge
 * is released here. HEAD of close() runs on the render thread before the
 * Window (and with it the GL context) is destroyed.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientShutdownMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void atomlib$releaseRenderBridge(CallbackInfo info) {
        MinecraftRenderBridge.releaseIfOpen();
    }
}
