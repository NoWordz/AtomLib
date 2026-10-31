package com.atom.lib.layout;

/** Immutable per-edge spacing. Finite but possibly negative: Yoga allows
 * negative margins, so sign rules live with the field that uses Edges
 * (LayoutStyle rejects negative padding, margin accepts it). */
public record Edges(float top,float right,float bottom,float left) {
    public static final Edges ZERO=new Edges(0,0,0,0);
    public Edges {if(!Float.isFinite(top)||!Float.isFinite(right)||!Float.isFinite(bottom)||!Float.isFinite(left))throw new IllegalArgumentException("Invalid edges");}
    public static Edges uniform(float value){return new Edges(value,value,value,value);}
}
