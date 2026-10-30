package com.atom.lib.devtools.gallery;

import com.atom.lib.component.*;
import com.atom.lib.layout.NodeId;
import com.atom.lib.style.*;
import java.util.List;

/** Real production components, no parallel showcase drawing implementation. */
public final class ComponentGallery {
    private float value=.6f;
    private boolean enabled=true;
    public UiElement page(int width){
        var slider=new Slider(new NodeId("width"),new SliderSpec(0,1,.05f),new ValueBinding<Float>(){
            public Float read(){return value;}public void preview(Float v){value=v;}public void commit(Float v){value=v;}public void cancel(Float v){value=v;}
        },false);
        var toggle=new Toggle("feedback",new ValueBinding<Boolean>(){
            public Boolean read(){return enabled;}public void preview(Boolean v){enabled=v;}public void commit(Boolean v){enabled=v;}public void cancel(Boolean v){enabled=v;}
        });
        var disabled=new Button("disabled","不可用操作",()->{});disabled.setEnabled(false);
        return new Section("root",Section.Kind.ROOT,List.of(
            new Label("title","AtomLib · "+width,Label.Tone.TITLE),
            new Label("subtitle","Skija 绘制 · Yoga 布局 · 宿主字体",Label.Tone.SECONDARY),
            new SettingRow("layout","布局尺寸","这段较长的中文说明会自动换行。页面只组合组件，不计算控件坐标。",slider),
            new SettingRow("sound","交互反馈","预览与提交由宿主决定。",toggle),
            new Button("restore","恢复默认",()->{value=.6f;enabled=true;}),disabled));
    }
    /** AC Raven palette and token families (MIT), supplied explicitly by this demo host. */
    public static ThemeSnapshot theme(){return new ThemeSnapshot(
        new Palette(0xff0d1117,0xff161b27,0xff2a313c,0xffe3b341,0xff1c1901,0xfff0f4f8,0xffa4afbd,0xffeb4034),
        new TypeScale(20,23.75f,17.5f),new SpacingScale(5,10,20,40),new RadiusScale(20,12),new MotionPolicy(false));}
}
