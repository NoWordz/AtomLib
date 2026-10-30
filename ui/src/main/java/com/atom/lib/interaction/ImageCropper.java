// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.interaction;

import com.atom.lib.component.*;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;
import java.util.Objects;
import java.util.function.Consumer;

/** Borrowed image, centre-anchored zoom, transactional pan and CPU PNG callback. */
public final class ImageCropper implements UiElement,PointerTarget,AutoCloseable {
    private final NodeId id;
    private final Image image;
    private final int outputWidth,outputHeight;
    private final EditTransaction<CropState> edit;
    private final Consumer<byte[]> png;
    private Rect frame=new Rect(0,0,0,0),clip=frame;
    private boolean dragging,closed;
    private float lastX,lastY;
    public ImageCropper(String id,Image image,int outputWidth,int outputHeight,ValueBinding<CropState> binding,Consumer<byte[]> png){
        this.id=new NodeId(id);this.image=Objects.requireNonNull(image);this.png=Objects.requireNonNull(png);
        if(image.isClosed()||outputWidth<1||outputHeight<1||outputWidth>8192||outputHeight>8192)throw new IllegalArgumentException("Invalid crop image/output size");
        this.outputWidth=outputWidth;this.outputHeight=outputHeight;edit=new EditTransaction<>(binding);
    }
    @Override public NodeId id(){return id;}
    @Override public PointerTarget pointerTarget(){return this;}
    @Override public boolean enabled(){return !closed&&frame.width()>0&&frame.height()>0;}
    public CropState state(){return normalized(edit.value());}
    public Rect cropFrame(){return frame;}
    public void beginEdit(){checkOpen();edit.begin();}
    public void commit(){dragging=false;edit.commit();}
    @Override public void cancel(){dragging=false;edit.cancel();}
    public void pan(float dx,float dy){
        checkFrame();if(!Float.isFinite(dx)||!Float.isFinite(dy))throw new IllegalArgumentException("Invalid pan delta");
        edit.begin();var state=state();float scale=base()*state.zoom();
        edit.preview(normalized(new CropState(state.zoom(),state.offsetX()+dx/scale,state.offsetY()+dy/scale)));
    }
    /** One wheel event is one transaction, unless a slider/pan edit is already active. */
    public void zoom(float factor){
        checkFrame();if(!Float.isFinite(factor)||factor<=0)throw new IllegalArgumentException("Invalid zoom factor");
        boolean discrete=!edit.active();edit.begin();var state=state();float base=base(),scale=base*state.zoom();
        float cx=frame.x()+frame.width()/2,cy=frame.y()+frame.height()/2;
        float[] next=CropMath.zoomAtCentre(image.getWidth(),image.getHeight(),scale,cx,cy,frame.width()/2,frame.height()/2,cx+state.offsetX()*scale,cy+state.offsetY()*scale,factor,base);
        edit.preview(new CropState(next[0]/base,(next[1]-cx)/next[0],(next[2]-cy)/next[0]));
        if(discrete)commit();
    }
    @Override public LayoutNode layout(HostContext host){
        return LayoutNode.leaf(id.value(),LayoutStyle.column().flex(1,1,Length.points(0)).behavior(false,false,true,false),
                c->new MeasuredSize(c.width().constrain(outputWidth),c.height().constrain(outputHeight),0),0);
    }
    @Override public void geometry(LayoutBox box,HostContext host){
        if(closed)return;edit.sync();var bounds=box.content();clip=box.clip();
        float scale=Math.min(bounds.width()/outputWidth,bounds.height()/outputHeight);
        float w=outputWidth*scale,h=outputHeight*scale;
        frame=new Rect(bounds.x()+(bounds.width()-w)/2,bounds.y()+(bounds.height()-h)/2,w,h);
        if(!enabled())cancel();
    }
    private float base(){return CropMath.baseScale(image.getWidth(),image.getHeight(),frame.width(),frame.height());}
    private CropState normalized(CropState value){
        if(frame.width()==0||frame.height()==0)return value;
        float scale=base()*value.zoom();
        float[] xy=CropMath.clampCover(image.getWidth(),image.getHeight(),scale,0,0,frame.width()/2,frame.height()/2,value.offsetX()*scale,value.offsetY()*scale);
        return new CropState(value.zoom(),xy[0]/scale,xy[1]/scale);
    }
    public Rect sourceRect(){
        checkFrame();var state=state();float scale=base()*state.zoom();
        float[] source=CropMath.visibleRect(image.getWidth(),image.getHeight(),scale,0,0,frame.width()/2,frame.height()/2,state.offsetX()*scale,state.offsetY()*scale);
        return new Rect(source[0],source[1],source[2],source[3]);
    }
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        if(!enabled())return;var source=sourceRect();
        canvas.drawImageRect(image,skia(source),skia(frame),SamplingMode.LINEAR,null,true);
        try(Paint outline=new Paint().setColor(host.theme().palette().accent()).setMode(PaintMode.STROKE).setStrokeWidth(1)){
            canvas.drawRect(skia(frame),outline);
        }
    }
    /** Does no file selection, disk write or upload. Callback receives a fresh PNG byte array. */
    public void exportPng(){
        checkFrame();var source=sourceRect();
        byte[] bytes;
        try(Surface surface=Surface.makeRasterN32Premul(outputWidth,outputHeight)){
            surface.getCanvas().clear(0);
            surface.getCanvas().drawImageRect(image,skia(source),io.github.humbleui.types.Rect.makeXYWH(0,0,outputWidth,outputHeight),SamplingMode.LINEAR,null,true);
            try(Image result=surface.makeImageSnapshot();Data data=result.encodeToData(EncodedImageFormat.PNG)){
                if(data==null)throw new IllegalStateException("PNG encoding failed");bytes=data.getBytes();
            }
        }
        png.accept(bytes);
    }
    @Override public boolean press(PointerEvent event){
        if(!enabled()||event.button()!=0||!frame.contains(event.x(),event.y())||!clip.contains(event.x(),event.y()))return false;
        beginEdit();dragging=true;lastX=event.x();lastY=event.y();return true;
    }
    @Override public void drag(PointerEvent event){if(dragging){float dx=event.x()-lastX,dy=event.y()-lastY;lastX=event.x();lastY=event.y();pan(dx,dy);}}
    @Override public void release(PointerEvent event){if(dragging){drag(event);commit();}}
    private void checkOpen(){if(closed||image.isClosed())throw new IllegalStateException("Cropper/image closed");}
    private void checkFrame(){checkOpen();if(!enabled())throw new IllegalStateException("Crop frame has no area");}
    private static io.github.humbleui.types.Rect skia(Rect r){return io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height());}
    @Override public void close(){if(closed)return;closed=true;cancel();}
}
