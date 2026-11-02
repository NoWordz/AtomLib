package com.atom.lib.controls;
import java.util.Objects;
public record SliderModel(String label,String description,float value,float min,float max,float step,boolean enabled){
    public SliderModel {
        Objects.requireNonNull(label);Objects.requireNonNull(description);
        for(float v:new float[]{value,min,max,step})if(!Float.isFinite(v))throw new IllegalArgumentException("Nonfinite slider value");
        if(min>=max||!Float.isFinite(max-min)||step<=0||value<min||value>max)throw new IllegalArgumentException("Invalid slider range");
    }
}
