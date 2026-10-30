package com.atom.lib.input;

public interface PointerTarget {
    default boolean enabled(){return true;}
    boolean press(PointerEvent event);
    void drag(PointerEvent event);
    void release(PointerEvent event);
    void cancel();
}
