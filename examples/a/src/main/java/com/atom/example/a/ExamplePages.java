package com.atom.example.a;

import com.atom.lib.component.*;
import com.atom.lib.layout.NodeId;
import com.atom.lib.shell.*;
import java.util.List;

/** Three supported integration paths: components, optional shell, raw Skija. */
public final class ExamplePages {
    private ExamplePages(){}
    public static UiElement settings(float[] width){
        var slider=new Slider(new NodeId("example.width"),new SliderSpec(320,640,10),new ValueBinding<Float>(){
            public Float read(){return width[0];} public void preview(Float v){width[0]=v;}
            public void commit(Float v){width[0]=v;} public void cancel(Float v){width[0]=v;}
        },false);
        return new Section("page",Section.Kind.SCROLL,List.of(
            new Label("title","AtomLib components",Label.Tone.TITLE),
            new SettingRow("width-row","Panel width","The host supplies data and actions; AtomLib owns the geometry.",slider)));
    }
    public static AppShell shell(UiElement page){
        return new AppShell("example.shell",page,new ShellHeader("example.header","Example",null,List.of()),
            new TabBar("example.tabs",List.of(new TabSpec("one","One"),new TabSpec("two","Two")),"one",ignored->{}));
    }
}
