package com.atom.example.a;

import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.layout.LayoutSnapshot;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import org.lwjgl.util.yoga.Yoga;

/** The host-supplied font draws the text; the design package owns the surfaces. */
public final class ExamplePainter {
    private ExamplePainter() {
    }

    public static void paint(Canvas canvas, Font hostFont) {
        ExamplePages.Page page = ExamplePages.shell();
        LayoutSnapshot layout = ExamplePages.layout(page,
                ExamplePages.DEFAULT_WIDTH, ExamplePages.DEFAULT_HEIGHT);
        int save = canvas.save();
        try {
            ExamplePages.paintSurface(canvas, layout);
            try (Paint paint = new Paint().setAntiAlias(true)) {
                var title = layout.box(ExamplePages.TITLE).border();
                paint.setColor(UiTokens.onFloatSurface(UiTokens.cardCutout()));
                canvas.drawString("AtomChat design package", title.x(),
                        baseline(title.y(), title.height(), hostFont), hostFont, paint);

                var button = layout.box(ExamplePages.BUTTON).border();
                paint.setColor(UiTokens.onAccent(UiTokens.accentFill()));
                canvas.drawString("Open settings", button.x() + UiTokens.s(12F),
                        baseline(button.y(), button.height(), hostFont), hostFont, paint);
            }
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
