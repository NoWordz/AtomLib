package com.atom.lib.controls;

import com.atom.lib.design.atomdefault.AtomDefault;
import com.atom.lib.design.atomchat.AtomChatFonts;
import com.atom.lib.font.FontSource;
import com.atom.lib.layout.NodeId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomDefaultComparisonTest {
    @Test void interleavedHostsKeepTheirOwnRenderedPalette() throws Exception {
        try (var light = new ControlFixture(); var dark = new ControlFixture()) {
            light.skin.updateStyle(AtomDefault.skin(AtomDefault.presets().get(0)).style());
            dark.skin.updateStyle(AtomDefault.skin(AtomDefault.presets().get(3)).style());
            light.session.setControls(List.of(light.slider,new Toggle(new NodeId("switch"), light.context,
                    new ToggleModel("开关", "预设颜色", true, true), ignored -> {})));
            dark.session.setControls(List.of(dark.slider,new Toggle(new NodeId("switch"), dark.context,
                    new ToggleModel("开关", "预设颜色", true, true), ignored -> {})));
            light.show(480); dark.show(480);
            byte[] before = SliderRenderTest.render(light);
            byte[] comparison = SliderRenderTest.render(dark);
            assertFalse(Arrays.equals(before,comparison));
            assertArrayEquals(before,SliderRenderTest.render(light));
            String output = System.getProperty("atomdefault.previewDir");
            if (output != null) {
                Path directory = Path.of(output);
                Files.createDirectories(directory);
                Files.write(directory.resolve("minimal-controls.png"),before);
                Files.write(directory.resolve("raven-controls.png"),comparison);
            }
        }
    }

    @Test void changingOneHostsBodyFontDoesNotChangeTheOtherHostsDrawing() {
        try (var left = new ControlFixture(); var right = new ControlFixture()) {
            left.show(480); right.show(480);
            byte[] leftBefore=SliderRenderTest.render(left);
            byte[] rightBefore=SliderRenderTest.render(right);
            right.fonts.register(AtomChatFonts.BODY, FontSource.bundledBold());
            right.show(480);
            assertFalse(Arrays.equals(rightBefore,SliderRenderTest.render(right)),"The changed body role must affect real glyphs");
            assertArrayEquals(leftBefore,SliderRenderTest.render(left));
        }
    }
}
