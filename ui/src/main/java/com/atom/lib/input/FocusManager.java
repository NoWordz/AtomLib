package com.atom.lib.input;

import com.atom.lib.layout.NodeId;
import java.util.*;

public final class FocusManager {
    private record Modal(List<NodeId> order,NodeId restore) {}
    private List<NodeId> order=List.of();
    private final Deque<Modal> modals=new ArrayDeque<>();
    private NodeId focused;
    private boolean visible;
    public void setOrder(List<NodeId> order){this.order=unique(order);if(focused!=null&&!activeOrder().contains(focused))focused=first();}
    public NodeId focused(){return focused;}
    public boolean focusVisible(){return visible;}
    public void focus(NodeId id,boolean visible){if(id!=null&&!activeOrder().contains(id))throw new IllegalArgumentException("Unavailable focus: "+id);focused=id;this.visible=visible;}
    public NodeId tab(boolean backwards){
        var active=activeOrder();visible=true;
        if(active.isEmpty()){focused=null;return null;}
        int index=focused==null?-1:active.indexOf(focused);
        focused=active.get(index<0?(backwards?active.size()-1:0):Math.floorMod(index+(backwards?-1:1),active.size()));return focused;
    }
    public void pushModal(List<NodeId> available){modals.push(new Modal(unique(available),focused));focused=first();visible=true;}
    public void popModal(){if(modals.isEmpty())throw new IllegalStateException("No focus modal");var last=modals.pop();focused=last.restore()!=null&&activeOrder().contains(last.restore())?last.restore():first();visible=true;}
    /** Refresh enabled/visible members without changing the stack or its restoration points.
     * Orders are supplied bottom to top, matching OverlayHost.snapshot(). */
    public void updateModalOrders(List<List<NodeId>> orders){
        if(orders.size()!=modals.size())throw new IllegalArgumentException("Modal count differs");
        var old=new ArrayList<>(modals);var next=new ArrayDeque<Modal>();
        for(int i=0;i<old.size();i++)next.addLast(new Modal(unique(orders.get(orders.size()-1-i)),old.get(i).restore()));
        modals.clear();modals.addAll(next);
        if(focused!=null&&!activeOrder().contains(focused))focused=first();
        if(focused==null&&!modals.isEmpty())focused=first();
    }
    public void clear(){order=List.of();modals.clear();focused=null;visible=false;}
    private List<NodeId> activeOrder(){return modals.isEmpty()?order:modals.peek().order();}
    private NodeId first(){return activeOrder().isEmpty()?null:activeOrder().get(0);}
    private static List<NodeId> unique(List<NodeId> ids){var copy=List.copyOf(ids);if(new HashSet<>(copy).size()!=copy.size())throw new IllegalArgumentException("Duplicate focus ID");return copy;}
}
