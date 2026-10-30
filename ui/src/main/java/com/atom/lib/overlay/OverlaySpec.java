package com.atom.lib.overlay;

import com.atom.lib.component.UiElement;
import com.atom.lib.layout.NodeId;
import java.util.Objects;

/** Immutable description. The content root ID identifies this overlay in its view. */
public record OverlaySpec(UiElement content, OverlayPlacement placement, NodeId anchor,
                          OverlaySize size, boolean modal) {
    public OverlaySpec {
        Objects.requireNonNull(content);Objects.requireNonNull(placement);Objects.requireNonNull(size);
        if(placement!=OverlayPlacement.CENTER&&anchor==null)throw new IllegalArgumentException("Anchored placement requires a NodeId");
    }
    public NodeId id(){return content.id();}
    public static OverlaySpec centered(UiElement content){return new OverlaySpec(content,OverlayPlacement.CENTER,null,OverlaySize.STANDARD,true);}
    public static OverlaySpec anchored(UiElement content,NodeId anchor,OverlayPlacement placement){return new OverlaySpec(content,placement,anchor,OverlaySize.ANCHOR,placement!=OverlayPlacement.TOOLTIP);}
    public OverlaySpec withModal(boolean modal){return new OverlaySpec(content,placement,anchor,size,modal);}
    public OverlaySpec withSize(OverlaySize size){return new OverlaySpec(content,placement,anchor,size,modal);}
}
