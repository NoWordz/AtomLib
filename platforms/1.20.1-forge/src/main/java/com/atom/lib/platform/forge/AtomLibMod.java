package com.atom.lib.platform.forge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
/** Safe on a dedicated server: no client signatures or native initialization. */
@Mod("atomlib")
public final class AtomLibMod {
    public AtomLibMod() {
        // Dist guard before the reference: a dedicated server never resolves
        // the client-only class, so no client or native code is loaded there.
        if (FMLEnvironment.dist.isClient()) AtomLibClient.init();
    }
}
