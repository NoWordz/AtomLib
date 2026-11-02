package com.atom.lib.controls;
import java.util.Objects;
public record ToggleModel(String label,String description,boolean checked,boolean enabled){
    public ToggleModel{Objects.requireNonNull(label);Objects.requireNonNull(description);}
}
