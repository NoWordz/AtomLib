package com.atom.lib.component;

public record SliderSpec(float min,float max,float step) {
    public SliderSpec { if(!Float.isFinite(min)||!Float.isFinite(max)||!Float.isFinite(step)||max<=min||step<=0||!Float.isFinite(max-min))throw new IllegalArgumentException("Invalid slider range or step"); }
    public float clamp(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite slider value");return Math.max(min,Math.min(max,value));}
    public float quantize(float value){if(value>=max)return max;if(value<=min)return min;return clamp(min+Math.round((value-min)/step)*step);}
}
