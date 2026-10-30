// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.shell;

import com.atom.lib.component.UiElement;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.*;
import java.util.*;

/** Optional shell. Owns page views; borrows the host's page elements, fonts, text and Canvas. */
public final class AppShell implements UiElement,AutoCloseable {
    private final NodeId id;
    private final Thread owner=Thread.currentThread();
    private final NavigationStack<UiElement> pages;
    private final List<UiElement> elements;
    private final ShellPageHost content;
    private PageTransition transition=new PageTransition(1);
    private boolean removingTop,closed;
    private long lastFrame=Long.MIN_VALUE;
    public AppShell(String id,UiElement root,ShellHeader header,TabBar tabs){
        this.id=new NodeId(id);pages=new NavigationStack<>(root);content=new ShellPageHost(id+".content",this,root);
        var children=new ArrayList<UiElement>();if(header!=null)children.add(header);children.add(content);if(tabs!=null)children.add(tabs);elements=List.copyOf(children);
    }
    @Override public NodeId id(){return id;}
    @Override public List<UiElement> children(){return elements;}
    public UiElement currentPage(){return pages.peek();}
    public int depth(){return pages.size();}
    public List<UiElement> pages(){return pages.snapshot();}
    public boolean transitioning(){return transition.active();}
    public float transitionValue(){return transition.value();}
    public float transitionVelocity(){return transition.velocity();}
    public Rect contentBounds(){return content.bounds();}
    public LayoutSnapshot contentFrame(){checkOpen();return content.snapshot();}
    public InputResult contentKey(KeyEvent event){checkOpen();return content.key(event);}
    public float contentWheel(float x,float y,float distance){checkOpen();return content.wheel(x,y,distance);}
    public void push(UiElement page){
        checkOpen();Objects.requireNonNull(page);
        if(transitioning())throw new IllegalStateException("Finish or reverse the current transition before pushing another page");
        if(pages.snapshot().stream().anyMatch(p->p==page))throw new IllegalArgumentException("Page instance is already in the stack");
        UiElement previous=pages.peek();content.pair(previous,page);pages.push(page);removingTop=false;
        transition=new PageTransition(0);transition.retarget(1,this::finish);
    }
    /** Starts pop, or reverses an in-progress push. The top remains in the stack until settled. */
    public boolean pop(){
        checkOpen();if(pages.size()==1)return false;
        if(transitioning()){
            if(!removingTop){removingTop=true;transition.retarget(0,this::finish);}
            return true;
        }
        var snapshot=pages.snapshot();content.pair(snapshot.get(snapshot.size()-2),snapshot.get(snapshot.size()-1));
        removingTop=true;transition=new PageTransition(1);transition.retarget(0,this::finish);return true;
    }
    public boolean cancelPop(){
        checkOpen();if(!transitioning()||!removingTop)return false;
        removingTop=false;transition.retarget(1,this::finish);return true;
    }
    public void advance(FrameTime time,boolean motionEnabled){
        checkOpen();if(time.frameId()<lastFrame)throw new IllegalArgumentException("Frame IDs must be monotonic");
        if(time.frameId()==lastFrame)return;lastFrame=time.frameId();transition.advance(time,motionEnabled);
    }
    private void finish(){if(removingTop)pages.pop();removingTop=false;content.stable(pages.peek());}
    @Override public LayoutNode layout(HostContext host){checkOpen();return new LayoutNode(id,LayoutStyle.column().behavior(false,false,true,false),elements.stream().map(c->c.layout(host)).toList(),null,0);}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        try(Paint paint=new Paint().setColor(host.theme().palette().surface())){var r=box.border();canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height()),paint);}
    }
    private void checkThread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Shell belongs to another thread");}
    private void checkOpen(){checkThread();if(closed)throw new IllegalStateException("Shell is closed");}
    @Override public void close(){checkThread();if(closed)return;closed=true;content.close();}
}
