package com.atom.lib.layout.internal;

import com.atom.lib.geometry.*;
import com.atom.lib.layout.*;
import org.lwjgl.util.yoga.YGMeasureFunc;
import java.util.*;
import static org.lwjgl.util.yoga.Yoga.*;

/** One view owns one engine. Only the creating thread may access its native tree. */
public final class YogaLayoutEngine implements LayoutEngine {
    static { YogaNatives.ensure(); }
    private final Thread thread=Thread.currentThread();
    private final long config=YGConfigNew();
    private final Map<NodeId,Entry> entries=new LinkedHashMap<>();
    private LayoutTree previous;
    private Viewport viewport;
    private LayoutSnapshot snapshot;
    private long revision;
    private boolean closed;
    private boolean layoutInProgress;
    private boolean intrinsicHeight;
    private Throwable callbackFailure;

    private final class Entry {
        final long pointer=YGNodeNewWithConfig(config);
        LayoutStyle style;
        LeafMeasure measure;
        long measureRevision;
        List<NodeId> children=List.of();
        YGMeasureFunc callback;
        void setMeasure(LayoutNode node){
            if(measure==node.measure()&&measureRevision==node.measureRevision())return;
            measure=node.measure();measureRevision=node.measureRevision();
            if(measure==null){YGNodeSetMeasureFunc(pointer,(YGMeasureFunc)null);if(callback!=null){callback.free();callback=null;}YGNodeMarkDirtyAndPropogateToDescendants(pointer);}
            else {
                if(callback==null){
                    callback=YGMeasureFunc.create((p,w,wm,h,hm,out)->{
                        try {
                            var size=measure.measure(new Constraints(axis(w,wm),axis(h,hm)));
                            out.set(axis(w,wm).constrain(size.width()),axis(h,hm).constrain(size.height()));
                        } catch(Throwable failure){if(callbackFailure==null)callbackFailure=failure;out.set(0,0);}
                    });
                    YGNodeSetMeasureFunc(pointer,callback);
                }
                YGNodeMarkDirty(pointer);
            }
        }
        void dispose(){YGNodeSetMeasureFunc(pointer,(YGMeasureFunc)null);YGNodeFree(pointer);if(callback!=null)callback.free();}
    }

    @Override public LayoutSnapshot layout(LayoutTree tree,Viewport nextViewport){
        return layout(tree,nextViewport,false);
    }
    /** Overlay-only measurement: exact viewport width, natural root height constrained by its style.
     * Keeps the same native tree between calls; public layout(tree, viewport) retains its fill behavior. */
    public LayoutSnapshot layoutIntrinsicHeight(LayoutTree tree,Viewport viewport){
        return layout(tree,viewport,true);
    }
    private LayoutSnapshot layout(LayoutTree tree,Viewport nextViewport,boolean naturalHeight){
        checkOpen();Objects.requireNonNull(tree);Objects.requireNonNull(nextViewport);
        if(layoutInProgress)throw new IllegalStateException("Cannot reenter an active Yoga layout");
        for(var node:tree.nodes())if(node.style().wrap()&&node.style().gap()>0)throw new IllegalArgumentException("wrap with gap is not supported by the locked Yoga adapter: "+node.id());
        layoutInProgress=true;
        try{return layoutTransaction(tree,nextViewport,naturalHeight);}finally{layoutInProgress=false;}
    }
    private LayoutSnapshot layoutTransaction(LayoutTree tree,Viewport nextViewport,boolean naturalHeight){
        if(previous!=null&&previous.root().equals(tree.root())&&nextViewport.equals(viewport)&&intrinsicHeight==naturalHeight)return snapshot;
        Set<NodeId> replace=new HashSet<>();
        for(var node:tree.nodes()){
            var old=entries.get(node.id());
            // Yoga 1.19 retains measured dimensions when a callback is removed.
            // Recreate only nodes changing between measured/container modes.
            if(old!=null&&(old.measure==null)!=(node.measure()==null))replace.add(node.id());
        }
        // Detach before removing/reparenting so no native parent can retain a freed child.
        for(var pair:entries.entrySet()){
            LayoutNode next;
            try{next=tree.node(pair.getKey());}catch(IllegalArgumentException missing){next=null;}
            var wanted=next==null?List.<NodeId>of():next.children().stream().map(LayoutNode::id).toList();
            if(replace.contains(pair.getKey())||pair.getValue().children.stream().anyMatch(replace::contains)||!pair.getValue().children.equals(wanted)){YGNodeRemoveAllChildren(pair.getValue().pointer);pair.getValue().children=List.of();}
        }
        Set<NodeId> live=new HashSet<>();for(var node:tree.nodes())live.add(node.id());
        var iterator=entries.entrySet().iterator();
        while(iterator.hasNext()){var entry=iterator.next();if(!live.contains(entry.getKey())||replace.contains(entry.getKey())){entry.getValue().dispose();iterator.remove();}}
        for(var node:tree.nodes())entries.computeIfAbsent(node.id(),id->new Entry());
        for(var node:tree.nodes()){
            var entry=entries.get(node.id());entry.setMeasure(node);
            if(!node.style().equals(entry.style)){applyStyle(entry.pointer,node.style());entry.style=node.style();}
            YGNodeStyleSetMargin(entry.pointer,YGEdgeRight,0);YGNodeStyleSetMargin(entry.pointer,YGEdgeBottom,0);
        }
        for(var node:tree.nodes()){
            var entry=entries.get(node.id());var children=node.children().stream().map(LayoutNode::id).toList();
            if(!entry.children.equals(children)){for(int i=0;i<children.size();i++)YGNodeInsertChild(entry.pointer,entries.get(children.get(i)).pointer,i);entry.children=children;}
            var visible=node.children().stream().filter(n->!n.style().hidden()).toList();
            for(var child:node.children()){
                float gap=visible.isEmpty()||visible.get(visible.size()-1).id().equals(child.id())?0:node.style().gap();
                long p=entries.get(child.id()).pointer;
                YGNodeStyleSetMargin(p,YGEdgeRight,node.style().direction()==LayoutStyle.Direction.ROW?gap:0);
                YGNodeStyleSetMargin(p,YGEdgeBottom,node.style().direction()==LayoutStyle.Direction.COLUMN?gap:0);
            }
        }
        YGConfigSetPointScaleFactor(config,nextViewport.pixelsPerUnit());
        if(viewport!=null&&viewport.pixelsPerUnit()!=nextViewport.pixelsPerUnit())YGNodeMarkDirtyAndPropogateToDescendants(entries.get(tree.root().id()).pointer);
        callbackFailure=null;
        // A finite owner height makes Yoga stretch an auto root to the viewport.
        // Passing undefined here lets Yoga derive the popup height from its children.
        YGNodeCalculateLayout(entries.get(tree.root().id()).pointer,nextViewport.width(),naturalHeight?Float.NaN:nextViewport.height(),YGDirectionLTR);
        if(callbackFailure!=null){
            for(var entry:entries.values())if(entry.measure!=null)YGNodeMarkDirty(entry.pointer);
            if(callbackFailure instanceof Error error)throw error;
            throw new IllegalStateException("Yoga leaf measurement failed",callbackFailure);
        }
        Map<NodeId,LayoutBox> boxes=new LinkedHashMap<>();List<NodeId> order=new ArrayList<>();
        collect(tree.root(),null,null,0,0,nextViewport.bounds(),boxes,order);
        snapshot=new ImmutableLayoutSnapshot(++revision,boxes,order);previous=tree;viewport=nextViewport;intrinsicHeight=naturalHeight;return snapshot;
    }

