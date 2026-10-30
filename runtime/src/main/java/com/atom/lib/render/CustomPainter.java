// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.render;

import io.github.humbleui.skija.Canvas;

@FunctionalInterface
public interface CustomPainter {
    /** Canvas is borrowed. Do not close it or restore below the callback's entry save count. */
    void paint(Canvas canvas);
}
