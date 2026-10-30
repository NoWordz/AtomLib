package com.atom.lib.devtools;

import com.atom.lib.*;
import com.atom.lib.component.*;
import com.atom.lib.devtools.gallery.ComponentGallery;
import com.atom.lib.devtools.inspect.LayoutInspector;
import com.atom.lib.font.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.layout.NodeId;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.TextService;
import com.atom.lib.view.*;
import io.github.humbleui.skija.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GalleryRenderTest {
    @Test void galleryUsesProductionPaintAndInspectorReportsRealGeometry(){
        try(var runtime=AtomLibRuntime.open("gallery-test");var fonts=new FontRegistry(runtime);var text=new TextService(fonts)){
            var id=new FontId("body");fonts.register(id,FontSource.file(Path.of(System.getProperty("atomlib.testFont"))));
            var host=new HostContext(fonts,id,text,ComponentGallery.theme(),()->{});
            var page=new ComponentGallery().page(320);
            try(var view=new UiView(host,page,new Viewport(320,560,1));var surface=Surface.makeRasterN32Premul(320,560)){
                view.paint(surface.getCanvas(),new FrameTime(1,0,.016));
                var inspector=new LayoutInspector();inspector.setEnabled(true);
                var metrics=inspector.inspect(view,new NodeId("sound"),0,2_000_000,1_000_000);
                assertEquals(view.frame().paintOrder().size(),metrics.nodes());
                assertNotNull(metrics.entries().stream().filter(e->e.id().equals(new NodeId("sound"))).findFirst().orElse(null));
                assertEquals(2.0f,metrics.layoutMillis());
                inspector.draw(surface.getCanvas(),host,new NodeId("sound"));
            }
        }
    }
}
