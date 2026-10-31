package com.atom.lib.layout;

import java.util.Objects;

public record LayoutStyle(Direction direction,Length width,Length height,
                          float minWidth,float minHeight,float maxWidth,float maxHeight,
                          float grow,float shrink,Length basis,Edges padding,float gap,
                          boolean wrap,boolean hidden,boolean clip,boolean scroll,
                          Edges margin,float aspectRatio,Align align,Justify justify) {
    public enum Direction { ROW, COLUMN }
    /** Cross-axis alignment of children. Defaults to STRETCH, matching Yoga's own default. */
    public enum Align { FLEX_START, CENTER, FLEX_END, STRETCH }
    /** Main-axis distribution of children. Defaults to FLEX_START, matching Yoga's own default. */
    public enum Justify { FLEX_START, CENTER, FLEX_END, SPACE_BETWEEN, SPACE_AROUND }
    public LayoutStyle {
        Objects.requireNonNull(direction);Objects.requireNonNull(width);Objects.requireNonNull(height);Objects.requireNonNull(basis);
        Objects.requireNonNull(padding);Objects.requireNonNull(margin);Objects.requireNonNull(align);Objects.requireNonNull(justify);
        for(float v:new float[]{minWidth,minHeight,grow,shrink,gap,padding.top(),padding.right(),padding.bottom(),padding.left()})if(!Float.isFinite(v)||v<0)throw new IllegalArgumentException("Invalid layout value");
        if(!Float.isFinite(aspectRatio)||aspectRatio<0)throw new IllegalArgumentException("Invalid aspect ratio");
        if(Float.isNaN(maxWidth)||Float.isNaN(maxHeight)||maxWidth<minWidth||maxHeight<minHeight)throw new IllegalArgumentException("Invalid size range");
    }
    public static LayoutStyle column(){return new LayoutStyle(Direction.COLUMN,Length.auto(),Length.auto(),0,0,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,0,1,Length.auto(),Edges.ZERO,0,false,false,false,false,Edges.ZERO,0,Align.STRETCH,Justify.FLEX_START);}
    public static LayoutStyle row(){return column().direction(Direction.ROW);}
    public LayoutStyle direction(Direction d){return new LayoutStyle(d,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,padding,gap,wrap,hidden,clip,scroll,margin,aspectRatio,align,justify);}
    public LayoutStyle size(Length w,Length h){return new LayoutStyle(direction,w,h,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,padding,gap,wrap,hidden,clip,scroll,margin,aspectRatio,align,justify);}
    public LayoutStyle limits(float minW,float minH,float maxW,float maxH){return new LayoutStyle(direction,width,height,minW,minH,maxW,maxH,grow,shrink,basis,padding,gap,wrap,hidden,clip,scroll,margin,aspectRatio,align,justify);}
    public LayoutStyle flex(float g,float s,Length b){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,g,s,b,padding,gap,wrap,hidden,clip,scroll,margin,aspectRatio,align,justify);}
    /** Uniform padding sugar: all four edges receive the same value. */
    public LayoutStyle padding(float p){return padding(Edges.uniform(p));}
    public LayoutStyle padding(Edges edges){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,edges,gap,wrap,hidden,clip,scroll,margin,aspectRatio,align,justify);}
    public LayoutStyle spacing(float p,float g){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,Edges.uniform(p),g,wrap,hidden,clip,scroll,margin,aspectRatio,align,justify);}
    /** Uniform margin sugar: all four edges receive the same value. */
    public LayoutStyle margin(float m){return margin(Edges.uniform(m));}
    public LayoutStyle margin(Edges edges){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,padding,gap,wrap,hidden,clip,scroll,edges,aspectRatio,align,justify);}
    /** Width/height ratio used by Yoga when exactly one dimension is definite; 0 disables it. */
    public LayoutStyle aspectRatio(float ratio){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,padding,gap,wrap,hidden,clip,scroll,margin,ratio,align,justify);}
    public LayoutStyle align(Align a){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,padding,gap,wrap,hidden,clip,scroll,margin,aspectRatio,a,justify);}
    public LayoutStyle justify(Justify j){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,padding,gap,wrap,hidden,clip,scroll,margin,aspectRatio,align,j);}
    public LayoutStyle behavior(boolean w,boolean h,boolean c,boolean s){return new LayoutStyle(direction,width,height,minWidth,minHeight,maxWidth,maxHeight,grow,shrink,basis,padding,gap,w,h,c,s,margin,aspectRatio,align,justify);}
}
