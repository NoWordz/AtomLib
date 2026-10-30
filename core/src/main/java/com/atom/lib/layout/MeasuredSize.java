package com.atom.lib.layout;

public record MeasuredSize(float width,float height,float baseline) {
    public MeasuredSize { if(!Float.isFinite(width)||!Float.isFinite(height)||!Float.isFinite(baseline)||width<0||height<0||baseline<0)throw new IllegalArgumentException("Invalid measurement"); }
}
