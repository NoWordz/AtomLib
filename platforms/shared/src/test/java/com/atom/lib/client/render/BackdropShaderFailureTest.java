// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import com.atom.lib.*;
import com.atom.lib.client.render.backdrop.*;
import com.atom.lib.controls.*;
import com.atom.lib.design.atomchat.*;
import com.atom.lib.font.FontRegistry;
import com.atom.lib.geometry.*;
import com.atom.lib.layout.NodeId;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.*;
import io.github.humbleui.skija.Surface;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

@Tag("broken-shader")
class BackdropShaderFailureTest extends HiddenGlFixture {
    @Test void shaderFailureLeavesDestinationUntouchedAndShellDrawsSolidFallback() throws Exception {
        var source=framebuffer(64,64);var destination=framebuffer(64,64);
        glBindFramebuffer(GL_FRAMEBUFFER,destination.framebuffer());glClearColor(.1f,.2f,.3f,.25f);glClear(GL_COLOR_BUFFER_BIT);
        int[] before=pixel(destination,32,32);
        try(var renderer=new BackdropRenderer()){
            var result=renderer.render(source,destination,new Rect(0,0,64,64),new BackdropStyle(4,0,1,0xff16191f),1,.5f);
            assertFalse(result.drawn());assertTrue(result.diagnostic().contains("Shader compile failed"));
            assertArrayEquals(before,pixel(destination,32,32));
            try(var runtime=AtomLibRuntime.open("fallback");var fonts=new FontRegistry(runtime);var text=new TextService(fonts,TextBreakPolicy.CJK_UI)){
                fonts.registerDefaults();AtomChatFonts.register(fonts);
                var context=new ControlContext(text,new AtomChatControlSkin(AtomChatControlStyle.defaults()));
                var tabs=new SegmentedChoice(new NodeId("tabs"),context,new ChoiceModel(List.of(new ChoiceModel.Option("a","A",true)),"a"),ignored->{});
                try(var shell=new SettingsShell(context);var session=new UiSession(tabs.controls());var surface=Surface.makeRasterN32Premul(560,400)){
                    var viewport=new Viewport(560,400,1);var style=new SettingsPageStyle(480,false,1,1,.93f,.235f);
                    var state=shell.layout(session,viewport,style,"Title","12:00",tabs,List.of(),List.of());
                    shell.paint(surface.getCanvas(),state,session,new FrameTime(1,0,0),false,!result.drawn());
                    try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){
                        var pixel=ImageIO.read(new ByteArrayInputStream(data.getBytes())).getRGB(Math.round(state.panel().x()+25),Math.round(state.panel().y()+state.panel().height()/2));
                        assertEquals(255,pixel>>>24,"Failed blur must produce an opaque panel surface");
                    }
                }
            }
        }
    }
}
