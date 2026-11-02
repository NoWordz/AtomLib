// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.design.atomchat;
import com.atom.lib.controls.*;
import com.atom.lib.geometry.*;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Canvas;
import java.util.*;

/** Optional settings chrome and page composition. Owns layout engines and one scroll viewport. */
public final class SettingsShell implements AutoCloseable {
    private final ControlContext context;
    private final YogaLayoutEngine headerLayout=new YogaLayoutEngine(),bodyLayout=new YogaLayoutEngine();
    private final ScrollViewport scroll=new ScrollViewport();
    public SettingsShell(ControlContext context){this.context=Objects.requireNonNull(context);}
    public record Frame(Viewport viewport,PresentationFrame presentation,Rect panel,SettingsPageStyle style,String title,String clock,List<SettingsGroup> groups){}
    public ScrollViewport scroll(){return scroll;}
    /** Call before updating models so any rollback is included in this frame's measurements. */
    public void prepare(UiSession session,Viewport viewport,SettingsPageStyle style){session.prepare(viewport);session.prepareCoordinates(style.uiScale()*style.contentScale());session.scrollViewport(scroll);}
    public Frame layout(UiSession session,Viewport viewport,SettingsPageStyle style,String title,String clock,SegmentedChoice navigation,List<SettingsGroup> groups,List<ActionButton> footer){
        prepare(session,viewport,style);
        float marginX=viewport.width()>=160?40:0,marginY=viewport.height()>=160?20:0;
        float width=Math.max(1,Math.min(style.width()*style.uiScale(),viewport.width()-marginX*2)),height=Math.max(1,Math.min(860*style.uiScale(),viewport.height()-marginY-(marginY>0?40:0)));
        var panel=new Rect(marginX,marginY,width,height);float scale=style.uiScale(),logicalWidth=width/scale;
        var headerId=new NodeId("shell/header");var heading=SettingsRows.label(new NodeId("shell/title"),title,AtomChatFonts.HEADING,UiTokens.FONT_TITLE,style.controls().textColor(),context.text());
        heading=new LayoutNode(heading.id(),heading.style().flex(1,1,Length.points(0)),heading.children(),heading.measure(),heading.measureRevision());
        var clockLabel=SettingsRows.label(new NodeId("shell/clock"),clock,AtomChatFonts.BODY,UiTokens.s(16),style.controls().textColor(),context.text());
        var header=new LayoutNode(headerId,LayoutStyle.row().size(Length.auto(),Length.points(UiTokens.HEADER_HEIGHT)).padding(new Edges(0,UiTokens.HEADER_PAD_X,0,UiTokens.HEADER_PAD_X)).align(LayoutStyle.Align.CENTER).flex(0,0,Length.auto()),List.of(heading,clockLabel),null,0);
        var nav=navigation.node(LayoutStyle.row().size(Length.auto(),Length.points(UiTokens.s(26))).spacing(0,UiTokens.s(16)).padding(new Edges(0,UiTokens.s(8),0,UiTokens.s(8))).flex(0,0,Length.auto()));
        float topPadding=style.halfScreen()?0:UiTokens.LIST_PAD_X;var ht=new LayoutTree(new LayoutNode(new NodeId("shell/top"),LayoutStyle.column().size(Length.points(logicalWidth),Length.auto()).padding(new Edges(UiTokens.s(14),topPadding,0,topPadding)).spacing(0,UiTokens.s(16)).padding(new Edges(UiTokens.s(14),topPadding,0,topPadding)),List.of(header,nav),null,0));
        var hv=new Viewport(viewport.width()/scale,viewport.height()/scale,viewport.pixelsPerUnit()*scale);
        var hf=PresentationFrame.create(ht,headerLayout.layoutIntrinsicHeight(ht,hv),hv,Map.of(),new PresentationFrame.Offset(panel.x()/scale,panel.y()/scale)).scaled(scale);
        float contentScale=scale*style.contentScale(),inset=UiTokens.s(18)*scale,top=panel.y()+UiTokens.s(118)*scale;
        var contentBounds=new Rect(panel.x()+inset,Math.min(panel.bottom(),top),Math.max(1,panel.width()-2*inset),Math.max(0,panel.bottom()-top-UiTokens.s(16)*scale));
        var children=new ArrayList<LayoutNode>();for(var group:groups)children.add(group.node());
        if(!footer.isEmpty()){children.add(LayoutNode.leaf("shell/footer-gap",LayoutStyle.column().size(Length.auto(),Length.points(UiTokens.s(92))).flex(0,0,Length.auto()),null,0));for(var button:footer)children.add(button.node(LayoutStyle.column()));}
        var bt=new LayoutTree(new LayoutNode(new NodeId("shell/content"),LayoutStyle.column().size(Length.points(contentBounds.width()/contentScale),Length.auto()).spacing(0,UiTokens.s(10)).behavior(false,false,true,true),children,null,0));
        var bv=new Viewport(contentBounds.width()/contentScale,Math.max(1,viewport.height()/contentScale),viewport.pixelsPerUnit()*contentScale);
        var bf=scroll.present(bt,bodyLayout.layoutIntrinsicHeight(bt,bv),viewport,contentBounds,contentScale);
        var layers=new ArrayList<PresentationFrame>();layers.add(hf);layers.add(bf);
        var modal=session.modalFrame(viewport);
        if(modal!=null)layers.add(modal);
        return new Frame(viewport,PresentationFrame.compose(layers),panel,style,title,clock,List.copyOf(groups));
    }
    public void paint(Canvas canvas,Frame state,UiSession session,FrameTime time,boolean motion){
        paint(canvas,state,session,time,motion,false);
    }
    public void paint(Canvas canvas,Frame state,UiSession session,FrameTime time,boolean motion,boolean solidFallback){
        var p=state.panel();var s=state.style();float radius=s.halfScreen()?0:UiTokens.s(28)*s.uiScale();
        SkiaDraw.drawRoundedRect(canvas,p.x(),p.y(),p.width(),p.height(),radius,solidFallback?(s.controls().panelBackgroundColor()|0xff000000):s.controls().panelBackgroundColor());
        var f=state.presentation();float scale=f.renderScale(new NodeId("shell/header"));int saved=canvas.getSaveCount();canvas.save();
        try{canvas.scale(scale,scale);var base=f.forPainting(new NodeId("shell/header"));var h=base.box(new NodeId("shell/header")).border();SkiaDraw.drawRoundedRect(canvas,h.x(),h.y(),h.width(),h.height(),s.halfScreen()?0:UiTokens.s(18),UiTokens.cardFill(s.controls()));
            // The title is centered independently of the trailing clock's width.
            var title=context.text().layout(new com.atom.lib.text.TextRequest(state.title(),AtomChatFonts.HEADING,UiTokens.FONT_TITLE,s.controls().textColor()),new Constraints(Constraints.Axis.atMost(h.width()),Constraints.Axis.undefined()));context.text().draw(canvas,title,h.x()+(h.width()-title.size().width())/2,h.y()+(h.height()-title.size().height())/2);
            SettingsRows.label(canvas,base,new NodeId("shell/clock"),state.clock(),AtomChatFonts.BODY,UiTokens.s(16),s.controls().textColor(),context.text());
        }finally{canvas.restoreToCount(saved);}
        for(var group:state.groups())group.paint(canvas,f,s.controls());
        session.paint(canvas,f,state.viewport(),time,motion);
    }
    @Override public void close(){headerLayout.close();bodyLayout.close();}
}
