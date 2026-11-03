package com.atom.lib.controls;

import com.atom.lib.design.atomchat.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Surface;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroupEntranceOpacityReviewTest {
    @Test void groupAndChildrenFadeAsOneSurface() throws Exception {
        try(var fixture=new ControlFixture();var shell=new SettingsShell(fixture.context);var surface=Surface.makeRasterN32Premul(560,400)) {
            var tabs=new SegmentedChoice(new NodeId("tabs"),fixture.context,new ChoiceModel(List.of(new ChoiceModel.Option("a","A",true)),"a"),ignored->{});
            var group=new SettingsGroup(new NodeId("group"),new Disclosure(new NodeId("heading"),fixture.context,"Group","layout",true,true,ignored->{}),List.of(fixture.slider));
            var controls=new ArrayList<Control>(tabs.controls());controls.addAll(group.controls());fixture.session.setControls(controls);
            var viewport=new Viewport(560,400,1);
            var style=new SettingsPageStyle(480,false,1,1,0f,.235f);
            var state=shell.layout(fixture.session,viewport,style,"Title","12:00",tabs,List.of(group),List.of(),Map.of(group.id(),new EntrancePose(0,0,.5f)));
            var frame=state.presentation();
            shell.paint(surface.getCanvas(),state,fixture.session,new FrameTime(1,0,.016),true);
            var card=frame.box(new NodeId("group/card")).border();
            int maxAlpha=0;
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) {
                var pixels=ImageIO.read(new ByteArrayInputStream(data.getBytes()));
                for(int y=Math.max(0,(int)card.y()+5);y<Math.min(pixels.getHeight(),(int)card.bottom()-5);y++)
                    for(int x=Math.max(0,(int)card.x()+5);x<Math.min(pixels.getWidth(),(int)card.right()-5);x++)
                        maxAlpha=Math.max(maxAlpha,pixels.getRGB(x,y)>>>24);
            }
            assertTrue(maxAlpha<=130,"A group faded to 50% must composite its contents once, max alpha was "+maxAlpha);
        }
    }
}
