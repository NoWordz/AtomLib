// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.interaction;

import com.atom.lib.component.*;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;
import java.util.Objects;

/** Reusable nine-anchor editor, with host-provided preview and persistence callbacks. */
public final class HudEditor implements UiElement,PointerTarget {
    private final NodeId id;
    private final float naturalWidth,naturalHeight,snapDistance;
    private final EditTransaction<HudPlacement> edit;
    private final HudPainter painter;
    private Rect viewport=new Rect(0,0,0,0),clip=viewport,item=viewport;
    private boolean dragging;
    private float grabX,grabY;
    public HudEditor(String id,float width,float height,float snapDistance,ValueBinding<HudPlacement> binding){
        this(id,width,height,snapDistance,binding,(canvas,bounds,host,time)->{
            try(Paint paint=new Paint().setColor(host.theme().palette().accent())){
                canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(bounds.x(),bounds.y(),bounds.width(),bounds.height()),paint);
            }
        });
    }
    public HudEditor(String id,float width,float height,float snapDistance,ValueBinding<HudPlacement> binding,HudPainter painter){
        this.id=new NodeId(id);
        if(!Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0||!Float.isFinite(snapDistance)||snapDistance<0)throw new IllegalArgumentException("Invalid HUD geometry");
        naturalWidth=width;naturalHeight=height;this.snapDistance=snapDistance;this.painter=Objects.requireNonNull(painter);edit=new EditTransaction<>(binding);
    }
    @Override public NodeId id(){return id;}
    @Override public PointerTarget pointerTarget(){return this;}
    public HudPlacement placement(){return edit.value();}
    public Rect itemBounds(){return item;}
    public void beginEdit(){edit.begin();}
    public void setScale(float scale){
        if(!Float.isFinite(scale))throw new IllegalArgumentException("Invalid scale");
        edit.begin();var p=edit.value();edit.preview(new HudPlacement(p.anchor(),p.offsetX(),p.offsetY(),Math.max(.5f,Math.min(2,scale))));updateBounds();
    }
    /** Selecting a grid anchor resets offsets; it is part of the current edit transaction. */
    public void setAnchor(HudAnchor anchor){Objects.requireNonNull(anchor);edit.begin();var p=edit.value();edit.preview(new HudPlacement(anchor,0,0,p.scale()));updateBounds();}
    public void setOffset(float x,float y){if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("Invalid offset");edit.begin();var p=edit.value();edit.preview(new HudPlacement(p.anchor(),x,y,p.scale()));updateBounds();}
    public void commit(){dragging=false;edit.commit();}
    @Override public void cancel(){dragging=false;edit.cancel();updateBounds();}
    @Override public LayoutNode layout(HostContext host){return LayoutNode.leaf(id.value(),LayoutStyle.column().flex(1,1,Length.points(0)).behavior(false,false,true,false),c->new MeasuredSize(c.width().constrain(300),c.height().constrain(180),0),0);}
    @Override public void geometry(LayoutBox box,HostContext host){viewport=box.content();clip=box.clip();edit.sync();updateBounds();if(viewport.width()==0||viewport.height()==0)cancel();}
    private void updateBounds(){
        var p=edit.value();float w=naturalWidth*p.scale(),h=naturalHeight*p.scale();
        item=new Rect(viewport.x()+p.anchor().x()*(viewport.width()-w)+p.offsetX(),viewport.y()+p.anchor().y()*(viewport.height()-h)+p.offsetY(),w,h);
    }
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        try(Paint lines=new Paint().setColor(host.theme().palette().control())){
            for(HudAnchor anchor:HudAnchor.values()){
                float x=viewport.x()+viewport.width()*anchor.x(),y=viewport.y()+viewport.height()*anchor.y();
                canvas.drawCircle(x,y,anchor==placement().anchor()?4:2,lines);
            }
        }
        int save=canvas.getSaveCount();canvas.save();
        try{canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(viewport.x(),viewport.y(),viewport.width(),viewport.height()));painter.paint(canvas,item,host,time);}
        finally{canvas.restoreToCount(save);}
    }
    @Override public boolean press(PointerEvent event){
        if(event.button()!=0||!item.contains(event.x(),event.y())||!clip.contains(event.x(),event.y()))return false;
        edit.begin();dragging=true;grabX=event.x()-item.x();grabY=event.y()-item.y();return true;
    }
    @Override public void drag(PointerEvent event){
        if(!dragging)return;
        var p=edit.value();float w=naturalWidth*p.scale(),h=naturalHeight*p.scale();
        float x=event.x()-grabX,y=event.y()-grabY;
        HudAnchor best=p.anchor();float bestDistance=Float.POSITIVE_INFINITY;
        for(HudAnchor anchor:HudAnchor.values()){
            float ax=viewport.x()+anchor.x()*(viewport.width()-w),ay=viewport.y()+anchor.y()*(viewport.height()-h);
            float distance=(float)Math.hypot(x-ax,y-ay);
            if(distance<bestDistance){best=anchor;bestDistance=distance;}
        }
        float ax=viewport.x()+best.x()*(viewport.width()-w),ay=viewport.y()+best.y()*(viewport.height()-h);
        float dx=x-ax,dy=y-ay;
        if(bestDistance<=snapDistance){dx=0;dy=0;}
        edit.preview(new HudPlacement(best,dx,dy,p.scale()));updateBounds();
    }
    @Override public void release(PointerEvent event){if(dragging){drag(event);commit();}}
}
