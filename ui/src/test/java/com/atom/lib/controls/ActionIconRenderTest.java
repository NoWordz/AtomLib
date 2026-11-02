package com.atom.lib.controls;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActionIconRenderTest {
    @Test void bitmapSlotKeepsMeasuredGeometryAndClipsScaledPixels(){
        try(var f=new ControlFixture();var iconSurface=Surface.makeRasterN32Premul(4,4)){
            iconSurface.getCanvas().clear(0xffff0000);
            try(var image=iconSurface.makeImageSnapshot()){
                var b=new ActionButton(new NodeId("action"),f.context,new ActionModel("Label",true,ActionModel.Role.NORMAL),()->{});
                b.icon(null,24);f.session.setControls(List.of(b));f.show(480,2);var before=f.frame.box(new NodeId("action/icon")).border();
                b.icon((canvas,r,opacity)->{try(var p=new Paint().setAlphaf(opacity)){canvas.drawImageRect(image,io.github.humbleui.types.Rect.makeXYWH(r.x()-20,r.y(),r.width()+40,r.height()),p);}},24);
                f.show(480,2);assertEquals(before,f.frame.box(new NodeId("action/icon")).border());assertTrue(before.right()<=f.frame.box(new NodeId("action/text")).border().x());
                try(var surface=Surface.makeRasterN32Premul(960,1720);var bitmap=new Bitmap()){
                    surface.getCanvas().scale(2,2);f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16000000,.016),false);
                    try(var result=surface.makeImageSnapshot()){bitmap.allocN32Pixels(960,1720);assertTrue(result.readPixels(bitmap));int x=Math.round((before.x()+before.width()/2)*2),y=Math.round((before.y()+before.height()/2)*2);assertEquals(0xffff0000,bitmap.getColor(x,y));assertNotEquals(0xffff0000,bitmap.getColor(Math.round((before.right()+2)*2),y));}
                }
                b.icon(null,0);f.show(480);assertFalse(f.frame.paintOrder().contains(new NodeId("action/icon")));
            }
        }
    }
    @Test void legacyStringIconAndImageSlotCannotCompete(){try(var f=new ControlFixture()){
        var b=new ActionButton(new NodeId("action"),f.context,new ActionModel("Label",true,ActionModel.Role.NORMAL,false,"layers"),()->{});
        assertThrows(IllegalArgumentException.class,()->b.icon(null,24));
    }}

    @Test void halfClippedSlotAndModalScrimUseTheDisplayedFrame(){
        try(var f=new ControlFixture();var imageSurface=Surface.makeRasterN32Premul(4,4)){
            imageSurface.getCanvas().clear(0xffff0000);try(var image=imageSurface.makeImageSnapshot()){
                var b=new ActionButton(new NodeId("action"),f.context,new ActionModel("Label",true,ActionModel.Role.NORMAL),()->{});
                b.icon((c,r,a)->{try(var p=new Paint().setAlphaf(a)){c.drawImageRect(image,io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height()),p);}},24);
                f.session.setControls(List.of(b));var scroll=new ScrollViewport();f.session.scrollViewport(scroll);
                var vp=new com.atom.lib.geometry.Viewport(480,860,1);var tree=new LayoutTree(new LayoutNode(new NodeId("clip-root"),LayoutStyle.column().size(Length.points(480),Length.auto()).behavior(false,false,true,true),List.of(b.node(LayoutStyle.column())),null,0));
                var frame=scroll.present(tree,f.engine.layoutIntrinsicHeight(tree,vp),vp,new com.atom.lib.geometry.Rect(0,0,480,18),1);
                try(var surface=Surface.makeRasterN32Premul(480,860);var bitmap=new Bitmap()){
                    surface.getCanvas().clear(0xffffffff);f.session.paint(surface.getCanvas(),frame,vp,new FrameTime(++f.ticks,f.ticks*16000000,.016),false);
                    try(var snapshot=surface.makeImageSnapshot()){bitmap.allocN32Pixels(480,860);assertTrue(snapshot.readPixels(bitmap));assertEquals(0xffff0000,bitmap.getColor(12,10));assertEquals(0xffffffff,bitmap.getColor(12,25));}
                }
                f.session.scrollViewport(null);f.show(480);var r=f.frame.box(new NodeId("action/icon")).border();
                f.session.open(new ModalDialog(new NodeId("modal"),f.context,new ModalOptions("Modal",250,10,false,null)),reason->{});f.show(480);
                try(var surface=Surface.makeRasterN32Premul(480,860);var bitmap=new Bitmap()){
                    f.session.paint(surface.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16000000,.016),false);
                    try(var snapshot=surface.makeImageSnapshot()){bitmap.allocN32Pixels(480,860);assertTrue(snapshot.readPixels(bitmap));int red=bitmap.getColor(Math.round(r.x()+r.width()/2),Math.round(r.y()+r.height()/2))>>16&255;assertTrue(red>0&&red<150,"modal scrim covers the icon in the same Skija layer");}
                }
            }
        }
    }
}
