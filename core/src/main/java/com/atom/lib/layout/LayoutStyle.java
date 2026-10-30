package com.atom.lib.layout;

import java.util.Objects;

public record LayoutStyle(Direction direction,Length width,Length height,
                          float minWidth,float minHeight,float maxWidth,float maxHeight,
                          float grow,float shrink,Length basis,float padding,float gap,
                          boolean wrap,boolean hidden,boolean clip,boolean scroll) {
    public enum Direction { ROW, COLUMN }
    public LayoutStyle {
        Objects.requireNonNull(direction);Objects.requireNonNull(width);Objects.requireNonNull(height);Objects.requireNonNull(basis);
        for(float v:new float[]{minWidth,minHeight,grow,shrink,padding,gap})if(!Float.isFinite(v)||v<0)throw new IllegalArgumentException("Invalid layout value");
        if(Float.isNaN(maxWidth)||Float.isNaN(maxHeight)||maxWidth<minWidth||maxHeight<minHeight)throw new IllegalArgumentException("Invalid size range");
    }
    public static LayoutStyle column(){return new LayoutStyle(Direction.COLUMN,Length.auto(),Length.auto(),0,0,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,0,1,Length.auto(),0,0,false,false,false,false);}
    public static LayoutStyle row(){return column().direction(Direction.ROW);}
    public LayoutStyle direction(Direction d){return new LayoutStyle(d,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,padding,gap,wrap,hidden,clip,scroll);}
    public LayoutStyle size(Length w,Length h){return new LayoutStyle(direction,w,h,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,padding,gap,wrap,hidden,clip,scroll);}
    public LayoutStyle limits(float minW,float minH,float maxW,float maxH){return new LayoutStyle(direction,width,height,minW,minH,maxW,maxH,grow,shrink,basis,padding,gap,wrap,hidden,clip,scroll);}
    public LayoutStyle flex(float g,float s,Length b){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,g,s,b,padding,gap,wrap,hidden,clip,scroll);}
    public LayoutStyle spacing(float p,float g){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,p,g,wrap,hidden,clip,scroll);}
    public LayoutStyle behavior(boolean w,boolean h,boolean c,boolean s){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,padding,gap,w,h,c,s);}
}
