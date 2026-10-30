package com.atom.lib.layout;

import java.util.*;

public record LayoutNode(NodeId id,LayoutStyle style,List<LayoutNode> children,LeafMeasure measure,long measureRevision) {
    public LayoutNode {
        Objects.requireNonNull(id);Objects.requireNonNull(style);children=List.copyOf(children);
        if(measure!=null&&!children.isEmpty())throw new IllegalArgumentException("Measured node must be a leaf: "+id);
    }
    public static LayoutNode leaf(String id,LayoutStyle style,LeafMeasure measure,long revision){return new LayoutNode(new NodeId(id),style,List.of(),measure,revision);}
}
