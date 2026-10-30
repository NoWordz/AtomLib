package com.atom.lib.motion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MotionClockTest {
    @Test void aFrameAdvancesEachInstanceOnlyOnceAndReversalKeepsVelocity() {
        var a=new SpringAnim(220,.8f,.001f,.001f);var b=new SpringAnim(220,.8f,.001f,.001f);
        a.setTarget(1);b.setTarget(1);var time=new FrameTime(1,16_000_000,.016);
        a.update(time,true);float value=a.value(),velocity=a.velocity();a.update(time,true);
        assertEquals(value,a.value());assertEquals(velocity,a.velocity());
        b.update(time,true);assertEquals(value,b.value());
        a.setTarget(0);assertEquals(value,a.value());assertEquals(velocity,a.velocity());
        a.update(new FrameTime(2,32_000_000,.016),true);
        assertEquals(value,b.value());assertNotEquals(value,a.value());
    }
    @Test void reducedMotionSnapsAndHitchesRemainFinite() {
        var spring=new SpringAnim(220,1,.001f,.001f);spring.setTarget(1);
        spring.update(new FrameTime(1,0,100),true);assertTrue(Float.isFinite(spring.value()));
        spring.update(new FrameTime(2,1,0),false);assertEquals(1,spring.value());assertEquals(0,spring.velocity());
        assertThrows(IllegalArgumentException.class,()->spring.setTarget(Float.NaN));
        assertThrows(IllegalArgumentException.class,()->new SpringAnim(-1,1));
    }
}
