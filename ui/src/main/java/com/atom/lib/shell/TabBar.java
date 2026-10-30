// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.shell;

import com.atom.lib.component.UiElement;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import java.util.*;
import java.util.function.Consumer;

/** Arbitrary host-defined tab count; route changes remain a host callback. */
public final class TabBar implements UiElement {
    private final NodeId id;
    private final List<TabSpec> tabs;
    private final List<UiElement> buttons;
    private final Consumer<String> onSelect;
    private String selected;
    public TabBar(String id,List<TabSpec> tabs,String selected,Consumer<String> onSelect){
        this.id=new NodeId(id);this.tabs=List.copyOf(tabs);this.onSelect=Objects.requireNonNull(onSelect);
        Set<String> seen=new HashSet<>();List<UiElement> children=new ArrayList<>();
        for(var tab:this.tabs){if(!seen.add(tab.id()))throw new IllegalArgumentException("Duplicate tab id");children.add(new ShellButton(id+".tab."+tab.id(),tab.label(),()->select(tab.id()),()->this.selected.equals(tab.id())));}
        if(!seen.contains(selected))throw new IllegalArgumentException("Unknown selected tab");this.selected=selected;buttons=List.copyOf(children);
    }
    public String selected(){return selected;}
    public List<TabSpec> tabs(){return tabs;}
    public void select(String id){
        if(tabs.stream().noneMatch(t->t.id().equals(id)))throw new IllegalArgumentException("Unknown tab");
        if(selected.equals(id))return;String old=selected;selected=id;
        try{onSelect.accept(id);}catch(RuntimeException|Error failure){selected=old;throw failure;}
    }
    @Override public NodeId id(){return id;}
    @Override public List<UiElement> children(){return buttons;}
    @Override public LayoutNode layout(HostContext host){
        var children=buttons.stream().map(b->{var n=b.layout(host);return new LayoutNode(n.id(),n.style().flex(1,1,Length.points(0)),n.children(),n.measure(),n.measureRevision());}).toList();
        return new LayoutNode(id,LayoutStyle.row().spacing(host.theme().spacing().small(),host.theme().spacing().small()).flex(0,0,Length.auto()),children,null,0);
    }
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        try(Paint paint=new Paint().setColor(host.theme().palette().card())){var r=box.border();canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height()),paint);}
    }
}
