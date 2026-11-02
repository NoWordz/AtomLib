package com.atom.lib.controls;
public record ControlVisual(boolean focused,float hover,float press,float position,float opacity) {
    public ControlVisual{for(float v:new float[]{hover,press,position,opacity})if(!Float.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("Visual weights must be in 0..1");}
}