    private void collect(LayoutNode node,NodeId parent,NodeId scrollOwner,float px,float py,Rect inheritedClip,Map<NodeId,LayoutBox> boxes,List<NodeId> order){
        if(node.style().hidden())return;
        long p=entries.get(node.id()).pointer;
        var border=new Rect(px+YGNodeLayoutGetLeft(p),py+YGNodeLayoutGetTop(p),Math.max(0,YGNodeLayoutGetWidth(p)),Math.max(0,YGNodeLayoutGetHeight(p)));
        var clip=node.style().clip()?inheritedClip.intersect(border):inheritedClip;
        var content=border.inset(node.style().padding());
        boxes.put(node.id(),new LayoutBox(border,content,border,clip,parent,scrollOwner));order.add(node.id());
        var nextScroll=node.style().scroll()?node.id():scrollOwner;
        for(var child:node.children())collect(child,node.id(),nextScroll,border.x(),border.y(),clip,boxes,order);
    }
    private static Constraints.Axis axis(float value,int mode){return mode==YGMeasureModeExactly?Constraints.Axis.exactly(value):mode==YGMeasureModeAtMost?Constraints.Axis.atMost(Math.max(0,value)):Constraints.Axis.undefined();}
    private static void applyStyle(long p,LayoutStyle s){
        YGNodeStyleSetFlexDirection(p,s.direction()==LayoutStyle.Direction.ROW?YGFlexDirectionRow:YGFlexDirectionColumn);
        setLength(p,s.width(),true);setLength(p,s.height(),false);
        YGNodeStyleSetMinWidth(p,s.minWidth());YGNodeStyleSetMinHeight(p,s.minHeight());
        YGNodeStyleSetMaxWidth(p,Float.isInfinite(s.maxWidth())?Float.NaN:s.maxWidth());YGNodeStyleSetMaxHeight(p,Float.isInfinite(s.maxHeight())?Float.NaN:s.maxHeight());
        YGNodeStyleSetFlexGrow(p,s.grow());YGNodeStyleSetFlexShrink(p,s.shrink());
        switch(s.basis().unit()){case AUTO->YGNodeStyleSetFlexBasisAuto(p);case POINTS->YGNodeStyleSetFlexBasis(p,s.basis().value());case PERCENT->YGNodeStyleSetFlexBasisPercent(p,s.basis().value());}
        YGNodeStyleSetPadding(p,YGEdgeAll,s.padding());YGNodeStyleSetFlexWrap(p,s.wrap()?YGWrapWrap:YGWrapNoWrap);YGNodeStyleSetDisplay(p,s.hidden()?YGDisplayNone:YGDisplayFlex);
    }
    private static void setLength(long p,Length value,boolean width){
        switch(value.unit()){
            case AUTO->{if(width)YGNodeStyleSetWidthAuto(p);else YGNodeStyleSetHeightAuto(p);}
            case POINTS->{if(width)YGNodeStyleSetWidth(p,value.value());else YGNodeStyleSetHeight(p,value.value());}
            case PERCENT->{if(width)YGNodeStyleSetWidthPercent(p,value.value());else YGNodeStyleSetHeightPercent(p,value.value());}
        }
    }
    private void checkThread(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Layout engine belongs to "+thread.getName());}
    private void checkOpen(){checkThread();if(closed)throw new IllegalStateException("Layout engine is closed");}
    @Override public void close(){checkThread();if(layoutInProgress)throw new IllegalStateException("Cannot close an active Yoga layout");if(closed)return;for(var entry:entries.values())YGNodeRemoveAllChildren(entry.pointer);for(var entry:entries.values())entry.dispose();entries.clear();YGConfigFree(config);closed=true;previous=null;snapshot=null;}
}
