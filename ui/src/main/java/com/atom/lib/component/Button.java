package com.atom.lib.component;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;
import java.util.*;
public final class Button implements UiElement,PointerTarget {
    private final NodeId id;
    private final Label label;
    private final Runnable action;
    private Rect hit=new Rect(0,0,0,0),clip=hit;
    private boolean enabled=true,pressed;
    public Button(String id,String text,Runnable action){this.id=new NodeId(id);label=new Label(id+".label",text,Label.Tone.ON_ACCENT);this.action=Objects.requireNonNull(action);}
    @Override public NodeId id(){return id;}
    @Override public List<UiElement> children(){return List.of(label);}
    @Override public PointerTarget pointerTarget(){return this;}
    public void setEnabled(boolean enabled){this.enabled=enabled;label.setTone(enabled?Label.Tone.ON_ACCENT:Label.Tone.DISABLED);}
    @Override public boolean enabled(){return enabled;}
    public boolean pressed(){return pressed;}
    @Override public LayoutNode layout(HostContext host){var s=host.theme().spacing();return new LayoutNode(id,LayoutStyle.column().spacing(s.medium(),0).limits(0,s.controlHeight(),Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY).flex(0,0,Length.auto()),List.of(label.layout(host)),null,0);}
    @Override public void geometry(LayoutBox box,HostContext host){hit=box.hit();clip=box.clip();}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){UiPaint.rounded(canvas,box.border(),host.theme().radius().control(),enabled?host.theme().palette().accent():host.theme().palette().control());}
    @Override public boolean press(PointerEvent e){if(e.button()!=0||!enabled)return false;pressed=true;return true;}
    @Override public void drag(PointerEvent e){}
    @Override public void release(PointerEvent e){if(!pressed)return;pressed=false;if(enabled&&hit.contains(e.x(),e.y())&&clip.contains(e.x(),e.y()))action.run();}
    @Override public void cancel(){pressed=false;}
    @Override public InputResult key(KeyEvent e){if(enabled&&e.pressed()&&(e.key()==KeyEvent.Key.ENTER||e.key()==KeyEvent.Key.SPACE)){action.run();return InputResult.CONSUMED;}return InputResult.PASS;}
}
