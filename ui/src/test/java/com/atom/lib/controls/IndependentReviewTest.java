package com.atom.lib.controls;

import com.atom.lib.input.*;
import com.atom.lib.layout.NodeId;
import com.atom.lib.layout.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.design.atomchat.*;
import io.github.humbleui.skija.Surface;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndependentReviewTest {
    @Test void presentationLayerOrderOwnsBothPaintingAndHitTesting() {
        try (var f = new ControlFixture()) {
            var aStyle = new AtomChatControlStyle(20, false, 0xff00ff00, -1, 1, 0xffaa2222, 0xff111111, -1);
            var bStyle = new AtomChatControlStyle(20, false, 0xff00ff00, -1, 1, 0xff2222aa, 0xff111111, -1);
            var selected = new ArrayList<String>();
            var a = new Toggle(new NodeId("a"), new ControlContext(f.text, new AtomChatControlSkin(aStyle)),
                new ToggleModel("开关", "", false, true), value -> selected.add("a"));
            var b = new Toggle(new NodeId("b"), new ControlContext(f.text, new AtomChatControlSkin(bStyle)),
                new ToggleModel("开关", "", false, true), value -> selected.add("b"));
            f.session.setControls(List.of(a, b));
            var viewport = new Viewport(480, 860, 1);
            var at = new LayoutTree(a.node(LayoutStyle.column()));
            var bt = new LayoutTree(b.node(LayoutStyle.column()));
            var af = PresentationFrame.create(at, f.engine.layout(at, viewport), viewport, Map.of(), new PresentationFrame.Offset(0, 0));
            var bf = PresentationFrame.create(bt, f.modalEngine.layout(bt, viewport), viewport, Map.of(), new PresentationFrame.Offset(0, 0));
            var frame = PresentationFrame.compose(List.of(bf, af)); // A is visibly above B.
            byte[] first;
            try (var surface = Surface.makeRasterN32Premul(480, 860)) {
                f.session.paint(surface.getCanvas(), frame, viewport, new FrameTime(1, 1, 0), false);
                try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) { first = data.getBytes(); }
            }
            f.session.setControls(List.of(b, a)); // Registration order must not change layers.
            try (var surface = Surface.makeRasterN32Premul(480, 860)) {
                f.session.paint(surface.getCanvas(), frame, viewport, new FrameTime(2, 2, 0), false);
                try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) {
                    assertArrayEquals(first, data.getBytes(), "Drawing must follow PresentationFrame layer order");
                }
            }
            var root = frame.box(a.id()).border();
            float x = root.x() + root.width() / 2, y = root.y() + root.height() / 2;
            f.pointer(PointerEvent.Type.PRESS, x, y, 0);
            f.pointer(PointerEvent.Type.RELEASE, x, y, 0);
            assertEquals(List.of("a"), selected);
        }
    }

    @Test void clickingDialogBodyDoesNotActAsBackdropCancellation() {
        try (var f = new ControlFixture()) {
            var results = new ArrayList<ConfirmationResult>();
            var dialog = new ConfirmationDialog(new NodeId("review-dialog"), f.context,
                new ConfirmationModel("确认？", "点击这里不应关闭对话框。", "取消", "确认"));
            f.show(480);
            f.session.open(dialog, results::add);
            f.show(480);
            var body = f.frame.box(new NodeId("review-dialog/body")).border();
            float x = body.x() + body.width() / 2, y = body.y() + body.height() / 2;
            assertEquals(InputResult.CONSUMED, f.pointer(PointerEvent.Type.PRESS, x, y, 0));
            assertEquals(InputResult.CONSUMED, f.pointer(PointerEvent.Type.RELEASE, x, y, 0));
            assertTrue(results.isEmpty(), "Dialog body is not the backdrop");
            f.key(KeyEvent.Key.ESCAPE, true);
            assertEquals(List.of(ConfirmationResult.CANCELLED), results);
        }
    }

    @Test void escapeCancelsTheHeldAdjustmentUntilItsRelease() {
        try (var f = new ControlFixture()) {
            f.show(480);
            f.key(KeyEvent.Key.TAB, true);
            f.key(KeyEvent.Key.TAB, false);
            f.key(KeyEvent.Key.RIGHT, true);
            f.key(KeyEvent.Key.ESCAPE, true);
            f.key(KeyEvent.Key.ESCAPE, false);
            f.key(KeyEvent.Key.RIGHT, true); // repeat of the still-held key
            f.key(KeyEvent.Key.RIGHT, false);
            assertEquals(List.of(480f), f.cancels);
            assertTrue(f.commits.isEmpty(), "Cancelled key gesture must not restart from a repeat");
            assertEquals(480, f.slider.previewValue());
            f.key(KeyEvent.Key.RIGHT, true);
            f.key(KeyEvent.Key.RIGHT, false);
            assertEquals(List.of(490f), f.commits);
        }
    }
}
