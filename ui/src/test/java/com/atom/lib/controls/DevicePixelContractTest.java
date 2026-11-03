// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;

import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.PointerEvent;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.design.atomchat.SkiaDraw;
import io.github.humbleui.skija.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DevicePixelContractTest {
    @Test void deviceDensityAndPresentationScalePaintAndHitTheSameTranslatedControl() {
        for(float density:new float[]{1,1.5f,2,3})for(float uiScale:new float[]{.8f,1,1.25f}) {
            try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul((int)(240*density),(int)(240*density));var bitmap=new Bitmap()) {
                int[] calls={0};
                var action=new ActionButton(new NodeId("action"),f.context,new ActionModel("Go",true,ActionModel.Role.SEGMENT,true),()->calls[0]++);
                action.icon((canvas,r,opacity)->{try(var paint=new Paint().setColor(0xffff0000)) {canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height()),paint);}},20);
                f.session.setControls(List.of(action));
                var root=new LayoutNode(new NodeId("root"),LayoutStyle.column().padding(10),List.of(action.node(LayoutStyle.column().size(Length.points(120),Length.points(40)))),null,0);
                var tree=new LayoutTree(root);var logical=new Viewport(240/uiScale,240/uiScale,density*uiScale);
                var screen=new Viewport(240,240,density);
                var frame=PresentationFrame.create(tree,f.engine.layout(tree,logical),logical,Map.of(),new PresentationFrame.Offset(0,0),Map.of(action.id(),new EntrancePose(.25f,7.5f,1))).scaled(uiScale);
                surface.getCanvas().scale(density,density);
                f.session.paint(surface.getCanvas(),frame,screen,new FrameTime(1,0,.016),false);
                var icon=frame.box(new NodeId("action/icon")).border();
                try(var rendered=surface.makeImageSnapshot()) {
                    bitmap.allocN32Pixels((int)(240*density),(int)(240*density));assertTrue(rendered.readPixels(bitmap));
                    int x=(int)((icon.x()+icon.width()/2)*density),y=(int)((icon.y()+icon.height()/2)*density);
                    assertEquals(0xffff0000,bitmap.getColor(x,y),"density="+density+" scale="+uiScale);
                }
                float x=icon.x()+icon.width()/2,y=icon.y()+icon.height()/2;
                f.session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));f.session.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0));
                assertEquals(1,calls[0],"hit uses displayed logical coordinates at density="+density+" scale="+uiScale);
            }
        }
    }
    @Test void strokeWidthAndAnimatedPhaseFollowTheSameDeviceTransform() {
        for(int density:new int[]{1,2,3})for(int shift:new int[]{0,1}) {
            try(var surface=Surface.makeRasterN32Premul(40*density,40*density);var bitmap=new Bitmap()) {
                var c=surface.getCanvas();c.clear(0);c.scale(density,density);c.translate(0,shift/(float)density);
                SkiaDraw.drawEdgeHighlight(c,10,10,20,20,0,1,0xffffffff);
                try(var image=surface.makeImageSnapshot()) {
                    bitmap.allocN32Pixels(40*density,40*density);assertTrue(image.readPixels(bitmap));
                    int x=20*density,top=10*density+shift;
                    assertEquals(0,bitmap.getColor(x,top-1)>>>24);
                    for(int y=top;y<top+density;y++)assertTrue((bitmap.getColor(x,y)>>>24)>200,"stroke must span its logical width");
                    assertEquals(0,bitmap.getColor(x,top+density)>>>24);
                }
            }
        }
    }
    @Test void rescaledScrollFrameUsesTheSamePixelsClickAndWheelCoordinates() {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(480,480);var bitmap=new Bitmap()) {
            int[] calls={0};var action=new ActionButton(new NodeId("action"),f.context,new ActionModel("Go",true,ActionModel.Role.SEGMENT,true),()->calls[0]++);
            action.icon((canvas,r,opacity)->{try(var paint=new Paint().setColor(0xffff0000)){canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height()),paint);}},20);
            var controls=new ArrayList<Control>();controls.add(action);
            for(int i=0;i<5;i++)controls.add(new Toggle(new NodeId("row"+i),f.context,new ToggleModel("Row","",false,true),ignored->{}));
            f.session.setControls(controls);var scroll=new ScrollViewport();f.session.scrollViewport(scroll);
            var children=controls.stream().map(c->c.node(LayoutStyle.column().size(Length.points(160),Length.auto()).flex(0,0,Length.auto()))).toList();
            var root=new LayoutNode(new NodeId("scroll"),LayoutStyle.column().size(Length.points(180),Length.auto()).behavior(false,false,true,true),children,null,0);
            var tree=new LayoutTree(root);var logical=new Viewport(240,240,1);var screen=new Viewport(480,480,1);
            var frame=scroll.present(tree,f.engine.layoutIntrinsicHeight(tree,logical),logical,new com.atom.lib.geometry.Rect(10,10,180,100),1).scaled(2);
            f.session.paint(surface.getCanvas(),frame,screen,new FrameTime(1,0,.016),false);
            var icon=frame.box(new NodeId("action/icon")).border();float x=icon.x()+icon.width()/2,y=icon.y()+icon.height()/2;
            try(var image=surface.makeImageSnapshot()){bitmap.allocN32Pixels(480,480);assertTrue(image.readPixels(bitmap));assertEquals(0xffff0000,bitmap.getColor((int)x,(int)y));}
            f.session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));f.session.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0));assertEquals(1,calls[0]);
            assertEquals(com.atom.lib.input.InputResult.CONSUMED,f.session.wheel(30,30,0,-1));assertTrue(scroll.offset()>0);
            assertEquals(new com.atom.lib.geometry.Rect(20,20,360,200),scroll.bounds());
        }
    }
}
