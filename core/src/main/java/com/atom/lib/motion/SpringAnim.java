// SPDX-License-Identifier: MIT
// Copyright (c) 2026 E33EPUS
// Adapted from AtomChat main@28fd6de0, shared/src/main/java/com/atom/chat/ui/SpringAnim.java.
// Changes: injected FrameTime, per-instance frame guard, finite parameter validation.
// Full license: licenses/AtomChat-MIT.txt.
package com.atom.lib.motion;

/** Semi-implicit, substepped mass-spring-damper. Thresholds use the caller's value domain. */
public final class SpringAnim {
    private final float stiffness,damping,settlePosition,settleVelocity;
    private float value,velocity,target;
    private boolean settled=true;
    private long lastFrame=Long.MIN_VALUE;
    public SpringAnim(float stiffness,float dampingRatio){this(stiffness,dampingRatio,.09f,2f);}
    public SpringAnim(float stiffness,float dampingRatio,float settlePosition,float settleVelocity){
        if(!Float.isFinite(stiffness)||stiffness<=0||!Float.isFinite(dampingRatio)||dampingRatio<0||!Float.isFinite(settlePosition)||settlePosition<=0||!Float.isFinite(settleVelocity)||settleVelocity<=0)throw new IllegalArgumentException("Invalid spring parameters");
        this.stiffness=stiffness;this.damping=2*dampingRatio*(float)Math.sqrt(stiffness);this.settlePosition=settlePosition;this.settleVelocity=settleVelocity;
    }
    public float value(){return value;}
    public float velocity(){return velocity;}
    public float target(){return target;}
    public boolean isSettled(){return settled;}
    public void setTarget(float target){requireFinite(target);if(this.target!=target){this.target=target;settled=false;}}
    public void snapTo(float value){requireFinite(value);this.value=value;target=value;velocity=0;settled=true;}
    public void update(FrameTime frame,boolean motionEnabled){
        if(frame.frameId()<lastFrame)throw new IllegalArgumentException("Frame IDs must be monotonic");
        if(!motionEnabled){lastFrame=frame.frameId();snapTo(target);return;}
        if(frame.frameId()==lastFrame)return;lastFrame=frame.frameId();
        if(settled)return;
        float delta=(float)Math.min(frame.deltaSeconds(),.05);
        int steps=Math.max(1,(int)Math.ceil(delta/.004));float step=delta/steps;
        for(int i=0;i<steps;i++){velocity+=(-stiffness*(value-target)-damping*velocity)*step;value+=velocity*step;}
        if(Math.abs(value-target)<=settlePosition&&Math.abs(velocity)<=settleVelocity){value=target;velocity=0;settled=true;}
    }
    private static void requireFinite(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("Invalid spring value");}
}
