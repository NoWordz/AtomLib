package com.atom.lib.overlay;

import com.atom.lib.component.ValueBinding;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.t8.T8Host;
import com.atom.lib.view.UiView;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OverlayComponentsTest {
    static KeyEvent key(KeyEvent.Key key){return new KeyEvent(key,true,false,false,false);}
    @Test void menuSkipsDisabledItemsAndClosesBeforeAnActionCanReenter(){
        int[] calls={0};Menu[] ref={null};
        var menu=new Menu("menu",List.of(new Menu.Item("off","Disabled",false,()->fail("disabled action")),new Menu.Item("go","Continue",true,()->{calls[0]++;assertFalse(ref[0].isOpen());})));
        ref[0]=menu;assertEquals("go",menu.selectedItem());menu.key(key(KeyEvent.Key.ENTER));menu.key(key(KeyEvent.Key.ENTER));assertEquals(1,calls[0]);
        menu.open();menu.key(key(KeyEvent.Key.ESCAPE));assertFalse(menu.isOpen());
    }
    @Test void confirmationAndEscapeHaveExactlyOneTerminalAction(){
        int[] confirm={0},cancel={0};var dialog=new ConfirmationDialog("confirm","Reset","Continue?",()->confirm[0]++,()->cancel[0]++);
        dialog.key(key(KeyEvent.Key.ESCAPE));dialog.dismiss();assertEquals(1,cancel[0]);assertEquals(0,confirm[0]);
        dialog.open();dialog.confirm();dialog.confirm();assertEquals(1,confirm[0]);assertFalse(dialog.isOpen());
    }
    @Test void colorPreviewStaysSeparateFromCommitAndCancellationRestoresOriginal(){
        var binding=new ColorBinding(0xff336699);var picker=new ColorPicker("color",binding);
        picker.previewHsva(0,1,1,1);assertEquals(0xffff0000,binding.value);assertEquals(0,binding.commits);
        picker.dismiss();assertEquals(0xff336699,binding.value);assertEquals(1,binding.cancels);
        picker.open();picker.previewHsva(120,1,1,.5f);picker.confirm();picker.confirm();assertEquals(0x8000ff00,binding.value);assertEquals(1,binding.commits);
    }
    @Test void aPreviewCanRemoveThePickerWithoutALaterCommit(){
        var binding=new ColorBinding(0xff123456);var picker=new ColorPicker("color",binding);binding.onPreview=picker::dismiss;
        picker.previewHsva(240,1,1,1);picker.confirm();assertEquals(0,binding.commits);assertEquals(1,binding.cancels);assertEquals(0xff123456,binding.value);
    }
    @Test void colorExceptionsKeepThePrimaryFailureAndTerminateTheDraft(){
        var binding=new ColorBinding(0xff123456);var picker=new ColorPicker("color",binding);var failure=new IllegalStateException("preview failed");binding.onPreview=()->{throw failure;};
        assertSame(failure,assertThrows(IllegalStateException.class,()->picker.previewHsva(0,1,1,1)));assertFalse(picker.isOpen());assertEquals(1,binding.cancels);
    }
    @Test void nativePaintProducesPixelsAndPointerUsesPreparedPickerGeometry(){
        try(var h=new T8Host();var surface=Surface.makeRasterN32Premul(400,420)){
            var binding=new ColorBinding(0xff123456);var picker=new ColorPicker("color",binding);
            try(var view=new UiView(h.host,picker,new com.atom.lib.geometry.Viewport(400,420,1))){
                surface.getCanvas().clear(0x00000000);int saved=surface.getCanvas().getSaveCount();view.paint(surface.getCanvas(),new FrameTime(1,0,.016));assertEquals(saved,surface.getCanvas().getSaveCount());
                Rect hue=picker.hueBounds();view.pointer(new PointerEvent(PointerEvent.Type.PRESS,hue.x()+hue.width()/3,hue.y()+hue.height()/2,0));view.pointer(new PointerEvent(PointerEvent.Type.RELEASE,hue.x()+hue.width()/3,hue.y()+hue.height()/2,0));assertEquals(0,binding.commits);
                Rect button=picker.confirmBounds();view.pointer(new PointerEvent(PointerEvent.Type.PRESS,button.x()+1,button.y()+1,0));view.pointer(new PointerEvent(PointerEvent.Type.RELEASE,button.x()+1,button.y()+1,0));assertEquals(1,binding.commits);
                try(var image=surface.makeImageSnapshot();var pixels=new Bitmap()){pixels.allocN32Pixels(400,420);assertTrue(image.readPixels(pixels));assertNotEquals(0,pixels.getColor(200,50));}
            }
        }
    }
    @Test void tooltipAndMenuUseHostFontsAndSurviveRepeatedCpuPaint(){
        try(var h=new T8Host();var surface=Surface.makeRasterN32Premul(300,160)){
            var tooltip=new Tooltip("tip","说明 / tooltip");try(var view=new UiView(h.host,tooltip,new com.atom.lib.geometry.Viewport(300,160,1))){view.paint(surface.getCanvas(),new FrameTime(1,0,.016));h.fonts.reload();view.paint(surface.getCanvas(),new FrameTime(1,0,.016));tooltip.hide();assertTrue(view.frame().paintOrder().isEmpty());}
            var menu=new Menu("menu",List.of(new Menu.Item("go","Continue",true,()->{})));try(var view=new UiView(h.host,menu,new com.atom.lib.geometry.Viewport(300,160,1))){view.paint(surface.getCanvas(),new FrameTime(2,1,.016));assertTrue(view.frame().box(menu.id()).clip().width()>0);}
        }
    }
    static final class ColorBinding implements ValueBinding<Integer>{
        int value,commits,cancels;Runnable onPreview=()->{};ColorBinding(int value){this.value=value;}
        public Integer read(){return value;}public void preview(Integer value){this.value=value;onPreview.run();}public void commit(Integer value){this.value=value;commits++;}public void cancel(Integer value){this.value=value;cancels++;}
    }
}
