package com.atom.lib.controls;
import com.atom.lib.controls.internal.ControlSlots;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import java.util.*;
import java.util.function.DoubleFunction;
import io.github.humbleui.skija.Canvas;

public final class Slider extends Control {
    private SliderModel model;
    private final DoubleFunction<String> format;
    private final SliderEvents events;
    private final EnumSet<KeyEvent.Key> adjusting=EnumSet.noneOf(KeyEvent.Key.class);
    private float preview,start,lastX;
    private boolean active,pointer;
    private Float override;
    public Slider(NodeId id,ControlContext context,SliderModel model,DoubleFunction<String> formatValue,SliderEvents events){
        super(id,context);this.model=Objects.requireNonNull(model);this.format=Objects.requireNonNull(formatValue);this.events=Objects.requireNonNull(events);preview=model.value();
    }
    public float previewValue(){return preview;}
    public void update(SliderModel next){
        Objects.requireNonNull(next);var old=model;boolean wasActive=active;model=next;
        boolean external=(!equal(next.value(),old.value())&&!equal(next.value(),preview))||next.min()!=old.min()||next.max()!=old.max()||next.step()!=old.step();
        if(active&&external){override=next.value();terminate(CancelReason.HOST_OVERRIDE);}
        else if(active&&!next.enabled())terminate(CancelReason.DISABLED);
        if(!active&&!wasActive)preview=next.value();
    }
    @Override public LayoutNode node(LayoutStyle style){return context.skin().sliderNode(id(),model,valueText(),style,context.text(),presentation());}
    private String valueText(){return Objects.requireNonNull(format.apply(preview),"Null slider value text");}
    Set<NodeId> slots(){return Set.of(id(),slot("title"),slot("body"),slot("value"),slot("track"));}
    private NodeId slot(String role){return ControlSlots.slot(id(),role);}
    boolean enabled(){return model.enabled();}
    boolean interacting(){return active;}
    private void begin(boolean pointer){if(active)terminate(CancelReason.FOCUS_LOST);active=true;this.pointer=pointer;start=preview;pressed=true;}
    boolean press(PointerEvent e,PresentationFrame frame){
        var box=frame.box(slot("track"));
        if(e.button()!=0||!box.hit().contains(e.x(),e.y())||!box.clip().contains(e.x(),e.y())||box.content().width()<=0)return false;
        begin(true);lastX=e.x();var track=box.content();
        float center=track.x()+track.width()*(preview-model.min())/(model.max()-model.min());
        // Hit area minus usable center travel defines the handle diameter.
        float radius=(box.hit().width()-track.width())/2;
        if(Math.abs(e.x()-center)>radius)publish(model.min()+(e.x()-track.x())/track.width()*(model.max()-model.min()));
        return true;
    }
    void drag(PointerEvent e,PresentationFrame frame){
        if(!active||!pointer)return;
        float dx=e.x()-lastX;lastX=e.x();float travel=frame.box(slot("track")).content().width();
        if(travel>0)publish(preview+dx/travel*(model.max()-model.min()));
    }
    void release(PointerEvent e,PresentationFrame frame){if(active&&pointer)commit();}
    private void publish(float next){
        next=Math.max(model.min(),Math.min(model.max(),next));if(equal(next,preview))return;preview=next;
        try{events.preview(next);}catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private void commit(){float initial=start,value=preview;clear();if(!equal(initial,value))events.commit(initial,value);}
    private void clear(){active=false;pointer=false;pressed=false;adjusting.clear();override=null;}
    void cancel(CancelReason reason){
        if(!active)return;float value=override==null?start:override;preview=value;clear();events.cancel(value,reason);
    }
    boolean key(KeyEvent e){
        boolean adjustment=switch(e.key()){case LEFT,RIGHT,UP,DOWN,HOME,END->true;default->false;};
        if(!adjustment)return false;
        if(!e.pressed()){
            if(!adjusting.remove(e.key()))return false;
            if(adjusting.isEmpty()&&active&&!pointer)commit();return true;
        }
        if(pointer)return true;
        if(!active)begin(false);adjusting.add(e.key());
        float next=switch(e.key()){
            case HOME->model.min();case END->model.max();case LEFT,DOWN->preview-model.step();default->preview+model.step();
        };publish(next);return true;
    }
    private boolean equal(float a,float b){return a==b||Math.abs((double)a-b)<=Math.min(Math.ulp(a),Math.ulp(b))*.25;}
    void paint(Canvas canvas,PresentationFrame frame,ControlVisual visual){context.skin().paintSlider(canvas,frame,id(),model,preview,valueText(),visual,context.text(),presentation());}
}
