// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.layout;
import com.atom.lib.geometry.*;
import java.util.*;
/** Single vertical viewport. Natural measurement is kept separate from its displayed clip. */
public final class ScrollViewport {
    private Rect bounds=new Rect(0,0,0,0);private float offset,extent;private Set<NodeId> descendants=Set.of();
    record State(Rect bounds,float extent,float offset,Set<NodeId> descendants){}
    /** UiSession publishes the state carried by exactly the frame that painted successfully. */
    public void publish(PresentationFrame frame){var s=frame.scrollState(this);if(s!=null){bounds=s.bounds();extent=s.extent();offset=s.offset();descendants=s.descendants();}}
    public Rect thumb(PresentationFrame frame){var s=frame.scrollState(this);return s==null?thumb():thumb(s.bounds(),s.extent(),s.offset());}
    private boolean hovered;
    public void hover(float x,float y){var t=thumb();hovered=t.height()>0&&new Rect(t.x()-3,bounds.y(),t.width()+6,bounds.height()).contains(x,y);}
    public boolean scrollbarVisible(){return offset>0||hovered;}
    public float offset(){return offset;}
    public float extent(){return extent;}
    public Rect bounds(){return bounds;}
    public boolean contains(NodeId id){return descendants.contains(id);}
    public boolean contains(PresentationFrame frame,NodeId id){var state=frame.scrollState(this);return state!=null&&state.descendants().contains(id);}
    public boolean scrollBy(float delta){if(!Float.isFinite(delta))throw new IllegalArgumentException("Nonfinite scroll");float next=Math.max(0,Math.min(Math.max(0,extent-bounds.height()),offset+delta));boolean changed=next!=offset;offset=next;return changed;}
    public boolean ensureVisible(Rect row){if(row.y()<bounds.y())return scrollBy(row.y()-bounds.y());if(row.bottom()>bounds.bottom())return scrollBy(row.bottom()-bounds.bottom());return false;}
    public PresentationFrame present(LayoutTree tree,LayoutSnapshot natural,Viewport screen,Rect bounds,float scale){
        return present(tree,natural,screen,bounds,scale,Map.of());
    }
    public PresentationFrame present(LayoutTree tree,LayoutSnapshot natural,Viewport screen,Rect bounds,float scale,Map<NodeId,EntrancePose> entrance){
        Objects.requireNonNull(bounds);var rootPose=entrance.get(tree.root().id());
        var displayedBounds=rootPose==null?bounds:bounds.translate(rootPose.x()*scale,rootPose.y()*scale).intersect(screen.bounds());
        float extent=natural.box(tree.root().id()).border().height()*scale;float offset=Math.max(0,Math.min(this.offset,Math.max(0,extent-displayedBounds.height())));var state=new State(displayedBounds,extent,offset,Set.copyOf(natural.paintOrder()));
        var limited=PresentationFrame.constrainRootHeight(natural,tree.root().id(),bounds.height()/scale);
        var frame=PresentationFrame.create(tree,limited,new Viewport(screen.width()/scale,screen.height()/scale,screen.pixelsPerUnit()*scale),Map.of(tree.root().id(),new PresentationFrame.Offset(0,offset/scale)),new PresentationFrame.Offset(bounds.x()/scale,bounds.y()/scale),entrance);
        return frame.scaled(scale).withScroll(this,state);
    }
    public Rect thumb(){return thumb(bounds,extent,offset);}
    private Rect thumb(Rect bounds,float extent,float offset){float h=extent<=bounds.height()?0:Math.min(bounds.height(),Math.max(24,bounds.height()*bounds.height()/extent));float y=extent<=bounds.height()?bounds.y():bounds.y()+offset/(extent-bounds.height())*(bounds.height()-h);return new Rect(bounds.right()-6,y,4,h);}
    public boolean dragThumb(float delta){
        if(!Float.isFinite(delta))throw new IllegalArgumentException("Nonfinite thumb drag");
        // The minimum thumb size changes the ratio between its travel and content travel.
        float travel=bounds.height()-thumb().height();
        return travel>0&&scrollBy(delta*Math.max(0,extent-bounds.height())/travel);
    }
}
