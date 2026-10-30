package com.atom.lib.component;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;
import java.util.*;
public final class SettingRow implements UiElement {
    private final Section card;
    public SettingRow(String id,String title,String description,UiElement control){
        var elements=new ArrayList<UiElement>();elements.add(new Label(id+".title",title,Label.Tone.PRIMARY));
        if(!description.isEmpty())elements.add(new Label(id+".description",description,Label.Tone.SECONDARY));
        elements.add(Objects.requireNonNull(control));card=new Section(id,Section.Kind.CARD,elements);
    }
    @Override public NodeId id(){return card.id();}
    @Override public List<UiElement> children(){return card.children();}
    @Override public LayoutNode layout(HostContext host){return card.layout(host);}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){card.paint(canvas,box,host,time);}
}
