// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.image;

import io.github.humbleui.skija.*;
import java.util.Objects;

/**
 * Draws an already captured host frame as a clipped, blurred backdrop.
 * Capture stays a host/bridge responsibility; this class never reads the
 * framebuffer or owns a DirectContext.
 */
public final class BackdropBlur {
    private BackdropBlur(){}

    public static void draw(Canvas canvas, Image captured, io.github.humbleui.types.Rect destination,
                            float sigma, int fallbackColor) {
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(destination, "destination");
        if (destination.isEmpty()) return;
        if (!Float.isFinite(sigma) || sigma < 0) throw new IllegalArgumentException("Invalid blur sigma");
        int saved = canvas.getSaveCount();
        canvas.save();
        try {
            canvas.clipRect(destination);
            if (captured == null || captured.isClosed() || sigma == 0) {
                try (Paint paint = new Paint().setColor(fallbackColor).setAntiAlias(true)) {
                    canvas.drawRect(destination, paint);
                }
                return;
            }
            var source = io.github.humbleui.types.Rect.makeWH(captured.getWidth(), captured.getHeight());
            try (ImageFilter blur = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP);
                 Paint paint = new Paint().setImageFilter(blur).setAntiAlias(true)) {
                canvas.drawImageRect(captured, source, destination, SamplingMode.LINEAR, paint, false);
            }
        } finally {
            canvas.restoreToCount(saved);
        }
    }
}
