package com.atom.lib.layout;

import java.util.*;

public final class LayoutTree {
    private final LayoutNode root;
    private final Map<NodeId,LayoutNode> nodes;
    public LayoutTree(LayoutNode root){this.root=Objects.requireNonNull(root);var map=new LinkedHashMap<NodeId,LayoutNode>();collect(root,map);nodes=Collections.unmodifiableMap(map);}
    private static void collect(LayoutNode node,Map<NodeId,LayoutNode> map){if(map.putIfAbsent(node.id(),node)!=null)throw new IllegalArgumentException("Duplicate node ID: "+node.id());for(var child:node.children())collect(child,map);}
    public LayoutNode root(){return root;}
    public LayoutNode node(NodeId id){var n=nodes.get(id);if(n==null)throw new IllegalArgumentException("Unknown node ID: "+id);return n;}
    public Collection<LayoutNode> nodes(){return nodes.values();}
}
