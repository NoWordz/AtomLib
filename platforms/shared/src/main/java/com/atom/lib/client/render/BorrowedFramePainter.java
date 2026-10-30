// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import io.github.humbleui.skija.Canvas;

@FunctionalInterface
public interface BorrowedFramePainter {
    /** Canvas and frame handles are borrowed. Only Skija drawing belongs inside this callback. */
    void paint(Canvas canvas,BorrowedFrame frame);
}
