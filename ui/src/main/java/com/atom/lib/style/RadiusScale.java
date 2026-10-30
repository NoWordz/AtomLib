package com.atom.lib.style;
public record RadiusScale(float card,float control) {
    public RadiusScale { if(!Float.isFinite(card)||!Float.isFinite(control)||card<0||control<0)throw new IllegalArgumentException("Invalid radius scale"); }
}
