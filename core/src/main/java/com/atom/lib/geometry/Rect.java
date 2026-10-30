package com.atom.lib.geometry;

public record Rect(float x, float y, float width, float height) {
    public Rect {
        if (!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(width)||!Float.isFinite(height)||width<0||height<0)
            throw new IllegalArgumentException("Rectangle must be finite with nonnegative size");
    }
    public float right(){return x+width;}
    public float bottom(){return y+height;}
    public boolean contains(float px,float py){return px>=x&&py>=y&&px<right()&&py<bottom();}
    public Rect intersect(Rect other){float nx=Math.max(x,other.x),ny=Math.max(y,other.y);return new Rect(nx,ny,Math.max(0,Math.min(right(),other.right())-nx),Math.max(0,Math.min(bottom(),other.bottom())-ny));}
    public Rect translate(float dx,float dy){return new Rect(x+dx,y+dy,width,height);}
    public Rect inset(float amount){return new Rect(x+amount,y+amount,Math.max(0,width-2*amount),Math.max(0,height-2*amount));}
}
