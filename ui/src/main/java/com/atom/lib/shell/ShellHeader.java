// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.shell;

import com.atom.lib.component.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;
import java.util.*;
import java.util.function.Supplier;

/** Optional, parameterized header; no fixed title, route names, icons or static press state. */
public final class ShellHeader implements UiElement {
    private final NodeId id;
    private final Label title;
    private final List<UiElement> elements;
    public ShellHeader(String id,String title,ShellAction back,List<ShellAction> actions){this(id,()->title,back,actions);}
    public ShellHeader(String id,Supplier<String> title,ShellAction back,List<ShellAction> actions){
        this.id=new NodeId(id);this.title=new Label(id+".title",Objects.requireNonNull(title),Label.Tone.TITLE);
        List<UiElement> children=new ArrayList<>();Set<String> ids=new HashSet<>();
        if(back!=null)children.add(new ShellButton(id+".back",back.label(),back.action(),()->false));
        children.add(this.title);
        for(var action:List.copyOf(actions)){
            if(!ids.add(action.id()))throw new IllegalArgumentException("Duplicate header action");
            children.add(new ShellButton(id+".action."+action.id(),action.label(),action.action(),()->false));
        }
        elements=List.copyOf(children);
    }
    @Override public NodeId id(){return id;}
    @Override public List<UiElement> children(){return elements;}
    @Override public LayoutNode layout(HostContext host){
        List<LayoutNode> nodes=new ArrayList<>();
        for(var child:elements){var node=child.layout(host);if(child==title)node=new LayoutNode(node.id(),node.style().flex(1,1,Length.points(0)),node.children(),node.measure(),node.measureRevision());nodes.add(node);}
        var spacing=host.theme().spacing();
        return new LayoutNode(id,LayoutStyle.row().spacing(spacing.medium(),spacing.small()).flex(0,0,Length.auto()),nodes,null,0);
    }
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        try(Paint paint=new Paint().setColor(host.theme().palette().card())){var r=box.border();canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height()),paint);}
    }
}
