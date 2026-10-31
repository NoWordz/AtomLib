package com.atom.example.b;

import com.atom.lib.layout.LayoutSnapshot;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import org.lwjgl.util.yoga.Yoga;

/** The host supplies the font and draws the text with plain Skija calls. */
public final class ExamplePainter {
    private ExamplePainter() {
    }

    public static void paint(Canvas canvas, Font hostFont) {
        ExamplePages.Page page = ExamplePages.shell();
        LayoutSnapshot layout = ExamplePages.layout(page,
                ExamplePages.DEFAULT_WIDTH, ExamplePages.DEFAULT_HEIGHT);
        int save = canvas.save();
        try (Paint paint = new Paint().setAntiAlias(true)) {
            ExamplePages.paintSurface(canvas, paint, layout);

            var title = layout.box(ExamplePages.TITLE).border();
            paint.setColor(ExamplePages.TITLE_INK);
            canvas.drawString("Draw it yourself", title.x(),
                    baseline(title.y(), title.height(), hostFont), hostFont, paint);

            var body = layout.box(ExamplePages.BODY).border();
            paint.setColor(ExamplePages.MUTED_INK);
            canvas.drawString("Yoga rectangles, plain Skija paint", body.x(),
                    baseline(body.y(), body.height(), hostFont), hostFont, paint);

            var button = layout.box(ExamplePages.BUTTON).border();
            paint.setColor(ExamplePages.ON_ACCENT);
            canvas.drawString("Native Skija", button.x() + 12F,
                    baseline(button.y(), button.height(), hostFont), hostFont, paint);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private static float baseline(float top, float height, Font font) {
        var metrics = font.getMetrics();
        float textHeight = -metrics.getAscent() + metrics.getDescent();
        return top + (height - textHeight) / 2F - metrics.getAscent();
    }

    public static int yogaDirection() {
        return Yoga.YGDirectionLTR;
    }
}
