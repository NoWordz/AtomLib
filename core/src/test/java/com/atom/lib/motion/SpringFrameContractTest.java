package com.atom.lib.motion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpringFrameContractTest {
    @Test void firstFrameAdvancesAndOlderFrameIsRejected() {
        var spring = new SpringAnim(220,.8f,.001f,.001f);
        spring.setTarget(1);
        spring.update(new FrameTime(3,16_000_000,.016),true);
        assertTrue(spring.value()>0 && spring.value()<1);
        assertThrows(IllegalArgumentException.class,()->spring.update(new FrameTime(2,32_000_000,.016),true));
    }

    @Test void longGapIsClampedAndReducedMotionReachesLatestTargetInSameFrame() {
        var stalled = new SpringAnim(220,.8f,.001f,.001f);
        var limited = new SpringAnim(220,.8f,.001f,.001f);
        stalled.setTarget(1); limited.setTarget(1);
        stalled.update(new FrameTime(1,1_000_000_000,1),true);
        limited.update(new FrameTime(1,50_000_000,.05),true);
        assertEquals(limited.value(),stalled.value(),.000001);
        stalled.setTarget(.25f);
        stalled.update(new FrameTime(1,1_000_000_000,0),false);
        assertEquals(.25f,stalled.value());
        assertEquals(0,stalled.velocity());
    }

    @Test void interruptedTargetsAndDifferentFrameRatesConverge() {
        var fast = new SpringAnim(220,.8f,.001f,.001f);
        var slow = new SpringAnim(220,.8f,.001f,.001f);
        fast.setTarget(1); slow.setTarget(1);
        for(int i=1;i<=120;i++) {
            if(i==31) { fast.setTarget(.2f); slow.setTarget(.2f); }
            fast.update(new FrameTime(i,i*8_333_333L,1.0/120),true);
            if(i%2==0) slow.update(new FrameTime(i/2,i*8_333_333L,1.0/60),true);
        }
        assertEquals(.2f,fast.value(),.002);
        assertEquals(.2f,slow.value(),.002);
        assertEquals(fast.value(),slow.value(),.002);
    }
}
