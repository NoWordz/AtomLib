package com.atom.lib.controls;

import com.atom.lib.design.atomchat.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.layout.*;
import io.github.humbleui.skija.Surface;
import com.atom.lib.motion.FrameTime;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsEntranceTest {
    @Test void aFullyTransparentEnteringRowDoesNotLeaveItsSeparatorVisible() throws Exception {
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context);var surface=Surface.makeRasterN32Premul(560,400)){
            var tabs=new SegmentedChoice(new NodeId("tabs"),f.context,new ChoiceModel(List.of(new ChoiceModel.Option("a","A",true)),"a"),ignored->{});
            var first=new Toggle(new NodeId("row0"),f.context,new ToggleModel("One","",false,true),ignored->{});
            var second=new Toggle(new NodeId("row1"),f.context,new ToggleModel("Two","",false,true),ignored->{});
            var group=new SettingsGroup(new NodeId("group"),new Disclosure(new NodeId("heading"),f.context,"Group","layout",true,true,ignored->{}),List.of(first,second));
            var controls=new ArrayList<Control>(tabs.controls());controls.addAll(group.controls());f.session.setControls(controls);
            var style=new SettingsPageStyle(480,false,1,1,.93f,.235f);var viewport=new Viewport(560,400,1);
            var state=shell.layout(f.session,viewport,style,"Title","12:00",tabs,List.of(group),List.of(),Map.of(first.id(),new EntrancePose(0,0,0)));
            var frame=state.presentation();var row=frame.box(first.id()).border();
            group.paint(surface.getCanvas(),frame,style.controls());
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){
                var pixels=ImageIO.read(new ByteArrayInputStream(data.getBytes()));
                int x=Math.round(row.x()+UiTokens.SETTINGS_ROW_PAD+5),y=Math.round(row.bottom()-1);
                assertEquals(pixels.getRGB(x,y-3),pixels.getRGB(x,y),"A transparent row must not draw its separator");
            }
        }
    }
    @Test void scrollingDoesNotAddNewRowsToAnExistingEntrance() {
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context);var surface=Surface.makeRasterN32Premul(560,400)){
            var tabs=new SegmentedChoice(new NodeId("tabs"),f.context,new ChoiceModel(List.of(new ChoiceModel.Option("a","A",true)),"a"),ignored->{});
            var rows=new ArrayList<Control>();for(int i=0;i<10;i++)rows.add(new Toggle(new NodeId("row"+i),f.context,new ToggleModel("Row","",false,true),ignored->{}));
            var group=new SettingsGroup(new NodeId("group"),new Disclosure(new NodeId("heading"),f.context,"Group","layout",true,true,ignored->{}),rows);
            var controls=new ArrayList<Control>(tabs.controls());controls.addAll(group.controls());f.session.setControls(controls);
            var style=new SettingsPageStyle(480,false,1,1,.93f,.235f);var viewport=new Viewport(560,400,1);
            var entrance=new PageEntrance(.2,.05,8);var firstTime=new FrameTime(1,0,0);entrance.start(firstTime,List.of(rows.get(0).id()));
            var first=shell.layout(f.session,viewport,style,"Title","12:00",tabs,List.of(group),List.of(),entrance.sample(firstTime,true));
            shell.paint(surface.getCanvas(),first,f.session,firstTime,true);
            float initial=first.presentation().opacity(rows.get(0).id());var bounds=shell.scroll().bounds();
            f.session.wheel(bounds.x()+10,bounds.y()+10,0,-5);
            var later=new FrameTime(2,100_000_000,.1);
            var second=shell.layout(f.session,viewport,style,"Title","12:00",tabs,List.of(group),List.of(),entrance.sample(later,true));
            shell.paint(surface.getCanvas(),second,f.session,later,true);
            assertTrue(shell.scroll().offset()>0);
            assertTrue(second.presentation().opacity(rows.get(0).id())>initial);
            assertEquals(1,second.presentation().opacity(rows.get(9).id()));
        }
    }
    @Test void movingTheWholeScrollPageMovesThePublishedWheelBounds() {
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context);var surface=Surface.makeRasterN32Premul(560,400)){
            var tabs=new SegmentedChoice(new NodeId("tabs"),f.context,new ChoiceModel(List.of(new ChoiceModel.Option("a","A",true)),"a"),ignored->{});
            var group=new SettingsGroup(new NodeId("group"),new Disclosure(new NodeId("heading"),f.context,"Group","layout",true,true,ignored->{}),List.of(f.slider));
            var controls=new ArrayList<Control>(tabs.controls());controls.addAll(group.controls());f.session.setControls(controls);
            var style=new SettingsPageStyle(480,false,1,1,.93f,.235f);var viewport=new Viewport(560,400,1);
            var state=shell.layout(f.session,viewport,style,"Title","12:00",tabs,List.of(group),List.of(),Map.of(new NodeId("shell/content"),new EntrancePose(0,12,.5f)));
            shell.paint(surface.getCanvas(),state,f.session,new FrameTime(1,0,.016),true);
            assertEquals(state.presentation().box(new NodeId("shell/content")).clip().y(),shell.scroll().bounds().y(),.01);
        }
    }
    @Test void groupCardAndItsControlsShareTheEntrancesGeometryAndOpacity() throws Exception {
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context);var surface=Surface.makeRasterN32Premul(560,400)){
            var tabs=new SegmentedChoice(new NodeId("tabs"),f.context,new ChoiceModel(List.of(new ChoiceModel.Option("a","A",true)),"a"),ignored->{});
            var group=new SettingsGroup(new NodeId("group"),new Disclosure(new NodeId("heading"),f.context,"Group","layout",true,true,ignored->{}),List.of(f.slider));
            var controls=new ArrayList<Control>(tabs.controls());controls.addAll(group.controls());f.session.setControls(controls);
            var style=new SettingsPageStyle(480,false,1,1,.93f,.235f);var viewport=new Viewport(560,400,1);
            var method=assertDoesNotThrow(()->SettingsShell.class.getMethod("layout",UiSession.class,Viewport.class,SettingsPageStyle.class,String.class,String.class,SegmentedChoice.class,List.class,List.class,Map.class));
            var state=(SettingsShell.Frame)method.invoke(shell,f.session,viewport,style,"Title","12:00",tabs,List.of(group),List.of(),Map.of(group.id(),new EntrancePose(0,12,.5f)));
            var frame=state.presentation();assertEquals(.5f,frame.opacity(f.slider.id()));
            var card=new NodeId("group/card");assertEquals(.5f,frame.opacity(card));
            group.paint(surface.getCanvas(),frame,style.controls());
            var box=frame.box(card).border();
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){
                int alpha=ImageIO.read(new ByteArrayInputStream(data.getBytes())).getRGB(Math.round(box.x()+box.width()/2),Math.round(box.y()+10))>>>24;
                assertTrue(alpha>0&&alpha<128,"Group surface must also fade: "+alpha);
            }
        }
    }
}
