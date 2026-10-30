// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.shell;
import com.atom.lib.motion.FrameTime;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class NavigationTest {
    @Test void rootCannotPopAndSnapshotCannotMutateStack(){
        var nav=new NavigationStack<>("root");assertFalse(nav.pop());nav.push("detail");
        assertThrows(UnsupportedOperationException.class,()->nav.snapshot().clear());
        assertTrue(nav.pop());assertEquals("root",nav.peek());
    }
    @Test void interruptKeepsPositionVelocityAndDuplicateFrameCannotAdvanceOrCompleteTwice(){
        var motion=new PageTransition(0);var old=new AtomicInteger();var latest=new AtomicInteger();
        motion.retarget(1,old::incrementAndGet);motion.advance(new FrameTime(1,0,.016),true);
        float value=motion.value(),velocity=motion.velocity();
        motion.advance(new FrameTime(1,0,.016),true);assertEquals(value,motion.value());
        motion.retarget(0,latest::incrementAndGet);assertEquals(value,motion.value());assertEquals(velocity,motion.velocity());
        for(int i=2;i<500;i++)motion.advance(new FrameTime(i,0,.016),true);
        assertEquals(0,motion.value());assertEquals(0,old.get());assertEquals(1,latest.get());
    }
    @Test void duplicateCompletedFrameCannotFireNewCompletion(){
        var motion=new PageTransition(0);var count=new AtomicInteger();
        motion.retarget(0,()->{count.incrementAndGet();motion.retarget(0,count::incrementAndGet);});
        motion.advance(new FrameTime(1,0,.016),true);assertEquals(1,count.get());
        motion.advance(new FrameTime(1,0,.016),true);assertEquals(1,count.get());
        motion.advance(new FrameTime(2,0,.016),true);assertEquals(2,count.get());
    }
    @Test void closeFromWrongThreadCannotPoisonOwnedShellLifecycle() throws Exception {
        var shell=new AppShell("shell",new com.atom.lib.component.Section("root",com.atom.lib.component.Section.Kind.GROUP,java.util.List.of()),null,null);
        try(shell){
            var result=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread worker=new Thread(()->{try{shell.close();}catch(Throwable e){result.set(e);}});
            worker.start();worker.join();assertInstanceOf(IllegalStateException.class,result.get());
            assertDoesNotThrow(()->shell.advance(new FrameTime(1,0,.016),true));
        }
    }
}
