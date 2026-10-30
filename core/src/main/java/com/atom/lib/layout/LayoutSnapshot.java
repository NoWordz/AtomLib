package com.atom.lib.layout;

import java.util.List;

public interface LayoutSnapshot {
    long revision();
    LayoutBox box(NodeId id);
    List<NodeId> paintOrder();
}
