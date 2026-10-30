package com.atom.lib.style;
public record TypeScale(float body,float title,float caption) {
    public TypeScale { for(float v:new float[]{body,title,caption})if(!Float.isFinite(v)||v<=0)throw new IllegalArgumentException("Invalid type scale"); }
}
