package com.atom.lib.controls;

import com.atom.lib.design.atomdefault.AtomDefault;
import com.atom.lib.design.atomchat.AtomChatControlSkin;
import com.atom.lib.layout.NodeId;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Surface;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomDefaultModalTest {
    @Test void legacySkinConstructorRetainsItsDarkDialogSurface() throws Exception {
        try (var f = new ControlFixture(); var surface = Surface.makeRasterN32Premul(480,860)) {
            var style=AtomDefault.skin(AtomDefault.presets().get(0)).style();
            var context=new ControlContext(f.text,new AtomChatControlSkin(style));
            f.show(480);
            f.session.open(new ConfirmationDialog(new NodeId("dialog"),context,
                    new ConfirmationModel("Title","Body","Cancel","OK")),r->{});
            f.show(480);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,
                    new FrameTime(++f.ticks,f.ticks*16_000_000,.016),false);
            var box=f.frame.box(new NodeId("dialog")).border();
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) {
                int color=ImageIO.read(new ByteArrayInputStream(data.getBytes())).getRGB(
                        Math.round(box.x()+8),Math.round(box.y()+box.height()/2));
                assertTrue(((color>>16)&255)<80,"Legacy constructor retains its original dark surface");
            }
        }
    }

    @Test void lightPaletteKeepsDialogSurfaceLightForDarkText() throws Exception {
        try (var f = new ControlFixture(); var surface = Surface.makeRasterN32Premul(480,860)) {
            var skin = AtomDefault.skin(AtomDefault.presets().get(0));
            var context = new ControlContext(f.text, skin);
            f.show(480);
            f.session.open(new ConfirmationDialog(new NodeId("dialog"), context,
                    new ConfirmationModel("Title", "Body", "Cancel", "OK")), r -> {});
            f.show(480);
            f.session.paint(surface.getCanvas(), f.frame, f.viewport,
                    new FrameTime(++f.ticks, f.ticks * 16_000_000, .016), false);
            var box = f.frame.box(new NodeId("dialog")).border();
            try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) {
                var pixels = ImageIO.read(new ByteArrayInputStream(data.getBytes()));
                int color = pixels.getRGB(Math.round(box.x()+8),Math.round(box.y()+box.height()/2));
                assertTrue(((color >> 16) & 255) > 180, "A light preset must not paint a dark dialog surface");
            }
        }
    }

    @Test void lightPaletteAlsoAppliesToComposableModalSurface() throws Exception {
        try (var f = new ControlFixture(); var surface = Surface.makeRasterN32Premul(480,860)) {
            var context=new ControlContext(f.text,AtomDefault.skin(AtomDefault.presets().get(0)));
            var modal=new ModalDialog(new NodeId("modal"),context,new ModalOptions("Title",280,20,true,null));
            f.show(480); f.session.open(modal,r->{}); f.show(480);
            f.session.paint(surface.getCanvas(),f.frame,f.viewport,
                    new FrameTime(++f.ticks,f.ticks*16_000_000,.016),false);
            var box=f.frame.box(new NodeId("modal")).border();
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) {
                int color=ImageIO.read(new ByteArrayInputStream(data.getBytes())).getRGB(
                        Math.round(box.x()+8),Math.round(box.y()+box.height()/2));
                assertTrue(((color>>16)&255)>180,"Composable modal must use the same light surface");
            }
        }
    }
}
