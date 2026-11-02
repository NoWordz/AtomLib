// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;
import com.atom.lib.design.atomchat.*;
import com.atom.lib.geometry.*;
import com.atom.lib.layout.*;
import com.atom.lib.input.*;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SettingsCompositionTest {
    @Test void halfScreenHeaderSpansTheFlatPanelRatherThanBeingInset(){
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)){
            var tabs=tabs(f,new ArrayList<>());f.session.setControls(tabs.controls());var state=show(f,shell,tabs,List.of(),List.of(),new SettingsPageStyle(480,true,1,1,.93f,.235f));
            var panel=state.panel();var header=state.presentation().box(new NodeId("shell/header")).border();assertEquals(panel.x(),header.x(),.01);assertEquals(panel.width(),header.width(),.01);
        }
    }
    @Test void failedPaintDoesNotPublishNewScrollBounds(){
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)){
            var tabs=tabs(f,new ArrayList<>());f.session.setControls(tabs.controls());var style=new SettingsPageStyle(480,false,1,1,.93f,.235f);show(f,shell,tabs,List.of(),List.of(),style);var old=shell.scroll().bounds();
            var next=shell.layout(f.session,new Viewport(560,260,1),style,"外观","11:16",tabs,List.of(),List.of());
            try(var surface=Surface.makeRasterN32Premul(560,260)){assertThrows(IllegalArgumentException.class,()->f.session.paint(surface.getCanvas(),next.presentation(),next.viewport(),new FrameTime(0,0,0),false));}
            assertEquals(old,shell.scroll().bounds(),"Only successfully painted geometry can route the next wheel");
        }
    }
    @Test void collapsingAGroupImmediatelyCancelsItsCapturedChildExactlyOnce(){
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)){
            var tabs=tabs(f,new ArrayList<>());var disclosure=new Disclosure(new NodeId("heading"),f.context,"布局缩放","layout",true,true,v->{});var group=new SettingsGroup(new NodeId("group"),disclosure,List.of(f.slider));var controls=new ArrayList<Control>(tabs.controls());controls.addAll(group.controls());f.session.setControls(controls);show(f,shell,tabs,List.of(group),List.of(),new SettingsPageStyle(480,false,1,1,.93f,.235f));
            f.press();f.move(f.thumb()+20);assertTrue(f.slider.previewValue()>480);disclosure.update(false,true);
            assertEquals(List.of(480f),f.cancels);f.release(f.thumb());assertTrue(f.commits.isEmpty());assertEquals(List.of(480f),f.cancels);
        }
    }
    SettingsShell.Frame show(ControlFixture f,SettingsShell shell,SegmentedChoice tabs,List<SettingsGroup> groups,List<ActionButton> footer,SettingsPageStyle style){
        var vp=new Viewport(560,400,1);var state=shell.layout(f.session,vp,style,"外观","11:16",tabs,groups,footer);try(var surface=Surface.makeRasterN32Premul(560,400)){shell.paint(surface.getCanvas(),state,f.session,new FrameTime(++f.ticks,f.ticks,0),false);}f.frame=state.presentation();return state;
    }
    SegmentedChoice tabs(ControlFixture f,List<String> events){return new SegmentedChoice(new NodeId("tabs"),f.context,new ChoiceModel(List.of(new ChoiceModel.Option("disabled","禁用",false),new ChoiceModel.Option("a","甲",true),new ChoiceModel.Option("b","乙",true)),"a"),events::add);}
    @Test void choiceUsesRealActivationAndDisabledOptionIsSkippedByTab(){
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)){
            var events=new ArrayList<String>();var tabs=tabs(f,events);f.session.setControls(tabs.controls());show(f,shell,tabs,List.of(),List.of(),new SettingsPageStyle(480,false,1,1,.93f,.235f));
            f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertTrue(events.isEmpty()); // first enabled is selected a
            f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);f.key(KeyEvent.Key.SPACE,true);f.key(KeyEvent.Key.SPACE,true);f.key(KeyEvent.Key.SPACE,false);assertEquals(List.of("b"),events);
        }
    }
    @Test void scrollingClipsHitTargetsCancelsSliderAndModalIsolatesWheel(){
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)){
            var tabs=tabs(f,new ArrayList<>());var disclosure=new Disclosure(new NodeId("heading"),f.context,"分组","layout",true,true,v->{});var rows=new ArrayList<Control>();rows.add(f.slider);for(int i=0;i<8;i++)rows.add(new Toggle(new NodeId("row"+i),f.context,new ToggleModel("选项","中文说明",false,true),v->{}));var group=new SettingsGroup(new NodeId("group"),disclosure,rows);var all=new ArrayList<Control>(tabs.controls());all.addAll(group.controls());f.session.setControls(all);var style=new SettingsPageStyle(480,false,1,1,.93f,.235f);show(f,shell,tabs,List.of(group),List.of(),style);
            float beforeY=f.frame.box(f.slider.id()).border().y();f.press();f.move(f.thumb()+20);assertTrue(f.slider.previewValue()>480);var bounds=shell.scroll().bounds();assertEquals(InputResult.CONSUMED,f.session.wheel(bounds.x()+20,bounds.y()+20,0,-3));assertEquals(480,f.slider.previewValue());assertEquals(List.of(480f),f.cancels);show(f,shell,tabs,List.of(group),List.of(),style);assertEquals(beforeY-120,f.frame.box(f.slider.id()).border().y());
            var last=f.frame.box(new NodeId("row7"));assertEquals(0,last.border().intersect(last.clip()).height());float offset=shell.scroll().offset();f.session.open(new ConfirmationDialog(new NodeId("dialog"),f.context,new ConfirmationModel("确认？","正文","取消","确认")),v->{});show(f,shell,tabs,List.of(group),List.of(),style);assertEquals(InputResult.CONSUMED,f.session.wheel(bounds.x()+20,bounds.y()+20,0,-100));assertEquals(offset,shell.scroll().offset());
        }
    }
    @Test void tabCanReachOffscreenFooterAndScrollbarDragIsLibraryOwned(){
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)){
            var tabs=tabs(f,new ArrayList<>());int[] actions={0};var button=new ActionButton(new NodeId("footer"),f.context,new ActionModel("执行",true,ActionModel.Role.NORMAL),()->actions[0]++);f.session.setControls(List.of(tabs.controls().get(0),tabs.controls().get(1),tabs.controls().get(2),button));var style=new SettingsPageStyle(480,false,1.2f,1,.93f,.235f);show(f,shell,tabs,List.of(),List.of(button),style);
            for(int i=0;i<3;i++){f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);}assertTrue(shell.scroll().offset()>0);show(f,shell,tabs,List.of(),List.of(button),style);f.key(KeyEvent.Key.ENTER,true);f.key(KeyEvent.Key.ENTER,false);assertEquals(1,actions[0]);
            var t=shell.scroll().thumb();f.pointer(PointerEvent.Type.PRESS,t.x()+1,t.y()+1,0);f.pointer(PointerEvent.Type.MOVE,t.x()+1,t.y()-200,0);f.pointer(PointerEvent.Type.RELEASE,t.x()+1,t.y()-200,0);assertEquals(0,shell.scroll().offset());
        }
    }
    @Test void scaledPresentationPaintsAndHitsSameToggleGeometry(){
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)){
            var tabs=tabs(f,new ArrayList<>());int[] changes={0};var toggle=new Toggle(new NodeId("toggle"),f.context,new ToggleModel("开关","",false,true),v->changes[0]++);var group=new SettingsGroup(new NodeId("g"),new Disclosure(new NodeId("heading"),f.context,"组","layout",true,true,v->{}),List.of(toggle));var all=new ArrayList<Control>(tabs.controls());all.addAll(group.controls());f.session.setControls(all);show(f,shell,tabs,List.of(group),List.of(),new SettingsPageStyle(480,false,.8f,1.2f,.93f,.235f));var r=f.frame.box(toggle.id()).border();float x=r.x()+r.width()/2,y=r.y()+r.height()/2;f.pointer(PointerEvent.Type.PRESS,x,y,0);f.pointer(PointerEvent.Type.RELEASE,x,y,0);assertEquals(1,changes[0]);assertEquals(.96f,f.frame.renderScale(toggle.id()),.00001);
        }
    }
}
