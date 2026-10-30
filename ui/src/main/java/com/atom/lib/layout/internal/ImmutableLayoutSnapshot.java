package com.atom.lib.layout.internal;

import com.atom.lib.layout.*;
import java.util.*;

final class ImmutableLayoutSnapshot implements LayoutSnapshot {
    private final long revision;
    private final Map<NodeId,LayoutBox> boxes;
    private final List<NodeId> order;
    ImmutableLayoutSnapshot(long revision,Map<NodeId,LayoutBox> boxes,List<NodeId> order){this.revision=revision;this.boxes=Map.copyOf(boxes);this.order=List.copyOf(order);}
    @Override public long revision(){return revision;}
    @Override public LayoutBox box(NodeId id){var box=boxes.get(id);if(box==null)throw new IllegalArgumentException("Unknown node ID: "+id);return box;}
    @Override public List<NodeId> paintOrder(){return order;}
}
