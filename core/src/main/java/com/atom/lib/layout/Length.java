package com.atom.lib.layout;

import java.util.Objects;

public record Length(float value,Unit unit) {
    public enum Unit { AUTO, POINTS, PERCENT }
    public Length { Objects.requireNonNull(unit);if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid length"); }
    public static Length auto(){return new Length(0,Unit.AUTO);}
    public static Length points(float n){return new Length(n,Unit.POINTS);}
    public static Length percent(float n){return new Length(n,Unit.PERCENT);}
}
