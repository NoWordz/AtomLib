// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.layout;

import com.atom.lib.motion.FrameTime;
import java.util.*;

/** Explicit-clock entrance for one page or its initially visible item roots. */
public final class PageEntrance {
    private final double duration,stagger;
    private final float distance;
    private List<NodeId> roots=List.of();
    private long started,lastFrame=Long.MIN_VALUE,lastNow=Long.MIN_VALUE;
    private boolean active;
    private boolean sampled;
    private long sampledFrame;
    private Map<NodeId,EntrancePose> lastSample=Map.of();

    public PageEntrance(double durationSeconds,double staggerSeconds,float distance) {
        if(!Double.isFinite(durationSeconds)||durationSeconds<=0||!Double.isFinite(staggerSeconds)||staggerSeconds<0||!Float.isFinite(distance))
            throw new IllegalArgumentException("Invalid entrance parameters");
        duration=durationSeconds;stagger=staggerSeconds;this.distance=distance;
    }
    /** The caller changes business pages immediately and passes only roots visible at that moment. */
    public void start(FrameTime frame,List<NodeId> visibleRoots) {
        Objects.requireNonNull(frame);List<NodeId> copy=List.copyOf(visibleRoots);
        if(new HashSet<>(copy).size()!=copy.size())throw new IllegalArgumentException("Duplicate entrance root");
        check(frame);roots=copy;started=frame.nowNanos();active=!roots.isEmpty();sampled=false;lastSample=Map.of();
    }
    public Map<NodeId,EntrancePose> sample(FrameTime frame,boolean motionEnabled) {
        Objects.requireNonNull(frame);check(frame);
        if(!motionEnabled){active=false;return Map.of();}
        if(!active)return Map.of();
        if(sampled&&sampledFrame==frame.frameId())return lastSample;
        double elapsed=((double)frame.nowNanos()-started)/1_000_000_000d;
        var result=new LinkedHashMap<NodeId,EntrancePose>();boolean complete=true;
        for(int i=0;i<roots.size();i++){
            double t=Math.max(0,Math.min(1,(elapsed-i*stagger)/duration));
            float progress=(float)easeOut(t);
            result.put(roots.get(i),new EntrancePose(0,distance*(1-progress),progress));
            if(t<1)complete=false;
        }
        if(complete){active=false;return Map.of();}
        sampled=true;sampledFrame=frame.frameId();lastSample=Collections.unmodifiableMap(result);return lastSample;
    }
    private void check(FrameTime frame){
        if(frame.frameId()<lastFrame||frame.nowNanos()<lastNow)throw new IllegalArgumentException("Nonmonotonic entrance frame");
        lastFrame=frame.frameId();lastNow=frame.nowNanos();
    }
    // Cubic-bezier (0.23, 1, 0.32, 1): solve x before evaluating y.
    private static double easeOut(double x){
        double lo=0,hi=1;
        for(int i=0;i<24;i++){
            double t=(lo+hi)*.5,one=1-t;
            double px=3*one*one*t*.23+3*one*t*t*.32+t*t*t;
            if(px<x)lo=t;else hi=t;
        }
        double t=(lo+hi)*.5,one=1-t;
        return 3*one*one*t+3*one*t*t+t*t*t;
    }
}
