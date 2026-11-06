package com.atom.lib.layout;

import com.atom.lib.geometry.*;
import java.util.*;

/** Immutable displayed geometry, shared by painting, clipping and hit testing. */
public final class PresentationFrame implements LayoutSnapshot {
    public record Offset(float x,float y){public Offset{if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("Nonfinite translation");}}
    private final long revision;
    private final Map<NodeId,LayoutBox> boxes;
    private final List<NodeId> order;
    private final java.util.concurrent.ConcurrentMap<Float,PresentationFrame> paintingFrames=new java.util.concurrent.ConcurrentHashMap<>();
    private Map<NodeId,LayoutBox> paintingBoxes;
    private Map<NodeId,Float> scales=Map.of();
    private Map<NodeId,Float> opacity=Map.of();
    private Set<NodeId> entering=Set.of();
    private Map<NodeId,Offset> entranceOffsets=Map.of();
    private Map<ScrollViewport,ScrollViewport.State> scrollStates=Map.of();
    PresentationFrame withScroll(ScrollViewport viewport,ScrollViewport.State state){scrollStates=Map.of(viewport,state);paintingFrames.clear();return this;}
    ScrollViewport.State scrollState(ScrollViewport viewport){return scrollStates.get(viewport);}
    private PresentationFrame(long revision,Map<NodeId,LayoutBox> boxes,List<NodeId> order){this.revision=revision;this.boxes=Map.copyOf(boxes);this.paintingBoxes=this.boxes;this.order=List.copyOf(order);}
    public static PresentationFrame create(LayoutTree tree,LayoutSnapshot snapshot,Viewport viewport,Map<NodeId,Offset> scroll,Offset translation){
        return create(tree,snapshot,viewport,scroll,translation,Map.of());
    }
    /** Applies either a page root or disjoint item roots; clipping and hits use displayed geometry. */
    public static PresentationFrame create(LayoutTree tree,LayoutSnapshot snapshot,Viewport viewport,Map<NodeId,Offset> scroll,Offset translation,Map<NodeId,EntrancePose> entrance){
        var boxes=new LinkedHashMap<NodeId,LayoutBox>();var order=new ArrayList<NodeId>();
        var opacity=new HashMap<NodeId,Float>();var entering=new HashSet<NodeId>();var offsets=new HashMap<NodeId,Offset>();
        collect(tree.root(),snapshot,scroll,translation.x(),translation.y(),viewport.bounds(),boxes,order,entrance,opacity,entering,offsets,1,false,new Offset(0,0));
        var result=new PresentationFrame(snapshot.revision(),boxes,order);result.opacity=Map.copyOf(opacity);result.entering=Set.copyOf(entering);result.entranceOffsets=Map.copyOf(offsets);return result;
    }
    private static void collect(LayoutNode node,LayoutSnapshot snapshot,Map<NodeId,Offset> scroll,float dx,float dy,Rect inheritedClip,Map<NodeId,LayoutBox> boxes,List<NodeId> order,Map<NodeId,EntrancePose> entrance,Map<NodeId,Float> opacity,Set<NodeId> entering,Map<NodeId,Offset> entranceOffsets,float inheritedOpacity,boolean insideEntrance,Offset entranceOffset){
        if(node.style().hidden())return;
        var pose=entrance.get(node.id());
        if(pose!=null){if(insideEntrance)throw new IllegalArgumentException("Nested entrance roots");dx+=pose.x();dy+=pose.y();inheritedOpacity*=pose.opacity();insideEntrance=true;entranceOffset=new Offset(pose.x(),pose.y());}
        var raw=snapshot.box(node.id());var border=raw.border().translate(dx,dy);
        var clip=node.style().clip()?inheritedClip.intersect(border):inheritedClip;
        boxes.put(node.id(),new LayoutBox(border,raw.content().translate(dx,dy),raw.hit().translate(dx,dy),clip,raw.parent(),raw.scrollOwner()));order.add(node.id());if(insideEntrance){opacity.put(node.id(),inheritedOpacity);entering.add(node.id());entranceOffsets.put(node.id(),entranceOffset);}
        var offset=scroll.getOrDefault(node.id(),new Offset(0,0));
        for(var child:node.children())collect(child,snapshot,scroll,dx-offset.x(),dy-offset.y(),clip,boxes,order,entrance,opacity,entering,entranceOffsets,inheritedOpacity,insideEntrance,entranceOffset);
    }
    @Override public long revision(){return revision;}
    @Override public LayoutBox box(NodeId id){var box=boxes.get(id);if(box==null)throw new IllegalArgumentException("Unknown node ID: "+id);return box;}
    @Override public List<NodeId> paintOrder(){return order;}
    public boolean contains(NodeId id){return boxes.containsKey(id);}
    /** Uniform displayed transform. Hit boxes and clips use exactly the rendered coordinates. */
    public PresentationFrame scaled(float scale){
        if(!Float.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Invalid display scale");
        var next=new LinkedHashMap<NodeId,LayoutBox>();var metadata=new HashMap<NodeId,Float>();
        for(var id:order){var b=box(id);next.put(id,new LayoutBox(scale(b.border(),scale),scale(b.content(),scale),scale(b.hit(),scale),scale(b.clip(),scale),b.parent(),b.scrollOwner()));metadata.put(id,renderScale(id)*scale);}
        var offsets=new HashMap<NodeId,Offset>();entranceOffsets.forEach((id,o)->offsets.put(id,new Offset(o.x()*scale,o.y()*scale)));
        var result=new PresentationFrame(revision,next,order);result.scales=Map.copyOf(metadata);result.paintingBoxes=paintingBoxes;result.opacity=opacity;result.entering=entering;result.entranceOffsets=Map.copyOf(offsets);result.scrollStates=scaleScrollStates(scrollStates,scale);return result;
    }
    private static Rect scale(Rect r,float s){return new Rect(r.x()*s,r.y()*s,r.width()*s,r.height()*s);}
    private static Map<ScrollViewport,ScrollViewport.State> scaleScrollStates(Map<ScrollViewport,ScrollViewport.State> states,float factor){
        var result=new HashMap<ScrollViewport,ScrollViewport.State>();
        states.forEach((viewport,state)->result.put(viewport,new ScrollViewport.State(scale(state.bounds(),factor),state.extent()*factor,state.offset()*factor,state.descendants())));
        return Map.copyOf(result);
    }
    public float renderScale(NodeId id){return scales.getOrDefault(id,1f);}
    public float opacity(NodeId id){return opacity.getOrDefault(id,1f);}
    public boolean hasEntrance(NodeId id){return entering.contains(id);}
    public Rect unanimatedBorder(NodeId id){var o=entranceOffsets.get(id);return o==null?box(id).border():box(id).border().translate(-o.x(),-o.y());}
    /** Used only while the painter applies the matching Canvas scale. */
    public PresentationFrame forPainting(NodeId id){
        float scale=renderScale(id);if(scale==1)return this;
        var cached=paintingFrames.get(scale);if(cached!=null)return cached;
        var next=new LinkedHashMap<NodeId,LayoutBox>();var metadata=new HashMap<NodeId,Float>();
        for(var item:order){
            var b=box(item);
            // Inverting rounded display floats can turn a 40u text slot into
            // 39.999996u and unexpectedly wrap it. Retain the measured geometry.
            next.put(item,renderScale(item)==scale?paintingBoxes.get(item):new LayoutBox(scale(b.border(),1/scale),scale(b.content(),1/scale),scale(b.hit(),1/scale),scale(b.clip(),1/scale),b.parent(),b.scrollOwner()));
            metadata.put(item,renderScale(item)/scale);
        }
        var offsets=new HashMap<NodeId,Offset>();entranceOffsets.forEach((item,o)->offsets.put(item,new Offset(o.x()/scale,o.y()/scale)));
        var result=new PresentationFrame(revision,next,order);result.scales=Map.copyOf(metadata);result.paintingBoxes=paintingBoxes;result.opacity=opacity;result.entering=entering;result.entranceOffsets=Map.copyOf(offsets);result.scrollStates=scaleScrollStates(scrollStates,1/scale);
        var previous=paintingFrames.putIfAbsent(scale,result);return previous==null?result:previous;
    }
    /** Layers are already positioned and clipped; later layers paint and hit above earlier layers. */
    public static PresentationFrame compose(List<PresentationFrame> layers){
        var boxes=new LinkedHashMap<NodeId,LayoutBox>();var paintingBoxes=new LinkedHashMap<NodeId,LayoutBox>();var order=new ArrayList<NodeId>();var scales=new HashMap<NodeId,Float>();var opacity=new HashMap<NodeId,Float>();var entering=new HashSet<NodeId>();var offsets=new HashMap<NodeId,Offset>();var scrollStates=new HashMap<ScrollViewport,ScrollViewport.State>();long revision=1;
        for(var layer:layers){
            for(var entry:layer.scrollStates.entrySet())if(scrollStates.putIfAbsent(entry.getKey(),entry.getValue())!=null)throw new IllegalArgumentException("Duplicate scroll viewport layer");
            revision=31*revision+layer.revision();
            for(var id:layer.order){if(boxes.putIfAbsent(id,layer.box(id))!=null)throw new IllegalArgumentException("Duplicate displayed ID: "+id);paintingBoxes.put(id,layer.paintingBoxes.get(id));order.add(id);scales.put(id,layer.renderScale(id));if(layer.hasEntrance(id)){opacity.put(id,layer.opacity(id));entering.add(id);offsets.put(id,layer.entranceOffsets.get(id));}}
        }
        var result=new PresentationFrame(revision,boxes,order);result.scales=Map.copyOf(scales);result.opacity=Map.copyOf(opacity);result.entering=Set.copyOf(entering);result.entranceOffsets=Map.copyOf(offsets);result.paintingBoxes=Map.copyOf(paintingBoxes);result.scrollStates=Map.copyOf(scrollStates);return result;
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
