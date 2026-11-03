package com.atom.lib.host;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.controls.*;
import com.atom.lib.design.atomchat.*;
import com.atom.lib.font.FontRegistry;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.TextService;
import com.atom.lib.text.TextRequest;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A host owns only the model, size and action; the skin owns label geometry and text style. */
class CenteredButtonHostTest {
    @Test void fixedSizeButtonsShareCenteredLabelsAndActivateAtTheirPaintedPosition() {
        try (var runtime = AtomLibRuntime.open("host-example"); var fonts = new FontRegistry(runtime);
             var text = new TextService(fonts); var yoga = new YogaLayoutEngine()) {
            fonts.registerDefaults();
            AtomChatFonts.register(fonts);
            var context = new ControlContext(text, new AtomChatControlSkin(AtomChatControlStyle.defaults()));
            var clicks = new int[1];
            var first = new ActionButton(new NodeId("first"), context,
                    new ActionModel("确认", true, ActionModel.Role.NORMAL, true), () -> clicks[0]++);
            var second = new ActionButton(new NodeId("second"), context,
                    new ActionModel("Apply", true, ActionModel.Role.NORMAL, true), () -> clicks[0]++);
            try (var session = new UiSession(List.of(first, second)); var surface = Surface.makeRasterN32Premul(320, 200)) {
                var viewport = new Viewport(320, 200, 1);
                session.prepare(viewport);
                var buttonSize = LayoutStyle.column().size(Length.points(180), Length.points(60));
                var tree = new LayoutTree(new LayoutNode(new NodeId("root"),
                        LayoutStyle.column().spacing(10, 10), List.of(first.node(buttonSize), second.node(buttonSize)), null, 0));
                var frame = PresentationFrame.create(tree, yoga.layout(tree, viewport), viewport, Map.of(), new PresentationFrame.Offset(0, 0));
                for (String id : List.of("first", "second")) {
                    var button = frame.box(new NodeId(id)).border();
                    var label = frame.box(new NodeId(id + "/text")).content();
                    String caption = id.equals("first") ? "确认" : "Apply";
                    var expected = text.layout(new TextRequest(caption, AtomChatFonts.BODY, UiTokens.FONT_BUTTON, 0xff000000),
                            new Constraints(Constraints.Axis.atMost(label.width()), Constraints.Axis.undefined()));
                    assertEquals(button.x() + button.width()/2, label.x() + label.width()/2, 1f);
                    assertEquals(button.y() + button.height()/2, label.y() + label.height()/2, 1f);
                    assertEquals(expected.size().height(), label.height(), 2f, "both labels use the skin's button text role, allowing Yoga grid rounding");
                    assertEquals(180, button.width(), 1f);
                    assertEquals(60, button.height(), 1f);
                }
                surface.getCanvas().clear(0xffffffff);
                session.paint(surface.getCanvas(), frame, viewport, new FrameTime(1, 1, 0), false);
                try (var image = surface.makeImageSnapshot(); var bitmap = new Bitmap()) {
                    bitmap.allocN32Pixels(320, 200); assertTrue(image.readPixels(bitmap));
                    var button = frame.box(new NodeId("first")).border();
                    assertNotEquals(0xffffffff, bitmap.getColor((int)button.x()+8, (int)(button.y()+button.height()/2)));
                }
                var button = frame.box(new NodeId("first")).border();
                float x = button.x()+button.width()/2, y = button.y()+button.height()/2;
                assertEquals(InputResult.CONSUMED, session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0)));
                assertEquals(InputResult.CONSUMED, session.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0)));
                assertEquals(1, clicks[0]);
            }
        }
    }
}
