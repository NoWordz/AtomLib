package com.atom.lib.design;

import com.atom.lib.design.atomchat.SkiaDraw;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.impl.Stats;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlurredBackgroundLifecycleTest {
    @Test void paintAndJavaFilterHaveIndependentNativeOwnership() {
        try(var filter=ImageFilter.makeBlur(2,2,FilterTileMode.CLAMP)) {
            assertFalse(filter.isClosed());int initial=filter.getRefCount();
            try(var paint=new Paint().setImageFilter(filter)) { assertTrue(filter.getRefCount()>initial); }
            assertFalse(filter.isClosed(),"Closing paint does not close the caller's Java filter handle");
            assertEquals(initial,filter.getRefCount());
        }
    }

    @Test void repeatedRealDrawsReleaseTheirFilterHandlesAndKeepTheSourceUsable() throws Exception {
        try(var source=Surface.makeRasterN32Premul(32,32);var target=Surface.makeRasterN32Premul(32,32)) {
            source.getCanvas().clear(0xff40a0e0);
            try(var snapshot=source.makeImageSnapshot()) {
                boolean prior=Stats.enabled;Stats.enabled=true;
                try {
                    int before=Stats.allocated.getOrDefault("ImageFilter",0);
                    for(int i=0;i<8;i++)SkiaDraw.drawBlurredBackground(target.getCanvas(),snapshot,0,0,32,32,0,2);
                    assertEquals(before,Stats.allocated.getOrDefault("ImageFilter",0),"Each Java filter handle must close");
                    assertFalse(snapshot.isClosed());
                    try(var result=target.makeImageSnapshot();var data=result.encodeToData()) {
                        int pixel=ImageIO.read(new ByteArrayInputStream(data.getBytes())).getRGB(16,16);
                        assertEquals(0xff40a0e0,pixel,"A real filtered draw must reach the target");
                    }
                } finally { Stats.enabled=prior; }
            }
        }
    }
}
