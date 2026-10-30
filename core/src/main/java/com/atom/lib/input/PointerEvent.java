package com.atom.lib.input;

import java.util.Objects;

public record PointerEvent(Type type,float x,float y,int button) {
    public enum Type { PRESS, MOVE, RELEASE, CANCEL }
    public PointerEvent { Objects.requireNonNull(type);if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("Invalid pointer coordinates"); }
}
