// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.shell;

import com.atom.lib.motion.*;
import java.util.Objects;

/** Independently composed spring transition. Retargeting preserves both position and velocity. */
public final class PageTransition {
    private final SpringAnim spring=new SpringAnim(220,1,.0001f,.001f);
    private Runnable completion;
    private long lastFrame=Long.MIN_VALUE;
    public PageTransition(float initial){spring.snapTo(initial);}
    public float value(){return spring.value();}
    public float velocity(){return spring.velocity();}
    public boolean active(){return completion!=null;}
    public void retarget(float target,Runnable completion){Objects.requireNonNull(completion);spring.setTarget(target);this.completion=completion;}
    public void advance(FrameTime time,boolean motionEnabled){
        if(time.frameId()<lastFrame)throw new IllegalArgumentException("Frame IDs must be monotonic");
        if(time.frameId()==lastFrame)return;lastFrame=time.frameId();
        spring.update(time,motionEnabled);
        if(spring.isSettled()&&completion!=null){var done=completion;completion=null;done.run();}
    }
}
