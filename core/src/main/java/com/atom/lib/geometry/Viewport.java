package com.atom.lib.geometry;

public record Viewport(float width,float height,float pixelsPerUnit) {
    public Viewport {
        if(!Float.isFinite(width)||!Float.isFinite(height)||!Float.isFinite(pixelsPerUnit)||width<0||height<0||pixelsPerUnit<=0)
            throw new IllegalArgumentException("Invalid viewport");
    }
    public Rect bounds(){return new Rect(0,0,width,height);}
}
