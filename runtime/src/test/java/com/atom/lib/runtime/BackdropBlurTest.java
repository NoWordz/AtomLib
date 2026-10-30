package com.atom.lib.runtime;

import com.atom.lib.image.BackdropBlur;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackdropBlurTest {
    @Test void capturedBackdropIsBlurredOnlyInsideItsClipAndNullUsesOpaqueFallback(){
        try(var source=Surface.makeRasterN32Premul(32,32)){
            var c=source.getCanvas();c.clear(0xff000000);
            try(var paint=new Paint().setColor(0xffffffff)){c.drawRect(io.github.humbleui.types.Rect.makeXYWH(14,0,4,32),paint);}
            try(var image=source.makeImageSnapshot();var target=Surface.makeRasterN32Premul(32,32);var bitmap=Bitmap.makeFromImage(image)){
                target.getCanvas().clear(0xff000000);
                BackdropBlur.draw(target.getCanvas(),image,io.github.humbleui.types.Rect.makeXYWH(0,0,32,32),3,0xff112233);
                try(var out=target.makeImageSnapshot();var pixels=Bitmap.makeFromImage(out)){
                    assertTrue(pixels.getColor(10,16)!=0xff000000);
                    assertTrue(pixels.getColor(11,16)!=0xff000000);
                }
                target.getCanvas().clear(0xff000000);
                BackdropBlur.draw(target.getCanvas(),null,io.github.humbleui.types.Rect.makeXYWH(4,4,8,8),3,0xff112233);
                try(var out=target.makeImageSnapshot();var pixels=Bitmap.makeFromImage(out)){
                    assertEquals(0xff112233,pixels.getColor(8,8));
                    assertEquals(0xff000000,pixels.getColor(2,2));
                }
            }
        }
    }
}
