// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.design.atomchat;
import com.atom.lib.controls.*;
import com.atom.lib.layout.*;
import io.github.humbleui.skija.Canvas;
import java.util.*;
/** Shared card surface; controls remain independent transactions. */
public final class SettingsGroup {
    private final NodeId id;private final Disclosure disclosure;private final List<Control> rows;
    public SettingsGroup(NodeId id,Disclosure disclosure,List<? extends Control> rows){this.id=Objects.requireNonNull(id);this.disclosure=Objects.requireNonNull(disclosure);this.rows=List.copyOf(rows);for(var row:this.rows)row.presentation(ControlPresentation.SETTINGS_ROW);disclosure.bindChildren(this.rows);}
    public NodeId id(){return id;}
    public List<Control> controls(){var result=new ArrayList<Control>();result.add(disclosure.control());if(disclosure.expanded())result.addAll(rows);return List.copyOf(result);}
    public LayoutNode node(){var children=new ArrayList<LayoutNode>();children.add(disclosure.node(LayoutStyle.row()));if(disclosure.expanded())children.add(new LayoutNode(new NodeId(id.value()+"/card"),LayoutStyle.column().flex(0,0,Length.auto()),rows.stream().map(c->c.node(LayoutStyle.column())).toList(),null,0));return new LayoutNode(id,LayoutStyle.column().spacing(0,UiTokens.s(10)).flex(0,0,Length.auto()),children,null,0);}
    public void paint(Canvas canvas,PresentationFrame frame,AtomChatControlStyle style){
        var id=new NodeId(this.id.value()+"/card");if(!frame.paintOrder().contains(id))return;var clip=frame.box(id).clip();int saved=canvas.getSaveCount();canvas.save();
        try{canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(clip.x(),clip.y(),clip.width(),clip.height()));float scale=frame.renderScale(id);canvas.scale(scale,scale);var f=frame.forPainting(id);var r=f.box(id).border();UiCards.drawCard(canvas,r.x(),r.y(),r.width(),r.height(),UiTokens.s(16),0,style,(x,y,w,h,rad,fill)->{});
            for(int i=0;i<rows.size()-1;i++){var row=f.box(rows.get(i).id()).border();SkiaDraw.drawRoundedRect(canvas,row.x()+UiTokens.SETTINGS_ROW_PAD,row.bottom()-1,Math.max(0,row.width()-2*UiTokens.SETTINGS_ROW_PAD),1,0,0x447e8188);}
        }finally{canvas.restoreToCount(saved);}
    }
}
