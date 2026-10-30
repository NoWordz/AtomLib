package com.atom.lib.probe;

import java.util.*;

abstract class ProbeBackend implements AutoCloseable {
    final List<Node> nodes = new ArrayList<>();
    boolean closed;
    static ProbeBackend open() { return System.getProperty("probe.backend", "java").equals("java") ? new JavaBackend() : new NativeBackend(); }
    final Node node() {
        if (closed) throw new IllegalStateException("Probe is closed");
        Node n=create(); nodes.add(n); return n;
    }
    abstract Node create();
    abstract void layout(Node root,float width,float height,float scale);
    abstract class Node {
        abstract Node row(); abstract Node wrap(); abstract Node padding(float n);
        abstract Node width(float n); abstract Node height(float n);
        abstract Node percentWidth(float n); abstract Node grow(float n); abstract Node shrink(float n);
        abstract Node basis(float n); abstract Node minWidth(float n); abstract Node maxWidth(float n);
        abstract Node marginRight(float n); abstract Node hidden();
        abstract Node measure(Measure m); abstract void dirty();
        abstract Node add(Node n); abstract void remove(Node n);
        abstract float x(); abstract float y(); abstract float width(); abstract float height();
    }
    interface Measure { float[] measure(float w,String wm,float h,String hm); }
    int ownedNodes() { return nodes.size(); }
    @Override public abstract void close();
}
