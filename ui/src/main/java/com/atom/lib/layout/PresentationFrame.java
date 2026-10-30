package com.atom.lib.layout;

import com.atom.lib.geometry.*;
import java.util.*;

/** Immutable displayed geometry, shared by painting, clipping and hit testing. */
public final class PresentationFrame implements LayoutSnapshot {
    public record Offset(float x,float y){public Offset{if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("Nonfinite translation");}}
    private final long revision;
    private final Map<NodeId,LayoutBox> boxes;
    private final List<NodeId> order;
    private PresentationFrame(long revision,Map<NodeId,LayoutBox> boxes,List<NodeId> order){this.revision=revision;this.boxes=Map.copyOf(boxes);this.order=List.copyOf(order);}
    public static PresentationFrame create(LayoutTree tree,LayoutSnapshot snapshot,Viewport viewport,Map<NodeId,Offset> scroll,Offset translation){
        var boxes=new LinkedHashMap<NodeId,LayoutBox>();var order=new ArrayList<NodeId>();
        collect(tree.root(),snapshot,scroll,translation.x(),translation.y(),viewport.bounds(),boxes,order);
        return new PresentationFrame(snapshot.revision(),boxes,order);
    }
    private static void collect(LayoutNode node,LayoutSnapshot snapshot,Map<NodeId,Offset> scroll,float dx,float dy,Rect inheritedClip,Map<NodeId,LayoutBox> boxes,List<NodeId> order){
        if(node.style().hidden())return;
        var raw=snapshot.box(node.id());var border=raw.border().translate(dx,dy);
        var clip=node.style().clip()?inheritedClip.intersect(border):inheritedClip;
        boxes.put(node.id(),new LayoutBox(border,raw.content().translate(dx,dy),raw.hit().translate(dx,dy),clip,raw.parent(),raw.scrollOwner()));order.add(node.id());
        var offset=scroll.getOrDefault(node.id(),new Offset(0,0));
        for(var child:node.children())collect(child,snapshot,scroll,dx-offset.x(),dy-offset.y(),clip,boxes,order);
    }
    @Override public long revision(){return revision;}
    @Override public LayoutBox box(NodeId id){var box=boxes.get(id);if(box==null)throw new IllegalArgumentException("Unknown node ID: "+id);return box;}
    @Override public List<NodeId> paintOrder(){return order;}
    /** Layers are already positioned and clipped; later layers paint and hit above earlier layers. */
    public static PresentationFrame compose(List<PresentationFrame> layers){
        var boxes=new LinkedHashMap<NodeId,LayoutBox>();var order=new ArrayList<NodeId>();long revision=1;
        for(var layer:layers){
            revision=31*revision+layer.revision();
            for(var id:layer.order){if(boxes.putIfAbsent(id,layer.box(id))!=null)throw new IllegalArgumentException("Duplicate displayed ID: "+id);order.add(id);}
        }
        return new PresentationFrame(revision,boxes,order);
    }
    /** Gives a naturally measured root a scrolling viewport without remeasuring its text
     * under an AtMost height (which would discard the overflow we need to scroll). */
    public static LayoutSnapshot constrainRootHeight(LayoutSnapshot natural,NodeId root,float maxHeight){
        if(!Float.isFinite(maxHeight)||maxHeight<0)throw new IllegalArgumentException("Invalid root viewport height");
        var box=natural.box(root);if(box.parent()!=null)throw new IllegalArgumentException("Expected layout root: "+root);
        var r=box.border();var border=new Rect(r.x(),r.y(),r.width(),Math.min(r.height(),maxHeight));
        var limited=new LayoutBox(border,box.content().intersect(border),box.hit().intersect(border),box.clip().intersect(border),null,box.scrollOwner());
        return new RootViewport(natural,root,limited);
    }
    private record RootViewport(LayoutSnapshot natural,NodeId root,LayoutBox limited) implements LayoutSnapshot {
        public long revision(){return natural.revision();}
        public LayoutBox box(NodeId id){return root.equals(id)?limited:natural.box(id);}
        public List<NodeId> paintOrder(){return natural.paintOrder();}
    }
    public NodeId topAt(Set<NodeId> interactive,float x,float y){for(int i=order.size()-1;i>=0;i--){var id=order.get(i);var box=boxes.get(id);if(interactive.contains(id)&&box.hit().contains(x,y)&&box.clip().contains(x,y))return id;}return null;}
}
