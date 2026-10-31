package com.atom.example.b;

import com.atom.lib.layout.LayoutNode;
import com.atom.lib.layout.LayoutSnapshot;
import com.atom.lib.layout.LayoutStyle;
import com.atom.lib.layout.LayoutTree;
import com.atom.lib.layout.Length;
import com.atom.lib.layout.NodeId;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import java.util.List;

/**
 * Example B: the host draws everything itself.
 * Yoga only supplies rectangles; the palette, spacing and paint are local.
 */
public final class ExamplePages {
    public static final float DEFAULT_WIDTH = 360F;
    public static final float DEFAULT_HEIGHT = 200F;

    // Palette and spacing owned by this host; no shared theme tokens.
    public static final int BACKDROP = 0xFF16181D;
    public static final int SURFACE = 0xFF242831;
    public static final int SURFACE_EDGE = 0xFF3B414D;
    public static final int TITLE_INK = 0xFFF2F4F8;
    public static final int MUTED_INK = 0xFFAAB2C0;
    public static final int DIVIDER_INK = 0xFF3B414D;
    public static final int ACCENT = 0xFFE0A94A;
    public static final int ON_ACCENT = 0xFF231A08;

    public static final float PAGE_PADDING = 15F;
    public static final float CARD_PADDING = 20F;
    public static final float ROW_GAP = 12.5F;
    public static final float TITLE_HEIGHT = 30F;
    public static final float DIVIDER_HEIGHT = 1F;
    public static final float BUTTON_HEIGHT = 37.5F;

    public static final NodeId ROOT = new NodeId("example.b.root");
    public static final NodeId CARD = new NodeId("example.b.card");
    public static final NodeId TITLE = new NodeId("example.b.title");
    public static final NodeId DIVIDER = new NodeId("example.b.divider");
    public static final NodeId BODY = new NodeId("example.b.body");
    public static final NodeId BUTTON = new NodeId("example.b.button");

    private ExamplePages() {
    }

    /** The page model: one layout tree, painted by {@link ExamplePainter}. */
    public record Page(LayoutTree tree) {
    }

    public static Page settings(float[] width) {
        float pageWidth = width.length == 0 ? DEFAULT_WIDTH : Math.max(240F, width[0]);

        LayoutStyle pageStyle = LayoutStyle.column()
                .size(Length.points(pageWidth), Length.points(DEFAULT_HEIGHT))
                .spacing(PAGE_PADDING, PAGE_PADDING);
        LayoutStyle cardStyle = LayoutStyle.column()
                .size(Length.percent(100F), Length.auto())
                .flex(1F, 1F, Length.auto())
                .spacing(CARD_PADDING, ROW_GAP);
        LayoutStyle titleStyle = LayoutStyle.row()
                .size(Length.percent(100F), Length.points(TITLE_HEIGHT));
        LayoutStyle dividerStyle = LayoutStyle.row()
                .size(Length.percent(100F), Length.points(DIVIDER_HEIGHT));
        LayoutStyle bodyStyle = LayoutStyle.column()
                .size(Length.percent(100F), Length.auto())
                .flex(1F, 1F, Length.auto());
        LayoutStyle buttonStyle = LayoutStyle.row()
                .size(Length.percent(100F), Length.points(BUTTON_HEIGHT))
                .flex(0F, 0F, Length.points(BUTTON_HEIGHT));

        LayoutNode card = new LayoutNode(CARD, cardStyle, List.of(
                new LayoutNode(TITLE, titleStyle, List.of(), null, 0L),
                new LayoutNode(DIVIDER, dividerStyle, List.of(), null, 0L),
                new LayoutNode(BODY, bodyStyle, List.of(), null, 0L),
                new LayoutNode(BUTTON, buttonStyle, List.of(), null, 0L)), null, 0L);
        LayoutNode root = new LayoutNode(ROOT, pageStyle, List.of(card), null, 0L);
        return new Page(new LayoutTree(root));
    }

    public static Page shell() {
        return settings(new float[]{DEFAULT_WIDTH});
    }

    public static LayoutSnapshot layout(Page page, float width, float height) {
        try (YogaLayoutEngine engine = new YogaLayoutEngine()) {
            return engine.layout(page.tree(), new com.atom.lib.geometry.Viewport(width, height, 1F));
        }
    }

    /** Paints the same structure with native canvas.drawRect calls. */
    public static void paintSurface(Canvas canvas, Paint paint, LayoutSnapshot layout) {
        var root = layout.box(ROOT).border();
        var card = layout.box(CARD).border();
        var divider = layout.box(DIVIDER).border();
        var button = layout.box(BUTTON).border();

        fill(canvas, paint, root.x(), root.y(), root.width(), root.height(), BACKDROP);
        fill(canvas, paint, card.x(), card.y(), card.width(), card.height(), SURFACE);
        canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(
                        card.x() + 0.5F, card.y() + 0.5F,
                        Math.max(0F, card.width() - 1F), Math.max(0F, card.height() - 1F)),
                paint.setColor(SURFACE_EDGE).setMode(PaintMode.STROKE).setStrokeWidth(1F));
        fill(canvas, paint, divider.x(), divider.y() + (divider.height() - DIVIDER_HEIGHT) / 2F,
                divider.width(), DIVIDER_HEIGHT, DIVIDER_INK);
        fill(canvas, paint, button.x(), button.y(), button.width(), button.height(), ACCENT);
    }

    private static void fill(Canvas canvas, Paint paint,
                             float x, float y, float width, float height, int color) {
        canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(x, y, width, height),
                paint.setColor(color).setMode(PaintMode.FILL));
    }
}
