package com.atom.lib.controls.internal;
import com.atom.lib.layout.NodeId;
import java.util.List;
/** Ordered, already-filtered displayed candidates, independent of controls. */
public final class FocusState {
    private List<NodeId> candidates=List.of();
    private NodeId current;
    public NodeId current(){return current;}
    public void candidates(List<NodeId> next){
        candidates=List.copyOf(next);
        if(current!=null&&!candidates.contains(current))current=candidates.isEmpty()?null:candidates.get(0);
    }
    public NodeId step(boolean reverse){
        if(candidates.isEmpty())return current=null;
        int index=current==null?-1:candidates.indexOf(current);
        current=candidates.get(index<0?(reverse?candidates.size()-1:0):Math.floorMod(index+(reverse?-1:1),candidates.size()));return current;
    }
    public void restore(NodeId id){current=id!=null&&candidates.contains(id)?id:candidates.isEmpty()?null:candidates.get(0);}
    public void select(NodeId id){if(id!=null&&!candidates.contains(id))throw new IllegalArgumentException("Not a displayed focus candidate");current=id;}
}
