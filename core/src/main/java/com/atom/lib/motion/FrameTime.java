package com.atom.lib.motion;

public record FrameTime(long frameId,long nowNanos,double deltaSeconds) {
    public FrameTime {
        if(!Double.isFinite(deltaSeconds)||deltaSeconds<0)throw new IllegalArgumentException("Invalid frame delta");
    }
}
