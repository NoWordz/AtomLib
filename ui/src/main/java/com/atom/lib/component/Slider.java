package com.atom.lib.component;

import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.NodeId;
import java.util.Objects;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;

public final class Slider implements PointerTarget,UiElement {
    private final NodeId id;
    private final SliderSpec spec;
    private final ValueBinding<Float> binding;
    private final boolean quantizeOnCommit;
    private Rect track=new Rect(0,0,0,0);
    private boolean enabled=true,active;
    private float before,value;
    private float controlHeight=40,trackHeight,knob;
    private long layoutRevision;
    private final LeafMeasure measure=c->new MeasuredSize(c.width().constrain(controlHeight*4),c.height().constrain(controlHeight),0);
    public Slider(NodeId id,SliderSpec spec,ValueBinding<Float> binding,boolean quantizeOnCommit){this.id=Objects.requireNonNull(id);this.spec=Objects.requireNonNull(spec);this.binding=Objects.requireNonNull(binding);this.quantizeOnCommit=quantizeOnCommit;value=spec.clamp(binding.read());}
    public NodeId id(){return id;}
    @Override public PointerTarget pointerTarget(){return this;}
    @Override public LayoutNode layout(HostContext host){if(controlHeight!=host.theme().spacing().controlHeight()){controlHeight=host.theme().spacing().controlHeight();layoutRevision++;}return LayoutNode.leaf(id.value(),LayoutStyle.column().flex(0,0,Length.auto()),measure,layoutRevision);}
    /** Optional fixed metrics; without them the track and knob scale with the control height. */
    public Slider setMetrics(float trackHeight,float knob){if(!Float.isFinite(trackHeight)||trackHeight<=0||!Float.isFinite(knob)||knob<=0)throw new IllegalArgumentException("Invalid slider metrics");this.trackHeight=trackHeight;this.knob=knob;return this;}
    private float trackHeight(){return trackHeight>0?trackHeight:Math.max(1,controlHeight*.1f);}
    private float knob(){return knob>0?knob:controlHeight*.4f;}
    @Override public void geometry(LayoutBox box,HostContext host){float radius=knob()/2;track=new Rect(box.content().x()+radius,box.content().y()+box.content().height()/2,Math.max(0,box.content().width()-radius*2),trackHeight());}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        var palette=host.theme().palette();float fraction=(value()-spec.min())/(spec.max()-spec.min());float thickness=track.height();
        var line=new Rect(track.x(),track.y()-thickness/2,track.width(),thickness);
        UiPaint.rounded(canvas,line,thickness/2,palette.control());
        UiPaint.rounded(canvas,new Rect(line.x(),line.y(),line.width()*fraction,line.height()),thickness/2,enabled?palette.accent():palette.muted());
        float handle=Math.min(knob(),box.border().height()/2);
        try(var paint=new Paint().setColor(enabled?palette.accent():palette.muted())){canvas.drawCircle(track.x()+track.width()*fraction,track.y(),handle/2,paint);}
    }
    public SliderSpec spec(){return spec;}
    public float value(){return active?value:spec.clamp(binding.read());}
    public boolean active(){return active;}
    public void setTrack(Rect track){this.track=Objects.requireNonNull(track);}
    public Rect track(){return track;}
    public void setEnabled(boolean enabled){this.enabled=enabled;if(!enabled)cancel();}
    @Override public boolean enabled(){return enabled;}
    @Override public boolean press(PointerEvent event){
        if(event.button()!=0||!enabled||track.width()<=0)return false;
        before=spec.clamp(binding.read());value=before;active=true;preview(event.x());return true;
    }
    @Override public void drag(PointerEvent event){if(active&&enabled)preview(event.x());}
    @Override public void release(PointerEvent event){
        if(!active)return;
        if(!enabled){cancel();return;}
        preview(event.x());if(!active)return;if(!enabled){cancel();return;}
        commitValue();
    }
    @Override public void cancel(){if(!active)return;active=false;value=before;binding.cancel(before);}
    private void preview(float x){if(track.width()<=0)return;value=spec.clamp(spec.min()+(x-track.x())/track.width()*(spec.max()-spec.min()));try{binding.preview(value);}catch(RuntimeException|Error failure){rollback(failure);throw failure;}}
    private void commitValue(){float committed=quantizeOnCommit?spec.quantize(value):value;active=false;value=committed;try{binding.commit(committed);}catch(RuntimeException|Error failure){value=before;try{binding.cancel(before);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}}
    private void rollback(Throwable original){try{cancel();}catch(RuntimeException|Error cleanup){if(cleanup!=original)original.addSuppressed(cleanup);}}
    public InputResult key(KeyEvent event){
        if(!enabled||!event.pressed()||active)return InputResult.PASS;
        float current=value();float next;
        switch(event.key()){
            case LEFT,DOWN->next=current-spec.step();
            case RIGHT,UP->next=current+spec.step();
            case HOME->next=spec.min();case END->next=spec.max();
            default->{return InputResult.PASS;}
        }
        next=spec.clamp(next);if(quantizeOnCommit)next=spec.quantize(next);
        before=current;active=true;value=next;
        try{binding.preview(next);}catch(RuntimeException|Error failure){rollback(failure);throw failure;}
        if(active&&enabled)commitValue();else if(active)cancel();
        return InputResult.CONSUMED;
    }
}
