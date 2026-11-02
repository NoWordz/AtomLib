package com.atom.lib.controls;

import com.atom.lib.design.atomchat.*;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Main-review tests: expectations derive from pointer ownership and scroll endpoints. */
class AppearanceIndependentReviewTest {
    @Test void minimumSizeScrollbarThumbCanReachTheContentEndAtTheTrackEnd() {
        try (var engine = new YogaLayoutEngine()) {
            var tree = new LayoutTree(LayoutNode.leaf("content",
                LayoutStyle.column().size(Length.points(100),Length.points(10000))
                    .behavior(false,false,true,true),null,0));
            var viewport = new Viewport(100,100,1);
            var scroll = new ScrollViewport();
            scroll.publish(scroll.present(tree,engine.layoutIntrinsicHeight(tree,viewport),
                viewport,new Rect(0,0,100,100),1));
            var thumb = scroll.thumb();
            scroll.dragThumb(100-thumb.height());
            assertEquals(9900,scroll.offset(),.01,
                "Moving the thumb to the track bottom must expose the final 100 units");
        }
    }

    @Test void unrelatedMouseButtonReleaseDoesNotEndTheLeftScrollbarGesture() {
        try (var fixture = new ControlFixture(); var shell = new SettingsShell(fixture.context)) {
            var tabs = new SegmentedChoice(new NodeId("tabs"),fixture.context,
                new ChoiceModel(List.of(new ChoiceModel.Option("adjust","调整",true)),"adjust"),id->{});
            var rows = new ArrayList<Control>();
            for (int i=0;i<20;i++) rows.add(new Toggle(new NodeId("option"+i),fixture.context,
                new ToggleModel("选项","中文说明",false,true),value->{}));
            var group = new SettingsGroup(new NodeId("group"),new Disclosure(new NodeId("heading"),
                fixture.context,"设置","layout",true,true,value->{}),rows);
            var controls = new ArrayList<Control>(tabs.controls());controls.addAll(group.controls());
            fixture.session.setControls(controls);
            var viewport = new Viewport(560,400,1);
            var state = shell.layout(fixture.session,viewport,new SettingsPageStyle(480,false,1,1,.93f,.235f),
                "外观","11:16",tabs,List.of(group),List.of());
            try (var surface = Surface.makeRasterN32Premul(560,400)) {
                shell.paint(surface.getCanvas(),state,fixture.session,new FrameTime(1,1,0),false);
            }
            var thumb = shell.scroll().thumb();float x=thumb.x()+1,y=thumb.y()+1;
            fixture.pointer(PointerEvent.Type.PRESS,x,y,0);
            fixture.pointer(PointerEvent.Type.RELEASE,x,y,1);
            fixture.pointer(PointerEvent.Type.MOVE,x,y+20,0);
            assertTrue(shell.scroll().offset()>0,
                "Right-button release cannot release a left-button-owned thumb drag");
            fixture.pointer(PointerEvent.Type.RELEASE,x,y+20,0);
        }
    }

    @Test void escapeCancelsScrollbarCaptureAndConsumesItsRemainingPointerTail() {
        try (var fixture = new ControlFixture(); var shell = new SettingsShell(fixture.context)) {
            var tabs = new SegmentedChoice(new NodeId("tabs"),fixture.context,
                new ChoiceModel(List.of(new ChoiceModel.Option("adjust","调整",true)),"adjust"),id->{});
            var rows = new ArrayList<Control>();
            for (int i=0;i<20;i++) rows.add(new Toggle(new NodeId("option"+i),fixture.context,
                new ToggleModel("选项","中文说明",false,true),value->{}));
            var group = new SettingsGroup(new NodeId("group"),new Disclosure(new NodeId("heading"),
                fixture.context,"设置","layout",true,true,value->{}),rows);
            var controls = new ArrayList<Control>(tabs.controls());controls.addAll(group.controls());
            fixture.session.setControls(controls);
            var viewport = new Viewport(560,400,1);
            var state = shell.layout(fixture.session,viewport,new SettingsPageStyle(480,false,1,1,.93f,.235f),
                "外观","11:16",tabs,List.of(group),List.of());
            try (var surface = Surface.makeRasterN32Premul(560,400)) {
                shell.paint(surface.getCanvas(),state,fixture.session,new FrameTime(1,1,0),false);
            }
            var thumb = shell.scroll().thumb();float x=thumb.x()+1,y=thumb.y()+1;
            fixture.pointer(PointerEvent.Type.PRESS,x,y,0);
            assertEquals(InputResult.CONSUMED,fixture.key(KeyEvent.Key.ESCAPE,true),
                "Escape must cancel the active thumb gesture before the host can close the screen");
            assertEquals(InputResult.CONSUMED,fixture.pointer(PointerEvent.Type.MOVE,x,y+20,0));
            assertEquals(0,shell.scroll().offset());
            assertEquals(InputResult.CONSUMED,fixture.pointer(PointerEvent.Type.RELEASE,x,y+20,0));
        }
    }
}
