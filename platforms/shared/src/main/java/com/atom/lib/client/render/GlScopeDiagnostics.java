// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

/** Opt-in capture events; the host's frame interval determines per-frame counts. */
@Name("com.atomlib.GlStateCapture")
@Label("AtomLib GL state capture")
@Category("AtomLib")
@StackTrace(false)
@Enabled(false)
final class GlScopeDiagnostics extends Event {
    private static final EventType TYPE=EventType.getEventType(GlScopeDiagnostics.class);
    long startedAtNanos;
    @Timespan(Timespan.NANOSECONDS) long captureNanos;
    int depth;
    int textureUnits;
    int textureTargets;
    boolean success;

    static boolean enabled() { return TYPE.isEnabled(); }
}
