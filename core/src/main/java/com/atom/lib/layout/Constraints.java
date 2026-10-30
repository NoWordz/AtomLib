package com.atom.lib.layout;

import java.util.Objects;

public record Constraints(Axis width,Axis height) {
    public Constraints { Objects.requireNonNull(width);Objects.requireNonNull(height); }
    public enum Mode { EXACTLY, AT_MOST, UNDEFINED }
    public record Axis(float value,Mode mode) {
        public Axis {
            Objects.requireNonNull(mode);
            if(mode==Mode.UNDEFINED)value=Float.NaN;
            else if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid axis bound");
        }
        public static Axis exactly(float value){return new Axis(value,Mode.EXACTLY);}
        public static Axis atMost(float value){return new Axis(value,Mode.AT_MOST);}
        public static Axis undefined(){return new Axis(Float.NaN,Mode.UNDEFINED);}
        public float constrain(float natural){if(!Float.isFinite(natural)||natural<0)throw new IllegalArgumentException("Invalid measured size");return switch(mode){case EXACTLY->value;case AT_MOST->Math.min(value,natural);case UNDEFINED->natural;};}
    }
}
