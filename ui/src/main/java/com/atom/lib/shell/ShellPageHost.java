// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.shell;

import com.atom.lib.component.UiElement;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.*;
import io.github.humbleui.skija.*;
import java.util.Objects;

/** Page-local UiViews keep their own immutable layout snapshots and local input coordinates. */
final class ShellPageHost implements UiElement,PointerTarget,AutoCloseable {
    private final NodeId id;
    private final AppShell shell;
    private UiElement frontPage,backPage;
    private UiView front,back;
    private HostContext host;
    private Rect bounds=new Rect(0,0,0,0);
    private boolean closed;
    ShellPageHost(String id,AppShell shell,UiElement root){this.id=new NodeId(id);this.shell=shell;frontPage=root;}
    @Override public NodeId id(){return id;}
    @Override public PointerTarget pointerTarget(){return this;}
    Rect bounds(){return bounds;}
    LayoutSnapshot snapshot(){if(front==null)throw new IllegalStateException("Shell has not received geometry");return front.frame();}
    void pair(UiElement below,UiElement above){
        cancel();
        if(back!=null)throw new IllegalStateException("Already displaying a page pair");
        if(frontPage==below){back=front;front=null;}
        else if(frontPage!=above)throw new IllegalStateException("Current page mismatch");
        backPage=below;frontPage=above;
        if(back!=null)back.suspendInput(true);if(front!=null)front.suspendInput(true);
    }
    void stable(UiElement page){
        UiView chosen=page==frontPage?front:back;
        UiView obsolete=chosen==front?back:front;
        front=chosen;back=null;frontPage=page;backPage=null;
        if(front!=null)front.suspendInput(false);
        if(obsolete!=null)obsolete.close();
    }
    @Override public LayoutNode layout(HostContext host){return LayoutNode.leaf(id.value(),LayoutStyle.column().flex(1,1,Length.points(0)).behavior(false,false,true,false),c->new MeasuredSize(c.width().constrain(0),c.height().constrain(0),0),0);}
    @Override public void geometry(LayoutBox box,HostContext next){
        if(closed)return;bounds=box.content();
        if(host!=null&&(host.fonts()!=next.fonts()||host.text()!=next.text()||!host.bodyFont().equals(next.bodyFont())))disposeViews();
        host=Objects.requireNonNull(next);
        var viewport=new Viewport(bounds.width(),bounds.height(),1);
        if(front==null)front=new UiView(host,frontPage,viewport);else{front.setViewport(viewport);front.setTheme(host.theme());}
        if(backPage!=null){if(back==null)back=new UiView(host,backPage,viewport);else{back.setViewport(viewport);back.setTheme(host.theme());}}
        front.suspendInput(shell.transitioning());if(back!=null)back.suspendInput(true);
    }
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        if(closed)return;shell.advance(time,!host.theme().motion().reducedMotion());
        int save=canvas.getSaveCount();canvas.save();
        try{
            canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(bounds.x(),bounds.y(),bounds.width(),bounds.height()));
            float progress=shell.transitionValue();
            if(back!=null)paintPage(back,canvas,-bounds.width()*.2f*progress,time);
            if(front!=null)paintPage(front,canvas,back==null?0:bounds.width()*(1-progress),time);
        }finally{canvas.restoreToCount(save);}
    }
    private void paintPage(UiView page,Canvas canvas,float offset,FrameTime time){
        int saved=canvas.getSaveCount();canvas.save();
        try{
            canvas.translate(bounds.x()+offset,bounds.y());
            try(Paint paint=new Paint().setColor(host.theme().palette().surface())){canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(0,0,bounds.width(),bounds.height()),paint);}
            page.paint(canvas,time);
        }finally{canvas.restoreToCount(saved);}
    }
    @Override public boolean press(PointerEvent event){if(shell.transitioning())return true;return front!=null&&front.pointer(local(event))==InputResult.CONSUMED;}
    @Override public void drag(PointerEvent event){if(!shell.transitioning()&&front!=null)front.pointer(local(event));}
    @Override public void release(PointerEvent event){if(!shell.transitioning()&&front!=null)front.pointer(local(event));}
    @Override public void cancel(){
        if(front!=null&&!front.isClosed())front.pointer(new PointerEvent(PointerEvent.Type.CANCEL,0,0,-1));
        if(back!=null&&!back.isClosed())back.pointer(new PointerEvent(PointerEvent.Type.CANCEL,0,0,-1));
    }
    @Override public void textInput(String text){if(!closed&&!shell.transitioning()&&front!=null)front.textInput(text);}
    @Override public InputResult key(KeyEvent event){return closed||shell.transitioning()||front==null?InputResult.PASS:front.key(event);}
    float wheel(float x,float y,float distance){return closed||shell.transitioning()||front==null?distance:front.wheel(x-bounds.x(),y-bounds.y(),distance);}
    private PointerEvent local(PointerEvent event){return new PointerEvent(event.type(),event.x()-bounds.x(),event.y()-bounds.y(),event.button());}
    private void disposeViews(){
        var a=front;var b=back;front=null;back=null;
        Throwable failure=null;
        try{if(a!=null)a.close();}catch(RuntimeException|Error e){failure=e;}
        try{if(b!=null)b.close();}catch(RuntimeException|Error e){if(failure==null)failure=e;else if(failure!=e)failure.addSuppressed(e);}
        if(failure instanceof RuntimeException e)throw e;if(failure instanceof Error e)throw e;
    }
    @Override public void close(){if(closed)return;closed=true;disposeViews();}
}
