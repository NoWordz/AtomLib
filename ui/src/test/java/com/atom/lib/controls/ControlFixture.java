package com.atom.lib.controls;
import com.atom.lib.*;
import com.atom.lib.font.*;
import com.atom.lib.text.*;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import com.atom.lib.design.atomchat.*;
import com.atom.lib.geometry.*;
import com.atom.lib.motion.*;
import com.atom.lib.input.*;
import io.github.humbleui.skija.*;
import java.util.*;

class ControlFixture implements AutoCloseable {
    final RuntimeHandle runtime=AtomLibRuntime.open("controls");
    final FontRegistry fonts=new FontRegistry(runtime);
    final TextService text=new TextService(fonts,TextBreakPolicy.CJK_UI);
    final YogaLayoutEngine engine=new YogaLayoutEngine();
    final YogaLayoutEngine modalEngine=new YogaLayoutEngine();
    final AtomChatControlSkin skin=new AtomChatControlSkin(AtomChatControlStyle.defaults());
    final ControlContext context=new ControlContext(text,skin);
    final List<Float> previews=new ArrayList<>(),commits=new ArrayList<>(),cancels=new ArrayList<>();
    final List<CancelReason> reasons=new ArrayList<>();
    Slider slider;UiSession session;PresentationFrame frame;Viewport viewport;
    long ticks;
    ControlFixture(){fonts.registerDefaults();AtomChatFonts.register(fonts);slider=new Slider(new NodeId("width"),context,model(480),v->Math.round(v)+" px",new SliderEvents(){
        public void preview(float v){previews.add(v);}
        public void commit(float a,float v){commits.add(v);}
        public void cancel(float v,CancelReason r){cancels.add(v);reasons.add(r);}
    });session=new UiSession(List.of(slider));}
    SliderModel model(float v){return new SliderModel("面板宽度","调整中文宽度。这里包含开关、确认窗。",v,400,600,10,true);}
    void show(float width){show(width,1);}
    void show(float width,float density){show(width,860,density);}
    void show(float width,float height,float density){
        viewport=new Viewport(width,height,density);
        session.prepare(viewport);
        var tree=new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column().size(Length.points(width),Length.auto()).padding(15).spacing(15,12),session.controls().stream().map(c->c.node(LayoutStyle.column().flex(0,0,Length.auto()))).toList(),null,0));
        frame=PresentationFrame.create(tree,engine.layout(tree,viewport),viewport,Map.of(),new PresentationFrame.Offset(0,0));
        var modal=session.modalFrame(viewport);
        if(modal!=null)frame=PresentationFrame.compose(List.of(frame,modal));
        try(var surface=Surface.makeRasterN32Premul(Math.round(width*density),Math.round(height*density))){
            surface.getCanvas().scale(density,density);session.paint(surface.getCanvas(),frame,viewport,new FrameTime(++ticks,ticks*16000000,.016),false);
        }
    }
    Rect track(){return frame.box(new NodeId("width/track")).content();}
    float thumb(){var r=track();return r.x()+r.width()*(slider.previewValue()-400)/200;}
    void press(){var r=track();pointer(PointerEvent.Type.PRESS,thumb(),r.y()+r.height()/2,0);}
    void move(float x){var r=track();pointer(PointerEvent.Type.MOVE,x,r.y()+r.height()/2,0);}
    void release(float x){var r=track();pointer(PointerEvent.Type.RELEASE,x,r.y()+r.height()/2,0);}
    InputResult pointer(PointerEvent.Type type,float x,float y,int button){return session.pointer(new PointerEvent(type,x,y,button));}
    InputResult key(KeyEvent.Key k,boolean down){return session.key(new KeyEvent(k,down,false,false,false));}
    public void close(){session.close();modalEngine.close();engine.close();text.close();fonts.close();runtime.close();}
}
