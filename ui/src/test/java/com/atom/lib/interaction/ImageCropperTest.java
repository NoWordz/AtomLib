// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.interaction;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.PointerEvent;
import com.atom.lib.layout.LayoutBox;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class ImageCropperTest {
    @Test void panCoverCentreZoomPngCallbackAndCancelUseSameSourceGeometry(){
        try(var host=new TestHost();var source=Surface.makeRasterN32Premul(400,200)){
            source.getCanvas().clear(0xff0000ff);
            try(var paint=new Paint().setColor(0xffff0000)){source.getCanvas().drawRect(io.github.humbleui.types.Rect.makeXYWH(0,0,200,200),paint);}
            try(var image=source.makeImageSnapshot()){
                var binding=new InteractionTest.Binding<>(new CropState(1,0,0));var png=new AtomicReference<byte[]>();
                try(var crop=new ImageCropper("crop",image,50,50,binding,png::set)){
                    var frame=new Rect(0,0,100,100);crop.geometry(new LayoutBox(frame,frame,frame,frame,null,null),host.host);
                    assertTrue(crop.press(new PointerEvent(PointerEvent.Type.PRESS,50,50,0)));
                    crop.drag(new PointerEvent(PointerEvent.Type.MOVE,1000,50,-1));crop.release(new PointerEvent(PointerEvent.Type.RELEASE,1000,50,0));
                    assertEquals(1,binding.commits);assertEquals(0,crop.sourceRect().x(),.01);
                    float sourceCenter=crop.sourceRect().x()+crop.sourceRect().width()/2;
                    crop.zoom(2);
                    assertEquals(sourceCenter,crop.sourceRect().x()+crop.sourceRect().width()/2,.01);
                    assertEquals(2,binding.commits);
                    crop.exportPng();assertNotNull(png.get());
                    try(var output=Image.makeFromEncoded(png.get());var bitmap=new Bitmap()){
                        assertEquals(50,output.getWidth());assertEquals(50,output.getHeight());
                        bitmap.allocN32Pixels(50,50);assertTrue(output.readPixels(bitmap));assertEquals(0xffff0000,bitmap.getColor(25,25));
                    }
                    var before=crop.state();crop.beginEdit();crop.pan(10,20);crop.cancel();assertEquals(before,crop.state());assertEquals(1,binding.cancels);
                }
                assertFalse(image.isClosed());
            }
        }
    }
}
