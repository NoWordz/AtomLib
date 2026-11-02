// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.design.atomchat;
import com.atom.lib.controls.*;
import com.atom.lib.controls.internal.ControlSlots;
import com.atom.lib.layout.*;
import com.atom.lib.text.*;
import com.atom.lib.font.FontId;
import io.github.humbleui.skija.Canvas;
import java.util.*;

/** Compact role geometry and material, independent of the owning group surface. */
final class SettingsRows {
    private SettingsRows(){}
    static NodeId slot(NodeId id,String role){return ControlSlots.slot(id,role);}
    static LayoutNode label(NodeId id,String value,FontId font,float size,int color,TextService text){
        var req=new TextRequest(value,font,size,color);
        if(value.isEmpty())return LayoutNode.leaf(id.value(),LayoutStyle.column().size(Length.auto(),Length.points(0)).flex(0,0,Length.auto()),null,0);
        return LayoutNode.leaf(id.value(),LayoutStyle.column().flex(0,0,Length.auto()),c->text.layout(req,c).size(),31L*req.hashCode()+text.fontGeneration());
    }
    static LayoutNode slider(NodeId id,SliderModel m,String value,LayoutStyle outer,TextService text,AtomChatControlStyle s){
        var title=label(slot(id,"title"),m.label(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_TITLE,s.textColor(),text);
        title=new LayoutNode(title.id(),title.style().flex(1,1,Length.points(0)),title.children(),title.measure(),title.measureRevision());
        var header=new LayoutNode(slot(id,"header"),LayoutStyle.row().spacing(0,UiTokens.SETTINGS_COMPACT_GAP).align(LayoutStyle.Align.FLEX_START).flex(0,0,Length.auto()),List.of(title,label(slot(id,"value"),value,AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,s.textColor(),text)),null,0);
        float knob=UiTokens.SLIDER_KNOB;
        var track=LayoutNode.leaf(slot(id,"track").value(),LayoutStyle.column().size(Length.auto(),Length.points(knob)).padding(new Edges((knob-UiTokens.SLIDER_TRACK_H)/2,knob/2,(knob-UiTokens.SLIDER_TRACK_H)/2,knob/2)).flex(0,0,Length.auto()),null,0);
        return new LayoutNode(id,outer.spacing(0,0).padding(new Edges(UiTokens.s(8),UiTokens.SETTINGS_ROW_PAD,UiTokens.s(12),UiTokens.SETTINGS_ROW_PAD)).limits(0,UiTokens.SETTINGS_SLIDER_ROW_H,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY).flex(0,0,Length.auto()),List.of(header,label(slot(id,"body"),m.description(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,s.textSecondaryColor(),text),track),null,0);
    }
    static LayoutNode toggle(NodeId id,ToggleModel m,LayoutStyle outer,TextService text,AtomChatControlStyle s){
        var labels=new LayoutNode(slot(id,"labels"),LayoutStyle.column().flex(1,1,Length.points(0)).spacing(0,UiTokens.s(2)),List.of(label(slot(id,"title"),m.label(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_TITLE,s.textColor(),text),label(slot(id,"body"),m.description(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,s.textSecondaryColor(),text)),null,0);
        var sw=LayoutNode.leaf(slot(id,"switch").value(),LayoutStyle.column().size(Length.points(UiTokens.SWITCH_W),Length.points(UiTokens.SWITCH_H)).flex(0,0,Length.auto()),null,0);
        return new LayoutNode(id,outer.direction(LayoutStyle.Direction.ROW).spacing(0,UiTokens.s(12)).padding(new Edges(UiTokens.s(8),UiTokens.SETTINGS_ROW_PAD,UiTokens.s(8),UiTokens.SETTINGS_ROW_PAD)).align(LayoutStyle.Align.CENTER).flex(0,0,Length.auto()),List.of(labels,sw),null,0);
    }
    static void label(Canvas c,PresentationFrame f,NodeId id,String value,FontId font,float size,int color,TextService text){
        if(value.isEmpty())return;var r=f.box(id).content();text.draw(c,text.layout(new TextRequest(value,font,size,color),new Constraints(Constraints.Axis.atMost(r.width()),Constraints.Axis.undefined())),r.x(),r.y());
    }
    static int alpha(int color,float opacity){return (Math.round((color>>>24)*opacity)<<24)|(color&0xffffff);}
    static void feedback(Canvas c,PresentationFrame f,NodeId id,ControlVisual v,AtomChatControlStyle s){
        var r=f.box(id).border();if(v.hover()>0)SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width(),r.height(),UiTokens.s(8),alpha(s.accentColor(),v.hover()*.08f));
        if(v.focused())SkiaDraw.drawEdgeHighlight(c,r.x()+1,r.y()+1,Math.max(0,r.width()-2),Math.max(0,r.height()-2),UiTokens.s(8),UiTokens.FOCUS_RING_WIDTH,s.accentColor());
    }
    static void slider(Canvas c,PresentationFrame f,NodeId id,SliderModel m,float value,String formatted,ControlVisual v,TextService text,AtomChatControlStyle s){
        feedback(c,f,id,v,s);float opacity=m.enabled()?1:.45f;
        label(c,f,slot(id,"title"),m.label(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_TITLE,alpha(s.textColor(),opacity),text);
        label(c,f,slot(id,"value"),formatted,AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,alpha(s.textColor(),opacity),text);
        label(c,f,slot(id,"body"),m.description(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,alpha(s.textSecondaryColor(),opacity),text);
        var r=f.box(slot(id,"track")).content();float t=(value-m.min())/(m.max()-m.min()),x=r.x()+r.width()*t,y=r.y()+r.height()/2,half=UiTokens.SLIDER_KNOB/2;
        SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width(),r.height(),r.height()/2,alpha(0xff88898d,opacity));
        SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width()*t,r.height(),r.height()/2,alpha(s.accentColor(),opacity));
        SkiaDraw.drawRoundedRect(c,x-half,y-half,half*2,half*2,half,alpha(s.accentColor(),opacity));
        float inner=half-UiTokens.s(1.5f);SkiaDraw.drawRoundedRect(c,x-inner,y-inner,inner*2,inner*2,inner,alpha(0xffffffff,opacity));
    }
    static void toggle(Canvas c,PresentationFrame f,NodeId id,ToggleModel m,ControlVisual v,TextService text,AtomChatControlStyle s){
        feedback(c,f,id,v,s);float opacity=m.enabled()?1:.45f;
        label(c,f,slot(id,"title"),m.label(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_TITLE,alpha(s.textColor(),opacity),text);
        label(c,f,slot(id,"body"),m.description(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,alpha(s.textSecondaryColor(),opacity),text);
        var r=f.box(slot(id,"switch")).content();SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width(),r.height(),r.height()/2,alpha(m.checked()?s.accentColor():0xff37383e,opacity));
        float size=UiTokens.SWITCH_KNOB,inset=UiTokens.SWITCH_INSET,x=r.x()+inset+(r.width()-size-2*inset)*v.position();SkiaDraw.drawRoundedRect(c,x,r.y()+inset,size,size,size/2,alpha(0xffffffff,opacity));
    }
}
