// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.design.atomchat;
import com.atom.lib.controls.*;
import com.atom.lib.controls.internal.ControlSlots;
import com.atom.lib.layout.*;
import com.atom.lib.font.FontId;
import com.atom.lib.text.*;
import com.atom.lib.geometry.Rect;
import com.atom.lib.geometry.Viewport;
import io.github.humbleui.skija.Canvas;
import java.util.*;

/** AC materials with instance-local style. All role geometry is Yoga-produced. */
public final class AtomChatControlSkin implements ControlSkin {
    private AtomChatControlStyle style;
    public AtomChatControlSkin(AtomChatControlStyle style){updateStyle(style);}
    public void updateStyle(AtomChatControlStyle style){this.style=Objects.requireNonNull(style);}
    public AtomChatControlStyle style(){return style;}
    public ControlMotion motion(){return new ControlMotion(.09,.14,.11);}
    public LayoutNode actionNode(NodeId id,ActionModel m,LayoutStyle outer,TextService text,float iconSize){
        var original=actionNode(id,m,outer,text);var label=original.children().get(0);
        label=new LayoutNode(label.id(),label.style().flex(1,1,Length.points(0)),label.children(),label.measure(),label.measureRevision());
        var image=LayoutNode.leaf(id.value()+"/icon",LayoutStyle.column().size(Length.points(iconSize),Length.points(iconSize)).flex(0,0,Length.auto()),null,0);
        return new LayoutNode(id,original.style().direction(LayoutStyle.Direction.ROW).spacing(6,8),List.of(image,label),null,0);
    }
    public void paintAction(Canvas c,PresentationFrame f,NodeId id,ActionModel m,ControlVisual v,TextService text,ControlIcon icon,float iconSize){
        paintAction(c,f,id,m,v,text);if(icon==null)return;
        var slot=f.box(new NodeId(id.value()+"/icon"));var bounds=slot.content();var clip=bounds.intersect(slot.clip());int saved=c.getSaveCount();c.save();
        try{c.clipRect(io.github.humbleui.types.Rect.makeXYWH(clip.x(),clip.y(),clip.width(),clip.height()));icon.paint(c,bounds,v.opacity()*(m.enabled()?1:.45f));}finally{c.restoreToCount(saved);}
    }
    public LayoutNode actionNode(NodeId id,ActionModel m,LayoutStyle outer,TextService text){
        var label=SettingsRows.label(new NodeId(id.value()+"/text"),m.label(),m.role()==ActionModel.Role.DISCLOSURE?AtomChatFonts.HEADING:AtomChatFonts.BODY,m.role()==ActionModel.Role.DISCLOSURE?UiTokens.SETTINGS_TILE_TITLE:UiTokens.FONT_BUTTON,style.textColor(),text);
        if(m.role()==ActionModel.Role.DISCLOSURE){
            var icon=LayoutNode.leaf(id.value()+"/icon",LayoutStyle.column().size(Length.points(UiTokens.s(18)),Length.points(UiTokens.s(18))).flex(0,0,Length.auto()),null,0);
            label=new LayoutNode(label.id(),label.style().flex(1,1,Length.points(0)),label.children(),label.measure(),label.measureRevision());
            var arrow=LayoutNode.leaf(id.value()+"/arrow",LayoutStyle.column().size(Length.points(UiTokens.s(12)),Length.points(UiTokens.s(12))).flex(0,0,Length.auto()),null,0);
            return new LayoutNode(id,outer.direction(LayoutStyle.Direction.ROW).spacing(0,UiTokens.s(10)).padding(new Edges(UiTokens.s(4),UiTokens.s(8),UiTokens.s(4),UiTokens.s(8))).align(LayoutStyle.Align.CENTER).flex(0,0,Length.auto()),List.of(icon,label,arrow),null,0);
        }
        return new LayoutNode(id,outer.padding(m.role()==ActionModel.Role.SEGMENT?new Edges(0,UiTokens.s(6),0,UiTokens.s(6)):Edges.uniform(UiTokens.s(6))).align(LayoutStyle.Align.CENTER).justify(LayoutStyle.Justify.CENTER).flex(outer.grow(),0,outer.basis()),List.of(label),null,0);
    }
    public void paintAction(Canvas c,PresentationFrame f,NodeId id,ActionModel m,ControlVisual v,TextService text){
        var r=f.box(id).border();int color=m.role()==ActionModel.Role.DANGER?UiTokens.dangerColor():m.selected()?style.accentColor():0;
        if(m.role()!=ActionModel.Role.DISCLOSURE&&color!=0)SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width(),r.height(),UiTokens.s(10),color);
        SettingsRows.feedback(c,f,id,v,style);
        if(m.role()==ActionModel.Role.DISCLOSURE){
            var icon=f.box(new NodeId(id.value()+"/icon")).border();var a=f.box(new NodeId(id.value()+"/arrow")).border();
            try(var p=new io.github.humbleui.skija.Paint().setAntiAlias(true).setColor(style.textColor()).setMode(io.github.humbleui.skija.PaintMode.STROKE).setStrokeWidth(UiTokens.s(1.5f))){
                float d=icon.width();c.drawRect(io.github.humbleui.types.Rect.makeXYWH(icon.x(),icon.y(),d,d),p);
                if(m.icon().equals("layers")){c.drawRect(io.github.humbleui.types.Rect.makeXYWH(icon.x()+d*.3f,icon.y()+d*.3f,d*.7f,d*.7f),p);}else{c.drawLine(icon.x(),icon.y()+d*.3f,icon.right(),icon.y()+d*.3f,p);c.drawLine(icon.x()+d*.3f,icon.y()+d*.3f,icon.x()+d*.3f,icon.bottom(),p);}
                var t=f.box(new NodeId(id.value()+"/text")).border();c.drawLine(t.x(),r.bottom()-UiTokens.s(2),r.right()-UiTokens.s(14),r.bottom()-UiTokens.s(2),p.setColor(0xff666970).setStrokeWidth(1));
            }
            String triangle=m.selected()?"M "+a.x()+" "+(a.y()+a.height()*.3f)+" L "+a.right()+" "+(a.y()+a.height()*.3f)+" L "+(a.x()+a.width()/2)+" "+(a.y()+a.height()*.8f)+" Z":"M "+(a.x()+a.width()*.3f)+" "+a.y()+" L "+(a.x()+a.width()*.3f)+" "+a.bottom()+" L "+a.right()+" "+(a.y()+a.height()/2)+" Z";
            try(var path=io.github.humbleui.skija.Path.makeFromSVGString(triangle);var p=new io.github.humbleui.skija.Paint().setAntiAlias(true).setColor(style.textColor())){c.drawPath(path,p);}
        }
        SettingsRows.label(c,f,new NodeId(id.value()+"/text"),m.label(),m.role()==ActionModel.Role.DISCLOSURE?AtomChatFonts.HEADING:AtomChatFonts.BODY,m.role()==ActionModel.Role.DISCLOSURE?UiTokens.SETTINGS_TILE_TITLE:UiTokens.FONT_BUTTON,SettingsRows.alpha(m.enabled()?style.textColor():style.textSecondaryColor(),m.enabled()?1:.55f),text);
    }
    public LayoutNode sliderNode(NodeId id,SliderModel m,String value,LayoutStyle outer,TextService text,ControlPresentation p){return p==ControlPresentation.CARD?sliderNode(id,m,value,outer,text):SettingsRows.slider(id,m,value,outer,text,style);}
    public void paintSlider(Canvas c,PresentationFrame f,NodeId id,SliderModel m,float value,String label,ControlVisual v,TextService text,ControlPresentation p){if(p==ControlPresentation.CARD)paintSlider(c,f,id,m,value,label,v,text);else SettingsRows.slider(c,f,id,m,value,label,v,text,style);}
    public LayoutNode toggleNode(NodeId id,ToggleModel m,LayoutStyle outer,TextService text,ControlPresentation p){return p==ControlPresentation.CARD?toggleNode(id,m,outer,text):SettingsRows.toggle(id,m,outer,text,style);}
    public void paintToggle(Canvas c,PresentationFrame f,NodeId id,ToggleModel m,ControlVisual v,TextService text,ControlPresentation p){if(p==ControlPresentation.CARD)paintToggle(c,f,id,m,v,text);else SettingsRows.toggle(c,f,id,m,v,text,style);}
    public LayoutNode modalTitle(NodeId id,String title,TextService text){
        return label(id,title,AtomChatFonts.HEADING,UiTokens.FONT_TITLE,style.textColor(),text);
    }
    public void paintModalSurface(Canvas c,PresentationFrame f,NodeId id,String title,TextService text){
        var r=f.box(id).border();int fill=0xfa10141e;
        SkiaDraw.drawChromeShadow(c,r.x(),r.y(),r.width(),r.height(),radius(16));
        SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width(),r.height(),radius(16),fill);
        SkiaDraw.drawEdgeHighlight(c,r.x(),r.y(),r.width(),r.height(),radius(16),UiTokens.s(1),UiTokens.hairline(fill));
        var titleId=new NodeId(id.value()+"/title");var clip=f.box(titleId).clip();int saved=c.getSaveCount();c.save();
        try{c.clipRect(io.github.humbleui.types.Rect.makeXYWH(clip.x(),clip.y(),clip.width(),clip.height()));text(c,f,titleId,title,AtomChatFonts.HEADING,UiTokens.FONT_TITLE,style.textColor(),text);}finally{c.restoreToCount(saved);}
    }
    public LayoutStyle modalDialogStyle(Viewport viewport){
        float padding=UiTokens.LIST_PAD_X;
        return LayoutStyle.column().size(Length.points(Math.max(0,Math.min(UiTokens.s(370),viewport.width()-padding*2))),Length.auto()).flex(0,0,Length.auto());
    }
    private float radius(float token){return UiTokens.s(token)*UiTokens.radiusFactor(style.cornerRadius());}
    private NodeId slot(NodeId id,String role){return ControlSlots.slot(id,role);}
    private LayoutNode label(NodeId id,String value,FontId font,float size,int color,TextService text){
        var request=new TextRequest(value,font,size,color);
        // Include the actual request, including value text and font generation.
        return LayoutNode.leaf(id.value(),LayoutStyle.column().flex(0,0,Length.auto()),c->text.layout(request,c).size(),31L*(31L*request.hashCode()+style.hashCode())+text.fontGeneration());
    }
    private LayoutStyle card(LayoutStyle outer){return outer.spacing(UiTokens.SETTINGS_ROW_PAD,UiTokens.SETTINGS_ROW_GAP).flex(outer.grow(),0,outer.basis());}
    public LayoutNode sliderNode(NodeId id,SliderModel model,String valueText,LayoutStyle outer,TextService text){
        float knob=UiTokens.SLIDER_KNOB;
        var track=LayoutNode.leaf(slot(id,"track").value(),LayoutStyle.column().size(Length.auto(),Length.points(knob)).padding(new Edges((knob-UiTokens.SLIDER_TRACK_H)/2,knob/2,(knob-UiTokens.SLIDER_TRACK_H)/2,knob/2)).flex(0,0,Length.auto()),null,0);
        return new LayoutNode(id,card(outer),List.of(
            label(slot(id,"title"),model.label(),AtomChatFonts.HEADING,UiTokens.SETTINGS_TILE_TITLE,style.textColor(),text),
            label(slot(id,"body"),model.description(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,style.textSecondaryColor(),text),
            label(slot(id,"value"),valueText,AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,style.textColor(),text),track),null,0);
    }
    private void card(Canvas c,PresentationFrame f,NodeId id,ControlVisual v){
        var r=f.box(id).border();UiCards.drawCard(c,r.x(),r.y(),r.width(),r.height(),radius(16),v.hover(),style,(x,y,w,h,rad,fill)->{});
        if(v.focused())SkiaDraw.drawEdgeHighlight(c,r.x(),r.y(),r.width(),r.height(),radius(16),UiTokens.FOCUS_RING_WIDTH,style.accentColor());
    }
    private void text(Canvas c,PresentationFrame f,NodeId id,String value,FontId font,float size,int color,TextService text){
        var r=f.box(id).content();var layout=text.layout(new TextRequest(value,font,size,color),new Constraints(Constraints.Axis.atMost(r.width()),Constraints.Axis.undefined()));
        if(layout.size().height()>r.height()+1.01f)throw new IllegalArgumentException("Stale or insufficient text geometry: "+id);
        text.draw(c,layout,r.x(),r.y());
    }
    public void paintSlider(Canvas c,PresentationFrame f,NodeId id,SliderModel model,float value,String valueText,ControlVisual v,TextService text){
        card(c,f,id,v);float opacity=model.enabled()?1:.45f;
        text(c,f,slot(id,"title"),model.label(),AtomChatFonts.HEADING,UiTokens.SETTINGS_TILE_TITLE,alpha(style.textColor(),opacity),text);
        text(c,f,slot(id,"body"),model.description(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,alpha(style.textSecondaryColor(),opacity),text);
        text(c,f,slot(id,"value"),valueText,AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,alpha(style.textColor(),opacity),text);
        var r=f.box(slot(id,"track")).content();float t=(value-model.min())/(model.max()-model.min());
        float half=UiTokens.SLIDER_KNOB/2,center=r.x()+r.width()*t,y=r.y()+r.height()/2;
        SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width(),r.height(),radius(3),alpha(UiTokens.trackRest(UiTokens.cardCutout(style)),opacity));
        SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width()*t,r.height(),radius(3),alpha(style.accentColor(),opacity));
        SkiaDraw.drawRoundedRect(c,center-half,y-half,2*half,2*half,Math.min(half,radius(9)),alpha(UiTokens.cardCutout(style),opacity));
        float inner=half-UiTokens.s(2);
        SkiaDraw.drawRoundedRect(c,center-inner,y-inner,2*inner,2*inner,Math.min(inner,radius(9)),alpha(style.accentColor(),opacity));
        if(v.press()>0)SkiaDraw.drawRoundedRect(c,center-inner,y-inner,2*inner,2*inner,Math.min(inner,radius(9)),alpha(UiTokens.hairline(style.accentColor()),v.press()*opacity));
    }
    private int alpha(int color,float weight){return (Math.round((color>>>24)*weight)<<24)|(color&0xffffff);}
    public LayoutNode toggleNode(NodeId id,ToggleModel model,LayoutStyle outer,TextService text){
        var toggle=LayoutNode.leaf(slot(id,"switch").value(),LayoutStyle.column().size(Length.points(UiTokens.SWITCH_W),Length.points(UiTokens.SWITCH_H)).flex(0,0,Length.auto()),null,0);
        return new LayoutNode(id,card(outer),List.of(
            label(slot(id,"title"),model.label(),AtomChatFonts.HEADING,UiTokens.SETTINGS_TILE_TITLE,style.textColor(),text),
            label(slot(id,"body"),model.description(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,style.textSecondaryColor(),text),toggle),null,0);
    }
    public void paintToggle(Canvas c,PresentationFrame f,NodeId id,ToggleModel model,ControlVisual v,TextService text){
        card(c,f,id,v);float opacity=model.enabled()?1:.45f;
        text(c,f,slot(id,"title"),model.label(),AtomChatFonts.HEADING,UiTokens.SETTINGS_TILE_TITLE,alpha(style.textColor(),opacity),text);
        text(c,f,slot(id,"body"),model.description(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,alpha(style.textSecondaryColor(),opacity),text);
        var r=f.box(slot(id,"switch")).content();float side=UiTokens.SWITCH_KNOB,inset=UiTokens.SWITCH_INSET;
        int base=UiTokens.cardCutout(style),rest=UiTokens.trackRest(base);
        int fill=mix(rest,style.accentColor(),v.position());
        SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width(),r.height(),Math.min(r.height()/2,radius(11)),alpha(fill,opacity));
        SkiaDraw.drawEdgeHighlight(c,r.x(),r.y(),r.width(),r.height(),Math.min(r.height()/2,radius(11)),UiTokens.s(1),alpha(UiTokens.rim(base),opacity));
        float x=r.x()+inset+(r.width()-side-2*inset)*v.position();
        SkiaDraw.drawRoundedRect(c,x,r.y()+inset,side,side,Math.min(side/2,radius(9)),alpha(0xfff5f5f5,opacity));
        if(v.press()>0)SkiaDraw.drawRoundedRect(c,x,r.y()+inset,side,side,Math.min(side/2,radius(9)),alpha(UiTokens.hairline(0xfff5f5f5),v.press()*opacity));
    }
    private int mix(int a,int b,float t){
        int r=Math.round((a>>16&255)*(1-t)+(b>>16&255)*t),g=Math.round((a>>8&255)*(1-t)+(b>>8&255)*t),bl=Math.round((a&255)*(1-t)+(b&255)*t);
        int alpha=Math.round((a>>>24)*(1-t)+(b>>>24)*t);
        return (alpha<<24)|(r<<16)|(g<<8)|bl;
    }
    public LayoutNode dialogNode(NodeId id,ConfirmationModel model,LayoutStyle outer,TextService text){
        var actionStyle=LayoutStyle.column().size(Length.auto(),Length.auto()).padding(UiTokens.s(10)).flex(1,0,Length.auto()).align(LayoutStyle.Align.CENTER).justify(LayoutStyle.Justify.CENTER);
        var cancel=new LayoutNode(slot(id,"cancel"),actionStyle,List.of(label(slot(id,"cancel/text"),model.cancelLabel(),AtomChatFonts.BODY,UiTokens.FONT_BUTTON,style.textColor(),text)),null,0);
        var confirm=new LayoutNode(slot(id,"confirm"),actionStyle,List.of(label(slot(id,"confirm/text"),model.confirmLabel(),AtomChatFonts.BODY,UiTokens.FONT_BUTTON,0xffffffff,text)),null,0);
        var actions=new LayoutNode(slot(id,"actions"),LayoutStyle.row().spacing(0,UiTokens.BUTTON_GAP).flex(0,0,Length.auto()),List.of(cancel,confirm),null,0);
        var icon=LayoutNode.leaf(slot(id,"icon").value(),LayoutStyle.column().size(Length.points(UiTokens.ICON_LARGE_SIZE),Length.points(UiTokens.ICON_LARGE_SIZE)).flex(0,0,Length.auto()),null,0);
        var title=label(slot(id,"title"),model.title(),AtomChatFonts.HEADING,UiTokens.FONT_TITLE,style.textColor(),text);
        title=new LayoutNode(title.id(),title.style().flex(1,1,Length.points(0)),title.children(),title.measure(),title.measureRevision());
        var header=new LayoutNode(slot(id,"header"),LayoutStyle.row().spacing(0,UiTokens.s(10)).align(LayoutStyle.Align.CENTER).flex(0,0,Length.auto()),List.of(icon,title),null,0);
        return new LayoutNode(id,card(outer),List.of(
            header,
            label(slot(id,"body"),model.message(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,style.textSecondaryColor(),text),actions),null,0);
    }
    public void paintDialog(Canvas c,PresentationFrame f,NodeId id,ConfirmationModel model,NodeId focused,ControlVisual v,TextService text){
        var r=f.box(id).border();int fill=0xfa10141e;
        SkiaDraw.drawChromeShadow(c,r.x(),r.y(),r.width(),r.height(),radius(16));
        SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width(),r.height(),radius(16),fill);
        SkiaDraw.drawEdgeHighlight(c,r.x(),r.y(),r.width(),r.height(),radius(16),UiTokens.s(1),UiTokens.hairline(fill));
        var icon=f.box(slot(id,"icon")).border();
        int iconSave=c.getSaveCount();c.save();
        try(var paint=new io.github.humbleui.skija.Paint().setAntiAlias(true).setColor(UiTokens.dangerColor()).setMode(io.github.humbleui.skija.PaintMode.STROKE).setStrokeWidth(UiTokens.ICON_STROKE_REF)){
            c.translate(icon.x(),icon.y());c.scale(icon.width()/20,icon.height()/20);c.drawPath(AppIcons.ICON_WARNING_PATH,paint);
        }finally{c.restoreToCount(iconSave);}
        text(c,f,slot(id,"title"),model.title(),AtomChatFonts.HEADING,UiTokens.FONT_TITLE,style.textColor(),text);
        text(c,f,slot(id,"body"),model.message(),AtomChatFonts.BODY,UiTokens.SETTINGS_TILE_SUB,style.textSecondaryColor(),text);
        action(c,f,slot(id,"cancel"),model.cancelLabel(),focused,false,v.press(),text);
        action(c,f,slot(id,"confirm"),model.confirmLabel(),focused,true,v.press(),text);
    }
    private void action(Canvas c,PresentationFrame f,NodeId id,String value,NodeId focused,boolean danger,float press,TextService text){
        var r=f.box(id).border();int fill=danger?UiTokens.dangerColor():UiTokens.trackRest(UiTokens.cardCutout(style));
        SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width(),r.height(),r.height()/2,fill);
        if(id.equals(focused))SkiaDraw.drawEdgeHighlight(c,r.x(),r.y(),r.width(),r.height(),r.height()/2,UiTokens.FOCUS_RING_WIDTH,style.accentColor());
        if(id.equals(focused)&&press>0)SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width(),r.height(),r.height()/2,alpha(UiTokens.hairline(fill),press));
        text(c,f,slot(id,"text"),value,AtomChatFonts.BODY,UiTokens.FONT_BUTTON,danger?0xffffffff:style.textColor(),text);
    }
}
