package com.atom.lib.controls;
import com.atom.lib.text.TextService;
import java.util.Objects;
public record ControlContext(TextService text,ControlSkin skin){
    public ControlContext{Objects.requireNonNull(text);Objects.requireNonNull(skin);}
}
