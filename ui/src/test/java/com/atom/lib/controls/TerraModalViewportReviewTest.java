package com.atom.lib.controls;

import com.atom.lib.design.atomchat.*;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Regression: oversized confirmations must share the same fitted paint/hit geometry. */
class TerraModalViewportReviewTest {
    private static final NodeId DIALOG = new NodeId("dialog");
    private final SettingsPageStyle style = new SettingsPageStyle(480,false,1,1,.93f,.235f);

    private SegmentedChoice tabs(ControlFixture f,SettingsShell shell) {
        var tabs=new SegmentedChoice(new NodeId("tabs"),f.context,
            new ChoiceModel(List.of(new ChoiceModel.Option("adjust","调整",true)),"adjust"),value->{});
        f.session.setControls(tabs.controls());
        // Hosts attach the shell before a later user action opens its modal.
        layout(f,shell,tabs,560,400);return tabs;
    }
    private void open(ControlFixture f,List<ConfirmationResult> results) {
        f.session.open(new ConfirmationDialog(DIALOG,f.context,new ConfirmationModel("确认重置？",
            "该操作仅增加内存计数，不删除聊天或修改文件。","取消","确认")),results::add);
    }
    private SettingsShell.Frame layout(ControlFixture f,SettingsShell shell,SegmentedChoice tabs,int w,int h) {
        return shell.layout(f.session,new Viewport(w,h,1),style,"外观","11:16",tabs,List.of(),List.of());
    }
    private void paint(ControlFixture f,SettingsShell shell,SettingsShell.Frame state,Surface surface) {
        shell.paint(surface.getCanvas(),state,f.session,new FrameTime(++f.ticks,f.ticks,0),false);
    }
    private void click(ControlFixture f,float x,float y) {
        assertEquals(InputResult.CONSUMED,f.pointer(PointerEvent.Type.PRESS,x,y,0));
        assertEquals(InputResult.CONSUMED,f.pointer(PointerEvent.Type.RELEASE,x,y,0));
    }

    @ParameterizedTest @CsvSource({"360,180","200,180"})
    void resizedConfirmationFitsAndPaintedButtonActuallyConfirms(int width,int height)throws Exception {
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)) {
            var tabs=tabs(f,shell);var results=new ArrayList<ConfirmationResult>();open(f,results);
            try(var initial=Surface.makeRasterN32Premul(560,400)){paint(f,shell,layout(f,shell,tabs,560,400),initial);}
            var state=layout(f,shell,tabs,width,height);var frame=state.presentation();
            var dialog=frame.box(DIALOG).border();
            assertTrue(dialog.x()>=-.001&&dialog.y()>=-.001&&dialog.right()<=width+.001&&dialog.bottom()<=height+.001,
                "Resized modal must fit without dropping its action buttons: "+dialog);
            assertTrue(frame.renderScale(DIALOG)<1,"Short viewport requires uniform modal fitting");
            var raw=f.modalEngine.layout(new LayoutTree(f.session.modalNode(new Viewport(width,height,1))),new Viewport(width,height,1));
            var label=new NodeId("dialog/cancel/text");
            assertEquals(raw.box(label).content().width(),frame.forPainting(DIALOG).box(label).content().width(),"Painting must retain the measured text width exactly");
            assertEquals(raw.box(label).content().height(),frame.forPainting(DIALOG).box(label).content().height());
            try(var surface=Surface.makeRasterN32Premul(width,height)) {
                assertDoesNotThrow(()->paint(f,shell,state,surface));
                var button=frame.box(new NodeId("dialog/confirm")).border();
                var clip=frame.box(new NodeId("dialog/confirm")).clip();
                assertTrue(button.x()>=clip.x()&&button.y()>=clip.y()&&button.right()<=clip.right()&&button.bottom()<=clip.bottom(),"The full confirm button must fit its clip");
                try(var image=surface.makeImageSnapshot();var png=image.encodeToData(EncodedImageFormat.PNG)) {
                    var pixels=ImageIO.read(new ByteArrayInputStream(png.getBytes()));
                    // Sample measured button padding, outside the text and rounded edge.
                    var textBox=frame.box(new NodeId("dialog/confirm/text")).content();
                    int rgb=pixels.getRGB((int)((button.x()+textBox.x())/2),(int)(button.y()+button.height()*.5f));
                    assertEquals(0xeb4034,rgb&0xffffff,"Paint must apply the same modal scale as hit testing");
                }
                // Blank card corner is not the backdrop dismiss target.
                click(f,dialog.x()+dialog.width()*.1f,dialog.y()+dialog.height()*.05f);
                assertTrue(results.isEmpty());
                click(f,button.x()+button.width()/2,button.y()+button.height()/2);
                assertEquals(List.of(ConfirmationResult.CONFIRMED),results);
            }
        }
    }

    @Test void normalViewportKeepsOriginalUnscaledModalLayout() {
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)) {
            var tabs=tabs(f,shell);open(f,new ArrayList<>());var viewport=new Viewport(560,400,1);
            var node=f.session.modalNode(viewport);var tree=new LayoutTree(node);
            var original=f.modalEngine.layout(tree,viewport).box(DIALOG).border();
            var state=layout(f,shell,tabs,560,400);
            assertEquals(1,state.presentation().renderScale(DIALOG));
            assertEquals(original,state.presentation().box(DIALOG).border());
            try(var surface=Surface.makeRasterN32Premul(560,400)){paint(f,shell,state,surface);}
        }
    }

    @Test void fittedDialogStillHasAFullViewportDismissOverlay() {
        try(var f=new ControlFixture();var shell=new SettingsShell(f.context)) {
            var tabs=tabs(f,shell);var results=new ArrayList<ConfirmationResult>();open(f,results);
            var state=layout(f,shell,tabs,360,180);
            assertEquals(new Rect(0,0,360,180),state.presentation().box(new NodeId("dialog/overlay")).border());
            assertEquals(1,state.presentation().renderScale(new NodeId("dialog/overlay")));
            try(var surface=Surface.makeRasterN32Premul(360,180)){assertDoesNotThrow(()->paint(f,shell,state,surface));}
            click(f,1,1);
            assertEquals(List.of(ConfirmationResult.CANCELLED),results);
        }
    }
}
