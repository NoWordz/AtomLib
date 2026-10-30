// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.overlay;

import com.atom.lib.component.*;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.*;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import java.util.Objects;

/** HSV + alpha transaction. Painting, dragging and buttons consume one prepared geometry. */
public final class ColorPicker implements UiElement,PointerTarget {
    private enum Part { NONE, SATURATION_VALUE, HUE, ALPHA, CONFIRM, CANCEL }
    private final NodeId id;
    private final ValueBinding<Integer> binding;
    private int original,value;
    private float hue,saturation,brightness,alpha;
    private boolean open;
    private Part pressed=Part.NONE;
    private Rect sv=new Rect(0,0,0,0),hueBox=sv,alphaBox=sv,confirmBox=sv,cancelBox=sv,clip=sv;
    private float preferredHeight=320;
    private long revision;
    private final LeafMeasure measure=c->new MeasuredSize(c.width().constrain(preferredHeight),c.height().constrain(preferredHeight),0);
    public ColorPicker(String id,ValueBinding<Integer> binding){this.id=new NodeId(id);this.binding=Objects.requireNonNull(binding);open();}
    @Override public NodeId id(){return id;}
    @Override public PointerTarget pointerTarget(){return this;}
    @Override public boolean enabled(){return open;}
    public boolean isOpen(){return open;}
    public int value(){return value;}
    public Rect saturationValueBounds(){return sv;}
    public Rect hueBounds(){return hueBox;}
    public Rect alphaBounds(){return alphaBox;}
    public Rect confirmBounds(){return confirmBox;}
    public Rect cancelBounds(){return cancelBox;}
    public void open(){if(open)return;int read=Objects.requireNonNull(binding.read());original=read;setValue(read);pressed=Part.NONE;open=true;}
    private void setValue(int color){value=color;float[] hsv=hsva(color);hue=hsv[0];saturation=hsv[1];brightness=hsv[2];alpha=hsv[3];}
    public void previewHsva(float hue,float saturation,float brightness,float alpha){
        if(!open)throw new IllegalStateException("Color picker is closed");
        int next=argb(hue,saturation,brightness,alpha);
        this.hue=hue==360?360:normalizeHue(hue);this.saturation=saturation;this.brightness=brightness;this.alpha=alpha;value=next;
        try{binding.preview(next);}catch(RuntimeException|Error failure){try{dismiss();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    public void confirm(){
        if(!open)return;open=false;pressed=Part.NONE;int next=value;
        try{binding.commit(next);}catch(RuntimeException|Error failure){setValue(original);try{binding.cancel(original);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    public void dismiss(){if(!open)return;open=false;pressed=Part.NONE;setValue(original);binding.cancel(original);}
    public void overlayClosed(){dismiss();}
    @Override public void cancel(){dismiss();}
    @Override public LayoutNode layout(HostContext host){float next=host.theme().spacing().controlHeight()*8;if(preferredHeight!=next){preferredHeight=next;revision++;}return LayoutNode.leaf(id.value(),LayoutStyle.column().size(Length.auto(),Length.points(preferredHeight)).flex(0,0,Length.auto()).behavior(false,!open,true,false),measure,revision);}
    @Override public void geometry(LayoutBox box,HostContext host){
        clip=box.clip().intersect(box.border());float pad=Math.min(host.theme().spacing().medium(),Math.min(box.content().width(),box.content().height())/2);var inner=box.content().inset(pad);
        float gap=host.theme().spacing().small(),buttonHeight=Math.min(host.theme().spacing().controlHeight(),inner.height()/4),barHeight=Math.min(host.theme().spacing().controlHeight()/2,inner.height()/8);
        float height=Math.min(inner.width(),Math.max(0,inner.height()-buttonHeight-barHeight*2-gap*3));
        sv=new Rect(inner.x(),inner.y(),inner.width(),height);hueBox=new Rect(inner.x(),sv.bottom()+gap,inner.width(),barHeight);alphaBox=new Rect(inner.x(),hueBox.bottom()+gap,inner.width(),barHeight);
        float buttonWidth=Math.max(0,(inner.width()-gap)/2);cancelBox=new Rect(inner.x(),alphaBox.bottom()+gap,buttonWidth,buttonHeight);confirmBox=new Rect(inner.right()-buttonWidth,cancelBox.y(),buttonWidth,buttonHeight);
    }
    private Part hit(float x,float y){if(!clip.contains(x,y))return Part.NONE;if(sv.contains(x,y))return Part.SATURATION_VALUE;if(hueBox.contains(x,y))return Part.HUE;if(alphaBox.contains(x,y))return Part.ALPHA;if(confirmBox.contains(x,y))return Part.CONFIRM;if(cancelBox.contains(x,y))return Part.CANCEL;return Part.NONE;}
    @Override public boolean press(PointerEvent event){if(!open||event.button()!=0)return false;pressed=hit(event.x(),event.y());if(pressed==Part.NONE)return false;update(event);return true;}
    @Override public void drag(PointerEvent event){if(open)update(event);}
    @Override public void release(PointerEvent event){if(!open)return;Part part=pressed;if(part==Part.CONFIRM||part==Part.CANCEL){pressed=Part.NONE;if(hit(event.x(),event.y())==part){if(part==Part.CONFIRM)confirm();else dismiss();}}else{update(event);pressed=Part.NONE;}}
    private void update(PointerEvent event){
        switch(pressed){
            case SATURATION_VALUE->{if(sv.width()>0&&sv.height()>0)previewHsva(hue,unit((event.x()-sv.x())/sv.width()),1-unit((event.y()-sv.y())/sv.height()),alpha);}
            case HUE->{if(hueBox.width()>0)previewHsva(unit((event.x()-hueBox.x())/hueBox.width())*360,saturation,brightness,alpha);}
            case ALPHA->{if(alphaBox.width()>0)previewHsva(hue,saturation,brightness,unit((event.x()-alphaBox.x())/alphaBox.width()));}
            default->{}
        }
    }
    @Override public InputResult key(KeyEvent event){if(!open||!event.pressed())return InputResult.PASS;switch(event.key()){case ESCAPE->dismiss();case ENTER->confirm();case LEFT->previewHsva(hue-1,saturation,brightness,alpha);case RIGHT->previewHsva(hue+1,saturation,brightness,alpha);default->{return InputResult.PASS;}}return InputResult.CONSUMED;}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        if(!open)return;int saved=canvas.getSaveCount();canvas.save();
        try(var paint=new Paint().setAntiAlias(true)){
            var border=box.border();canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(clip.x(),clip.y(),clip.width(),clip.height()));
            canvas.drawRRect(RRect.makeXYWH(border.x(),border.y(),border.width(),border.height(),host.theme().radius().card()),paint.setColor(host.theme().palette().card()));
            gradient(canvas,sv,false,new int[]{0xffffffff,argb(hue,1,1,1)});
            gradient(canvas,sv,true,new int[]{0x00000000,0xff000000});
            gradient(canvas,hueBox,false,new int[]{0xffff0000,0xffffff00,0xff00ff00,0xff00ffff,0xff0000ff,0xffff00ff,0xffff0000});
            checker(canvas,alphaBox,paint,host);
            int rgb=argb(hue,saturation,brightness,1);gradient(canvas,alphaBox,false,new int[]{rgb&0x00ffffff,rgb});
            float radius=Math.max(1,host.theme().spacing().small()/2);
            canvas.drawCircle(sv.x()+saturation*sv.width(),sv.y()+(1-brightness)*sv.height(),radius,paint.setColor(host.theme().palette().onAccent()).setMode(PaintMode.STROKE).setStrokeWidth(2));paint.setMode(PaintMode.FILL);
            float hx=hueBox.x()+hue/360*hueBox.width(),ax=alphaBox.x()+alpha*alphaBox.width();
            canvas.drawLine(hx,hueBox.y(),hx,hueBox.bottom(),paint.setColor(host.theme().palette().onAccent()).setStrokeWidth(2));canvas.drawLine(ax,alphaBox.y(),ax,alphaBox.bottom(),paint);
            button(canvas,cancelBox,"Cancel",host.theme().palette().control(),host,paint);button(canvas,confirmBox,"Apply",host.theme().palette().accent(),host,paint);
        }finally{canvas.restoreToCount(saved);}
    }
    private static void fill(Canvas canvas,Rect r,int color,Paint paint){if(r.width()>0&&r.height()>0)canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height()),paint.setColor(color));}
    private static void gradient(Canvas canvas,Rect r,boolean vertical,int[] colors){
        if(r.width()==0||r.height()==0)return;
        try(var shader=Shader.makeLinearGradient(r.x(),r.y(),vertical?r.x():r.right(),vertical?r.bottom():r.y(),colors);var paint=new Paint().setShader(shader)){
            canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height()),paint);
        }
    }
    private static void checker(Canvas canvas,Rect bounds,Paint paint,HostContext host){float cell=Math.max(2,host.theme().spacing().small()*2);for(float y=0;y<bounds.height();y+=cell)for(float x=0;x<bounds.width();x+=cell)fill(canvas,new Rect(bounds.x()+x,bounds.y()+y,Math.min(cell,bounds.width()-x),Math.min(cell,bounds.height()-y)),(((int)(x/cell)+(int)(y/cell))&1)==0?0xff777777:0xffbbbbbb,paint);}
    private static void button(Canvas canvas,Rect rect,String text,int color,HostContext host,Paint paint){canvas.drawRRect(RRect.makeXYWH(rect.x(),rect.y(),rect.width(),rect.height(),host.theme().radius().control()),paint.setColor(color));var layout=host.text().layout(new TextRequest(text,host.bodyFont(),host.theme().type().body(),host.theme().palette().onAccent(),1,true),new Constraints(Constraints.Axis.atMost(rect.width()),Constraints.Axis.atMost(rect.height())));host.text().draw(canvas,layout,rect.x()+Math.max(0,(rect.width()-layout.size().width())/2),rect.y()+Math.max(0,(rect.height()-layout.size().height())/2));}
    public static int argb(float hue,float saturation,float brightness,float alpha){
        if(!Float.isFinite(hue)||!Float.isFinite(saturation)||!Float.isFinite(brightness)||!Float.isFinite(alpha)||saturation<0||saturation>1||brightness<0||brightness>1||alpha<0||alpha>1)throw new IllegalArgumentException("Invalid HSVA");
        float h=normalizeHue(hue)/60,c=brightness*saturation,x=c*(1-Math.abs(h%2-1)),m=brightness-c;float r=0,g=0,b=0;
        if(h<1){r=c;g=x;}else if(h<2){r=x;g=c;}else if(h<3){g=c;b=x;}else if(h<4){g=x;b=c;}else if(h<5){r=x;b=c;}else{r=c;b=x;}
        return (Math.round(alpha*255)<<24)|(Math.round((r+m)*255)<<16)|(Math.round((g+m)*255)<<8)|Math.round((b+m)*255);
    }
    public static float[] hsva(int argb){float r=((argb>>>16)&255)/255f,g=((argb>>>8)&255)/255f,b=(argb&255)/255f,max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min;float h=d==0?0:max==r?60*((g-b)/d%6):max==g?60*((b-r)/d+2):60*((r-g)/d+4);return new float[]{normalizeHue(h),max==0?0:d/max,max,(argb>>>24)/255f};}
    private static float normalizeHue(float h){return (h%360+360)%360;}
    private static float unit(float value){return Math.max(0,Math.min(1,value));}
}
