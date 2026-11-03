// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;

import com.atom.lib.design.atomchat.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.input.PointerEvent;
import io.github.humbleui.skija.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsGroupCompositeTest {
    @Test void shellFadesWholeGroupOnceOverAnOpaqueBackgroundAndKeepsHitGeometry() {
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)) {
            var tabs=new SegmentedChoice(new NodeId("tabs"),f.context,new ChoiceModel(List.of(new ChoiceModel.Option("a","A",true)),"a"),ignored->{});
            var group=new SettingsGroup(new NodeId("group"),new Disclosure(new NodeId("heading"),f.context,"Group","layout",true,true,ignored->{}),List.of(f.slider));
            var controls=new ArrayList<Control>(tabs.controls());controls.addAll(group.controls());f.session.setControls(controls);
            var viewport=new Viewport(560,400,1);var style=new SettingsPageStyle(480,false,1,1,.93f,.235f);
            int[][] pixels=new int[3][];float[] alpha={0,.5f,1};PresentationFrame displayed=null;
            for(int i=0;i<3;i++) {
                var state=shell.layout(f.session,viewport,style,"Title","12:00",tabs,List.of(group),List.of(),Map.of(group.id(),new EntrancePose(0,0,alpha[i])));
                displayed=state.presentation();
                try(var surface=Surface.makeRasterN32Premul(560,400);var bitmap=new Bitmap()) {
                    surface.getCanvas().clear(0xff294766);shell.paint(surface.getCanvas(),state,f.session,new FrameTime(i+1,i*16000000L,.016),false);
                    try(var image=surface.makeImageSnapshot()) {bitmap.allocN32Pixels(560,400);assertTrue(image.readPixels(bitmap));pixels[i]=new int[560*400];
                        for(int y=0;y<400;y++)for(int x=0;x<560;x++)pixels[i][y*560+x]=bitmap.getColor(x,y);}
                }
            }
            int maxError=0,changed=0;
            for(int i=0;i<pixels[0].length;i++)for(int shift:new int[]{0,8,16}) {
                int zero=pixels[0][i]>>shift&255,half=pixels[1][i]>>shift&255,full=pixels[2][i]>>shift&255;
                if(Math.abs(full-zero)>8){changed++;maxError=Math.max(maxError,Math.abs(2*half-zero-full));}
            }
            assertTrue(changed>100);assertTrue(maxError<=5,"half-opacity must interpolate the whole group once; error="+maxError);
            var track=displayed.box(new NodeId("width/track")).content();
            float x=track.x()+track.width()*.8f,y=track.y()+track.height()/2;
            f.session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));f.session.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0));
            assertTrue(f.slider.previewValue()>480,"hit snapshot follows the displayed group");
        }
    }
    @Test void twoGroupsUseIndependentEntranceLayersAndRestoreCanvas() {
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)) {
            var tabs=new SegmentedChoice(new NodeId("tabs"),f.context,new ChoiceModel(List.of(new ChoiceModel.Option("a","A",true)),"a"),ignored->{});
            var one=new Toggle(new NodeId("one"),f.context,new ToggleModel("One","",false,true),ignored->{});
            var two=new Toggle(new NodeId("two"),f.context,new ToggleModel("Two","",false,true),ignored->{});
            var first=new SettingsGroup(new NodeId("g1"),new Disclosure(new NodeId("h1"),f.context,"First","layout",true,true,ignored->{}),List.of(one));
            var second=new SettingsGroup(new NodeId("g2"),new Disclosure(new NodeId("h2"),f.context,"Second","layout",true,true,ignored->{}),List.of(two));
            var controls=new ArrayList<Control>(tabs.controls());controls.addAll(first.controls());controls.addAll(second.controls());f.session.setControls(controls);
            var vp=new Viewport(560,600,1);var style=new SettingsPageStyle(480,false,1,1,.93f,.235f);int[][] pixels=new int[3][];
            float[][] opacity={{0,0},{1,1},{.25f,.75f}};PresentationFrame last=null;
            for(int i=0;i<3;i++) {
                var frame=shell.layout(f.session,vp,style,"Title","12:00",tabs,List.of(first,second),List.of(),Map.of(first.id(),new EntrancePose(0,0,opacity[i][0]),second.id(),new EntrancePose(0,0,opacity[i][1])));
                last=frame.presentation();
                try(var surface=Surface.makeRasterN32Premul(560,600);var bitmap=new Bitmap()) {
                    var canvas=surface.getCanvas();canvas.clear(0xff204060);int saved=canvas.getSaveCount();shell.paint(canvas,frame,f.session,new FrameTime(i+1,i*16000000L,.016),false);assertEquals(saved,canvas.getSaveCount());
                    try(var image=surface.makeImageSnapshot()){bitmap.allocN32Pixels(560,600);assertTrue(image.readPixels(bitmap));pixels[i]=new int[560*600];for(int y=0;y<600;y++)for(int x=0;x<560;x++)pixels[i][y*560+x]=bitmap.getColor(x,y);}
                }
            }
            for(int g=0;g<2;g++) {
                var r=last.box(new NodeId("g"+(g+1)+"/card")).border().intersect(last.box(new NodeId("g"+(g+1)+"/card")).clip());
                int compared=0,max=0;
                for(int y=(int)r.y()+5;y<r.bottom()-5;y++)for(int x=(int)r.x()+5;x<r.right()-5;x++)for(int shift:new int[]{0,8,16}) {
                    int p=y*560+x,a=pixels[0][p]>>shift&255,b=pixels[1][p]>>shift&255,c=pixels[2][p]>>shift&255;
                    if(Math.abs(b-a)>8){compared++;max=Math.max(max,Math.abs(4*c-(g==0?3*a+b:a+3*b)));}
                }
                assertTrue(compared>20);assertTrue(max<=10,"group "+g+" must have its own opacity; error="+max);
            }
        }
    }
    @Test void failedDecorationRestoresCanvasAndKeepsPreviousInputSnapshot() {
        try(var f=new ControlFixture();var surface=Surface.makeRasterN32Premul(480,860)) {
            f.show(480);var frame=f.frame;var box=frame.box(new NodeId("width/track")).content();
            var canvas=surface.getCanvas();int save=canvas.getSaveCount();
            assertThrows(IllegalStateException.class,()->f.session.paint(canvas,frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16000000,.016),false,
                    Map.of(new NodeId("root"),c->{throw new IllegalStateException("paint failed");})));
            assertEquals(save,canvas.getSaveCount());
            assertThrows(IllegalArgumentException.class,()->f.session.paint(canvas,frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16000000,.016),false,Map.of(new NodeId("missing"),c->{})));
            float x=box.x()+box.width()*.8f,y=box.y()+box.height()/2;
            f.session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));f.session.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0));
            assertTrue(f.slider.previewValue()>480);
        }
    }
    @Test void decorationCannotLeakCanvasTransformIntoFollowingControls() {
        try(var f=new ControlFixture();var first=Surface.makeRasterN32Premul(480,860);var second=Surface.makeRasterN32Premul(480,860)) {
            f.show(480);
            f.session.paint(first.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16000000,.016),false,Map.of());
            f.session.paint(second.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16000000,.016),false,Map.of(new NodeId("root"),c->c.translate(1000,1000)));
            try(var a=first.makeImageSnapshot();var b=second.makeImageSnapshot();var ad=a.encodeToData();var bd=b.encodeToData()) {
                assertArrayEquals(ad.getBytes(),bd.getBytes(),"decoration transform must not affect control pixels");
            }
        }
    }
}
