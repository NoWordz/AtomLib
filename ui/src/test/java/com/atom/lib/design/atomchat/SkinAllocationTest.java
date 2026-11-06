package com.atom.lib.design.atomchat;

import com.atom.lib.controls.ControlMotion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SkinAllocationTest {
    @Test void constantMotionDoesNotAllocatePerControlPerFrame() {
        var skin=new AtomChatControlSkin(AtomChatControlStyle.defaults());
        var motion=skin.motion();
        assertEquals(new ControlMotion(.09,.14,.11),motion);
        assertSame(motion,skin.motion());
        skin.updateStyle(AtomChatControlStyle.defaults());
        assertSame(motion,skin.motion());
    }
}
