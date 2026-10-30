package com.atom.lib.layout;

import com.atom.lib.geometry.Viewport;

public interface LayoutEngine extends AutoCloseable {
    LayoutSnapshot layout(LayoutTree tree,Viewport viewport);
    @Override void close();
}
