package com.atom.lib.overlay;

import com.atom.lib.component.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.KeyEvent;
import com.atom.lib.t8.T8Host;
import com.atom.lib.view.UiView;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OverlayContentViewTest {
    @Test void removingConfirmationContentFromTheViewCancelsExactlyOnce(){
        try(var h=new T8Host()){
            int[] cancelled={0};var content=new ConfirmationDialog("confirmation","Reset","Changes will be discarded",()->fail("must not confirm"),()->cancelled[0]++);
            try(var view=new UiView(h.host,new Section("base",Section.Kind.ROOT,List.of()),new Viewport(400,400,1))){
                view.showOverlay(OverlaySpec.centered(content));view.key(new KeyEvent(KeyEvent.Key.ESCAPE,true,false,false,false));
                assertEquals(1,cancelled[0]);assertFalse(content.isOpen());assertTrue(view.overlaySnapshot().isEmpty());content.dismiss();assertEquals(1,cancelled[0]);
            }
        }
    }
    @Test void confirmingColorContentRemovesTheModalInsteadOfLeavingAnInvisibleBlocker(){
        try(var h=new T8Host()){
            var binding=new OverlayComponentsTest.ColorBinding(0xff336699);var picker=new ColorPicker("picker",binding);
            try(var view=new UiView(h.host,new Section("base",Section.Kind.ROOT,List.of()),new Viewport(400,400,1))){
                view.showOverlay(OverlaySpec.centered(picker));var button=picker.confirmBounds();
                view.pointer(new com.atom.lib.input.PointerEvent(com.atom.lib.input.PointerEvent.Type.PRESS,button.x()+1,button.y()+1,0));
                view.pointer(new com.atom.lib.input.PointerEvent(com.atom.lib.input.PointerEvent.Type.RELEASE,button.x()+1,button.y()+1,0));
                assertEquals(1,binding.commits);assertEquals(0,binding.cancels);assertTrue(view.overlaySnapshot().isEmpty());
            }
        }
    }
}
