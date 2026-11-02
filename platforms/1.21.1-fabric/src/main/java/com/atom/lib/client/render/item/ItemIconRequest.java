package com.atom.lib.client.render.item;
import net.minecraft.item.ItemStack;
import java.util.Objects;
/** Immutable request snapshot; count, NBT/components and item identity survive copying. */
public record ItemIconRequest(String key,ItemStack stack,int pixelSize) {
    public ItemIconRequest {if(key==null||key.isBlank()||pixelSize<=0)throw new IllegalArgumentException("Invalid item icon request");stack=Objects.requireNonNull(stack).copy();}
    /** Binary-only consumers with different development mappings can pass the native value
     * without resolving this artifact's mapped ItemStack descriptor. Never accepts an ID/texture substitute. */
    public static ItemIconRequest fromNativeStack(String key,Object value,int pixelSize){if(!(value instanceof ItemStack stack))throw new IllegalArgumentException("Expected this platform's native ItemStack");return new ItemIconRequest(key,stack,pixelSize);}
    @Override public ItemStack stack(){return stack.copy();}
}
