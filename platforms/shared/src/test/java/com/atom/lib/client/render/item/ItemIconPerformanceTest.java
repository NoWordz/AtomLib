// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render.item;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.client.render.*;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class ItemIconPerformanceTest extends HiddenGlFixture {
    @TempDir Path directory;

    @Test void emptyFrameDoesNotInitializeSkiaOrCaptureGlOnPrepareAndRelease() throws Exception {
        try(var runtime=AtomLibRuntime.open();var bridge=new SkijaRenderBridge(runtime);var atlas=new ItemIconAtlas(bridge)) {
            Path file=directory.resolve("empty.jfr");
            try(var recording=new Recording()) {
                recording.enable("com.atomlib.GlStateCapture");recording.start();
                try(var frame=atlas.prepare(List.of(new ItemIconAtlas.Entry("empty",16,true)),1,(t,w,h)->fail("empty pass"))) {
                    assertNotNull(frame.icon("empty"));
                }
                assertThrows(IllegalArgumentException.class,()->atlas.prepare(List.of(),1,(t,w,h)->{}));
                recording.stop();recording.dump(file);
            }
            assertEquals(0,RecordingFile.readAllEvents(file).stream().filter(e->e.getEventType().getName().equals("com.atomlib.GlStateCapture")).count(),"Empty icon frames have no GL resource dependency");
        }
    }

    @Test void reflectionMetadataIsReusedButMutableValuesAreCapturedEveryTime() throws Exception {
        var fields=ItemRenderState.class.getDeclaredMethod("fields",Class.class);fields.setAccessible(true);
        assertSame(fields.invoke(null,ItemRenderStateTest.Cache.class),fields.invoke(null,ItemRenderStateTest.Cache.class));
        // Replacements must be inspected anew even though the class's Field objects are cached.
        var array=ItemRenderStateTest.Cache.class.getDeclaredField("TEXTURES");array.setAccessible(true);
        var original=(Object[])array.get(null);
        for(int binding:new int[]{13,29}) {
            var field=original[0].getClass().getDeclaredField("binding");field.setAccessible(true);field.setInt(original[0],binding);
            try(var state=new ItemRenderState(ItemRenderStateTest.Globals.class,ItemRenderStateTest.Cache.class,ItemRenderStateTest.Shader.class)) {field.setInt(original[0],99);}
            assertEquals(binding,field.getInt(original[0]));
        }
        var field=original[0].getClass().getDeclaredField("binding");field.setAccessible(true);field.setInt(original[0],7);
    }
}
