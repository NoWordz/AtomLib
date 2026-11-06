package com.atom.lib.client.render.item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.atom.lib.client.render.*;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
/** Owned by MinecraftRenderBridge; every frame uses the native GUI item renderer. */
public final class MinecraftItemIcons implements AutoCloseable {
    private static boolean reloadHook;
    private final ItemIconAtlas atlas;
    public MinecraftItemIcons(SkijaRenderBridge bridge){
        RenderSystem.assertOnRenderThread();SkijaRenderBridge.assertOutsideSkijaPass();atlas=new ItemIconAtlas(bridge);
        if(!reloadHook){var manager=(ReloadableResourceManager)Minecraft.getInstance().getResourceManager();manager.registerReloadListener((ResourceManagerReloadListener)resources->MinecraftRenderBridge.invalidateIfOpen());reloadHook=true;}
    }
    public static int pixels(float logicalSize,float pixelsPerUnit){return ItemIconAtlas.pixels(logicalSize,pixelsPerUnit);}
    public ItemIconFrame prepare(List<ItemIconRequest> requests,long frameId){
        RenderSystem.assertOnRenderThread();SkijaRenderBridge.assertOutsideSkijaPass();requests=List.copyOf(requests);
        var stacks=new LinkedHashMap<String,ItemStack>();var entries=new ArrayList<ItemIconAtlas.Entry>();
        for(var request:requests){var stack=request.stack();if(stacks.putIfAbsent(request.key(),stack)!=null)throw new IllegalArgumentException("Duplicate item icon key: "+request.key());entries.add(new ItemIconAtlas.Entry(request.key(),request.pixelSize(),stack.isEmpty()));}
        if(entries.stream().anyMatch(entry->!entry.empty()))VanillaItemPass.flushHost();
        return atlas.prepare(entries,frameId,(tiles,width,height)->{
            try(var pass=new VanillaItemPass(width,height)){for(var tile:tiles)if(!tile.empty())try{pass.draw(stacks.get(tile.key()),tile.x(),tile.y(),tile.size());}catch(RuntimeException|Error e){throw new IllegalStateException("Native item icon failed [1.21.1-neoforge] key="+tile.key(),e);}}
        });
    }
    public void invalidate(){RenderSystem.assertOnRenderThread();atlas.invalidate();}
    public void close(){RenderSystem.assertOnRenderThread();atlas.close();}
}
