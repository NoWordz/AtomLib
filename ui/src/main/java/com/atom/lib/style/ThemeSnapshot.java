package com.atom.lib.style;
import java.util.Objects;
public record ThemeSnapshot(Palette palette,TypeScale type,SpacingScale spacing,RadiusScale radius,MotionPolicy motion) {
    public ThemeSnapshot { Objects.requireNonNull(palette);Objects.requireNonNull(type);Objects.requireNonNull(spacing);Objects.requireNonNull(radius);Objects.requireNonNull(motion); }
}
