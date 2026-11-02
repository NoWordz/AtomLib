package com.atom.lib.controls;
import java.util.Objects;
public record ConfirmationModel(String title,String message,String cancelLabel,String confirmLabel){
    public ConfirmationModel{Objects.requireNonNull(title);Objects.requireNonNull(message);Objects.requireNonNull(cancelLabel);Objects.requireNonNull(confirmLabel);}
}
