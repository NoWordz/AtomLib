package com.atom.lib.component;

import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.t8.T8Host;
import com.atom.lib.view.UiView;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextFieldViewTest {
    @Test void focusTextInputCommitAndANewEditingTransactionUseTheProductionView(){
        try(var h=new T8Host()){
            var binding=new TextFieldEditingTest.Binding("AB");var field=new TextField("field",binding,new TextFieldEditingTest.Clip());
            try(var view=new UiView(h.host,field,new Viewport(240,50,1))){
                view.key(new KeyEvent(KeyEvent.Key.TAB,true,false,false,false));assertTrue(field.isEditing());
                view.textInput("中");view.key(new KeyEvent(KeyEvent.Key.ENTER,true,false,false,false));assertEquals("AB中",binding.value);assertEquals(1,binding.commits);
                view.textInput("!");assertEquals("AB中!",binding.value);
                view.key(new KeyEvent(KeyEvent.Key.ESCAPE,true,false,false,false));assertEquals("AB中",binding.value);assertEquals(1,binding.cancels);
            }
        }
    }
    @Test void realTextSelectionPaintingAndReloadAreClippedAndDoNotAdvanceTheDraft(){
        try(var h=new T8Host();var surface=Surface.makeRasterN32Premul(300,90)){
            var binding=new TextFieldEditingTest.Binding("A very long grapheme-safe text 😀é中文 field");var field=new TextField("field",binding,new TextFieldEditingTest.Clip());field.focus();
            try(var view=new UiView(h.host,field,new Viewport(180,40,1))){
                field.select(0,field.text().length());surface.getCanvas().clear(0);int saved=surface.getCanvas().getSaveCount();
                view.paint(surface.getCanvas(),new FrameTime(1,0,.016));assertTrue(field.horizontalOffset()>0);assertEquals(saved,surface.getCanvas().getSaveCount());
                h.fonts.reload();assertDoesNotThrow(()->view.paint(surface.getCanvas(),new FrameTime(1,0,.016)));assertEquals(0,binding.commits);
                try(var image=surface.makeImageSnapshot();var pixels=new Bitmap()){pixels.allocN32Pixels(300,90);assertTrue(image.readPixels(pixels));assertNotEquals(0,pixels.getColor(30,20));assertEquals(0,pixels.getColor(220,20));}
            }
        }
    }
}
