package com.atom.lib.controls;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.*;
import io.github.humbleui.skija.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SliderRenderTest {
    @Test void pressedSliderChangesPixelsAfterFocusIsAlreadyEstablished(){
        try(var f=new ControlFixture()){
            f.show(480);f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);byte[] focused=render(f);
            f.press();assertFalse(Arrays.equals(focused,render(f)),"Pointer down must have its own local feedback");
        }
    }
    @Test void realTrackAndThumbPaintAccentAndFocusChangesPixels() throws Exception {
        try(var f=new ControlFixture()){
            f.show(480);byte[] normal=render(f);f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);byte[] focused=render(f);
            assertFalse(Arrays.equals(normal,focused));var image=ImageIO.read(new ByteArrayInputStream(focused));var r=f.track();
            int accent=f.skin.style().accentColor();int y=Math.round(r.y()+r.height()/2);
            assertEquals(accent,image.getRGB(Math.round(r.x()+5),y));assertEquals(accent,image.getRGB(Math.round(f.thumb()),y));
            for(String role:List.of("title","body","value")){
                var box=f.frame.box(new NodeId("width/"+role)).content();int ink=0;
                for(int iy=(int)Math.ceil(box.y());iy<box.bottom();iy++)for(int ix=(int)Math.ceil(box.x());ix<box.right();ix++){
                    int color=image.getRGB(ix,iy);if((color>>>24)>230&&(color>>16&255)>150&&(color>>8&255)>150&&(color&255)>150)ink++;
                }assertTrue(ink>20,"Missing actual text for "+role);
            }
        }
    }
    static byte[] render(ControlFixture f){
        try(var s=Surface.makeRasterN32Premul(480,860)){
            f.session.paint(s.getCanvas(),f.frame,f.viewport,new FrameTime(++f.ticks,f.ticks*16000000,.016),false);
            try(var image=s.makeImageSnapshot();var data=image.encodeToData()){return data.getBytes();}
        }
    }
}
