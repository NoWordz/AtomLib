package com.atom.lib.scroll;

public final class ScrollView {
    private float offset,max;
    public float offset(){return offset;}
    public float maxOffset(){return max;}
    public void setExtent(float content,float viewport){if(!Float.isFinite(content)||!Float.isFinite(viewport)||content<0||viewport<0)throw new IllegalArgumentException("Invalid scroll extent");max=Math.max(0,content-viewport);offset=Math.min(max,offset);}
    /** Returns unconsumed logical distance for the parent scroll owner. */
    public float scroll(float distance){if(!Float.isFinite(distance))throw new IllegalArgumentException("Invalid scroll distance");float before=offset;offset=Math.max(0,Math.min(max,offset+distance));return distance-(offset-before);}
    public void reveal(float start,float end,float viewport){if(start<offset)scroll(start-offset);else if(end>offset+viewport)scroll(end-offset-viewport);}
}
