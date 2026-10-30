package com.atom.lib.layout;

import com.atom.lib.geometry.Rect;
import java.util.Objects;

public record LayoutBox(Rect border,Rect content,Rect hit,Rect clip,NodeId parent,NodeId scrollOwner) {
    public LayoutBox { Objects.requireNonNull(border);Objects.requireNonNull(content);Objects.requireNonNull(hit);Objects.requireNonNull(clip); }
}
