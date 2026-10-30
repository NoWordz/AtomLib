package com.atom.lib.style;
public record SpacingScale(float small,float medium,float large,float controlHeight) {
    public SpacingScale { for(float v:new float[]{small,medium,large,controlHeight})if(!Float.isFinite(v)||v<0)throw new IllegalArgumentException("Invalid spacing scale");if(controlHeight<=0)throw new IllegalArgumentException("Invalid control height"); }
}
