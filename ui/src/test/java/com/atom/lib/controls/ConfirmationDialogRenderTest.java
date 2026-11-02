package com.atom.lib.controls;
import com.atom.lib.layout.*;
import com.atom.lib.motion.*;
import com.atom.lib.geometry.*;
import io.github.humbleui.skija.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfirmationDialogRenderTest {
    @Test void centeredDialogFocusChangesRealPixelsAndRestoresCanvasStack(){
        try(var f=new ControlFixture()){
            f.show(280);f.session.open(new ConfirmationDialog(new NodeId("dialog"),f.context,new ConfirmationModel("确认重置？","中文说明。这里不应把句号放在新行开头。","取消","确认")),r->{});f.show(280);
            var box=f.frame.box(new NodeId("dialog")).border();assertEquals((280-box.width())/2,box.x(),1.01);assertEquals((860-box.height())/2,box.y(),1.01);
            try(var surface=Surface.makeRasterN32Premul(280,860)){
                var c=surface.getCanvas();int saved=c.getSaveCount();f.session.paint(c,f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16000000,.016),false);assertEquals(saved,c.getSaveCount());
                byte[] initial;try(var im=surface.makeImageSnapshot();var data=im.encodeToData()){initial=data.getBytes();}
                f.key(com.atom.lib.input.KeyEvent.Key.TAB,true);f.key(com.atom.lib.input.KeyEvent.Key.TAB,false);c.clear(0);
                f.session.paint(c,f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16000000,.016),false);
                try(var im=surface.makeImageSnapshot();var data=im.encodeToData()){assertFalse(Arrays.equals(initial,data.getBytes()));}
                assertEquals(saved,c.getSaveCount());
            }
        }
    }
}
