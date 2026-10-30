package com.atom.lib.probe;

import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LayoutContractTest {
    private ProbeBackend backend() { return ProbeBackend.open(); }

    @Test void paddingGrowShrinkAndPercentHaveFiniteStableGeometry() {
        try (var b = backend()) {
            var root = b.node().row().padding(10);
            var fixed = b.node().width(100).height(20);
            var fluid = b.node().grow(1).shrink(1).basis(0).minWidth(0).maxWidth(500);
            root.add(fixed).add(fluid);
            b.layout(root, 320, 100, 1);
            assertEquals(100, fixed.width(), .01);
            assertEquals(200, fluid.width(), .01);
            assertEquals(110, fluid.x(), .01);
            b.layout(root, 640, 100, 1);
            assertEquals(500, fluid.width(), .01);
            var percent = b.node().percentWidth(50).height(10);
            var parent = b.node().add(percent);
            b.layout(parent, 200, 100, 1);
            assertEquals(100, percent.width(), .01);
        }
    }

    @Test void wrapMarginsHiddenAndFractionalPixelsArePredictable() {
        try (var b = backend()) {
            var root = b.node().row().wrap();
            var a = b.node().width(60).height(20).marginRight(5);
            var c = b.node().width(60).height(20);
            var hidden = b.node().width(1000).height(1000).hidden();
            root.add(a).add(c).add(hidden);
            b.layout(root, 100, 100, 2);
            assertEquals(20, c.y(), .01);
            assertEquals(0, hidden.width(), .01);
            var fractional = b.node().row();
            var one = b.node().grow(1).basis(0);
            var two = b.node().grow(1).basis(0);
            fractional.add(one).add(two);
            b.layout(fractional, 101, 10, 2);
            assertEquals(50.5f, one.width(), .01);
            assertEquals(101, two.x() + two.width(), .01);
        }
    }

    @Test void measureModesDirtyTextAndIndependentTrees() {
        try (var b = backend()) {
            List<String> modes = new ArrayList<>();
            int[] units = {12};
            var text = b.node().measure((w, wm, h, hm) -> {
                modes.add(wm);
                float natural = units[0] * 10;
                float width = wm.equals("EXACTLY") ? w : wm.equals("AT_MOST") ? Math.min(w, natural) : natural;
                float height = width == 0 ? 20 : (float)Math.ceil(natural / width) * 20;
                return new float[]{width, hm.equals("EXACTLY") ? h : hm.equals("AT_MOST") ? Math.min(h, height) : height};
            });
            var root = b.node().add(text);
            b.layout(root, 60, Float.NaN, 1);
            assertEquals(40, text.height(), .01);
            units[0] = 18; text.dirty();
            b.layout(root, 60, Float.NaN, 1);
            assertEquals(60, text.height(), .01);
            var other = b.node().add(b.node().width(25).height(15));
            b.layout(other, 25, 15, 1);
            b.layout(root, 120, Float.NaN, 1);
            assertEquals(40, text.height(), .01);
            assertEquals(25, other.width(), .01);
            assertFalse(modes.isEmpty());
            var free = b.node().measure((w,wm,h,hm) -> { modes.add(wm); return new float[]{17,19}; });
            b.layout(free, Float.NaN, Float.NaN, 1);
            assertEquals(17, free.width(), .01);
            assertTrue(modes.contains("UNDEFINED"));
            b.layout(root, 0, 0, 1);
            assertTrue(Float.isFinite(text.width()));
        }
    }

    @Test void textSamplesUseCodePointsAndReactToFontMetrics() {
        for (String sample : List.of("中文布局无需空格也能换行", "averylongwordwithoutspaces", "A😀e\u0301")) {
            try (var b = backend()) {
                float[] advance = {10};
                var leaf = b.node().measure((w,wm,h,hm) -> {
                    float natural = sample.codePointCount(0,sample.length()) * advance[0];
                    float width = wm.equals("UNDEFINED") ? natural : wm.equals("EXACTLY") ? w : Math.min(w,natural);
                    return new float[]{width, (float)Math.ceil(natural / Math.max(1,width))*20};
                });
                var root = b.node().add(leaf);
                b.layout(root, 40, Float.NaN, 1);
                float initial = leaf.height();
                advance[0] = 20; leaf.dirty();
                b.layout(root, 40, Float.NaN, 1);
                assertTrue(leaf.height() > initial);
            }
        }
    }

    @Test void removalAndRepeatedCloseReleaseAllOwnedNodes() {
        for (int i=0; i<30; i++) {
            var b = backend();
            var child = b.node().width(10).height(10);
            var root = b.node().add(child);
            b.layout(root, 100, 100, 1);
            root.remove(child);
            b.layout(root, 100, 100, 1);
            assertEquals(2, b.ownedNodes());
            b.close(); b.close();
            assertEquals(0, b.ownedNodes());
            assertThrows(IllegalStateException.class, b::node);
        }
    }

    @Test void recordStableAndDirtyWorkloads() {
        var bean = ManagementFactory.getThreadMXBean();
        var allocation = bean instanceof com.sun.management.ThreadMXBean m ? m : null;
        for (int count : new int[]{12,100,1000}) {
            try (var b = backend()) {
                var root = b.node();
                List<ProbeBackend.Node> children = new ArrayList<>();
                for (int i=0; i<count; i++) { var n=b.node().height(10);children.add(n);root.add(n); }
                for(int i=0;i<25;i++) b.layout(root,320,Float.NaN,1);
                long tid=Thread.currentThread().getId();
                long alloc=allocation==null?0:allocation.getThreadAllocatedBytes(tid);
                long start=System.nanoTime();
                for(int i=0;i<100;i++) b.layout(root,320,Float.NaN,1);
                long stable=System.nanoTime()-start;
                start=System.nanoTime();
                for(int i=0;i<100;i++) { children.get(i%count).height(10+(i%3));b.layout(root,320,Float.NaN,1); }
                long dirty=System.nanoTime()-start;
                long bytes=allocation==null?-1:allocation.getThreadAllocatedBytes(tid)-alloc;
                System.out.printf(Locale.ROOT,"BENCH backend=%s java=%s nodes=%d stableNs/op=%d dirtyNs/op=%d allocatedBytes=%d%n",System.getProperty("probe.backend"),System.getProperty("java.version"),count,stable/100,dirty/100,bytes);
                assertTrue(Float.isFinite(root.height()));
            }
        }
    }
}
