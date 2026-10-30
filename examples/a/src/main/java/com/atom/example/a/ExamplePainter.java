package com.atom.example.a;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import org.lwjgl.util.yoga.Yoga;

/** Compiles using only the platform Maven coordinate and its public API. */
public final class ExamplePainter {
    private ExamplePainter() { }
    public static void paint(Canvas canvas, Font hostFont) {
        int save = canvas.save();
        try (Paint paint = new Paint()) {
            canvas.clipRect(Rect.makeXYWH(0, 0, 160, 48));
            canvas.drawString("AtomLib a", 8, 24, hostFont, paint);
        } finally {
            canvas.restoreToCount(save);
        }
    }
    public static int yogaDirection() { return Yoga.YGDirectionLTR; }
}
