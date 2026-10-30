// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.interaction;

import com.atom.lib.geometry.Rect;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;

@FunctionalInterface
public interface HudPainter {
    /** Borrowed Canvas; draw the host's own preview inside bounds. */
    void paint(Canvas canvas,Rect bounds,HostContext host,FrameTime time);
}
