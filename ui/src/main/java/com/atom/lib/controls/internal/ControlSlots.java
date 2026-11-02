package com.atom.lib.controls.internal;
import com.atom.lib.layout.NodeId;
public final class ControlSlots {
    private ControlSlots(){}
    public static NodeId slot(NodeId root,String role){return new NodeId(root.value()+"/"+role);}
}
