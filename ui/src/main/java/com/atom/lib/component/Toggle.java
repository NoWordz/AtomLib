package com.atom.lib.component;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;
import java.util.Objects;
public final class Toggle implements UiElement,PointerTarget {
    private final NodeId id;
    private final ValueBinding<Boolean> binding;
    private boolean enabled=true,pressed;
    private Rect hit=new Rect(0,0,0,0),clip=hit;
    private float height=40;
    private long revision;
    private final LeafMeasure measure=c->new MeasuredSize(c.width().constrain(height*1.5f),c.height().constrain(height),0);
    public Toggle(String id,ValueBinding<Boolean> binding){this.id=new NodeId(id);this.binding=Objects.requireNonNull(binding);}
    @Override public NodeId id(){return id;}
    @Override public PointerTarget pointerTarget(){return this;}
    public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean enabled(){return enabled;}
    @Override public LayoutNode layout(HostContext host){if(height!=host.theme().spacing().controlHeight()){height=host.theme().spacing().controlHeight();revision++;}return LayoutNode.leaf(id.value(),LayoutStyle.column().flex(0,0,Length.auto()),measure,revision);}
    @Override public void geometry(LayoutBox box,HostContext host){hit=box.hit();clip=box.clip();}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        float h=Math.min(height*.6f,box.content().height()),w=Math.min(h*1.8f,box.content().width());
        var track=new Rect(box.content().x(),box.content().y()+(box.content().height()-h)/2,w,h);boolean on=binding.read();
        UiPaint.rounded(canvas,track,h/2,on&&enabled?host.theme().palette().accent():host.theme().palette().control());
        float radius=Math.max(0,h/2-host.theme().spacing().small()*.5f);
        try(var paint=new Paint().setColor(host.theme().palette().onAccent())){canvas.drawCircle(track.x()+(on?w-h/2:h/2),track.y()+h/2,radius,paint);}
    }
    @Override public boolean press(PointerEvent event){if(!enabled||event.button()!=0)return false;pressed=true;return true;}
    @Override public void drag(PointerEvent event){}
    @Override public void release(PointerEvent event){if(!pressed)return;pressed=false;if(enabled&&hit.contains(event.x(),event.y())&&clip.contains(event.x(),event.y()))flip();}
    @Override public void cancel(){pressed=false;}
    private void flip(){boolean next=!binding.read();binding.preview(next);binding.commit(next);}
    @Override public InputResult key(KeyEvent event){if(enabled&&event.pressed()&&(event.key()==KeyEvent.Key.ENTER||event.key()==KeyEvent.Key.SPACE)){flip();return InputResult.CONSUMED;}return InputResult.PASS;}
}
