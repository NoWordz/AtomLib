package com.atom.lib.controls;
import com.atom.lib.layout.NodeId;
import java.util.Objects;
public record ModalOptions(String title,float preferredWidth,float margin,boolean dismissOnBackdrop,NodeId initialFocus) {
    public ModalOptions { Objects.requireNonNull(title); if(!Float.isFinite(preferredWidth)||preferredWidth<=0||!Float.isFinite(margin)||margin<0)throw new IllegalArgumentException("Invalid modal dimensions"); }
}
