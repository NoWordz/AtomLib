// SPDX-License-Identifier: Apache-2.0 AND MIT AND ISC
package com.atom.lib.component;

import io.github.humbleui.skija.*;

/**
 * Four source-reviewed line icons from AtomChat's allowlisted Lucide/Feather
 * family. AtomChat adaptation copyright (c) 2026 E33EPUS (MIT); Lucide is ISC
 * and Feather is MIT. Full notices ship in licenses/.
 */
public final class IconPaths {
    public static final Path SETTINGS = Path.makeFromSVGString(
            "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6"
            + " M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0"
            + "l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2"
            + " 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06"
            + "a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82"
            + " 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0"
            + " 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0"
            + "l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2"
            + " 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06"
            + "a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9"
            + "a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z");
    public static final Path GLOBE = Path.makeFromSVGString(
            "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20"
            + " M2 12h20"
            + " M12 2a15.3 15.3 0 0 1 4 10a15.3 15.3 0 0 1-4 10a15.3 15.3 0 0 1-4-10a15.3 15.3 0 0 1 4-10z");
    public static final Path MENTION = Path.makeFromSVGString(
            "M12 8 a4 4 0 1 0 0 8 a4 4 0 1 0 0 -8"
            + " M16 8 v5 a3 3 0 0 0 6 0 v-1 a10 10 0 1 0 -4 8");
    public static final Path PHRASE = Path.makeFromSVGString("M13 2 3 14h9l-1 8 10-12h-9l1-8z");

    private IconPaths(){}

    public static void draw(Canvas canvas, Path icon, float centerX, float centerY, float size, float stroke, int color) {
        java.util.Objects.requireNonNull(icon, "icon");
        if (!Float.isFinite(size) || size <= 0) throw new IllegalArgumentException("Invalid icon size");
        var bounds = icon.getBounds();
        float scale = size / Math.max(bounds.getWidth(), bounds.getHeight());
        int saved = canvas.getSaveCount();
        canvas.save();
        try {
            canvas.translate(centerX - (bounds.getLeft() + bounds.getRight()) * scale / 2f,
                    centerY - (bounds.getTop() + bounds.getBottom()) * scale / 2f);
            canvas.scale(scale, scale);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(PaintMode.STROKE).setStrokeWidth(Math.max(1, stroke) / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND).setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally { canvas.restoreToCount(saved); }
    }
}
