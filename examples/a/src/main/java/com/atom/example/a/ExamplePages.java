package com.atom.example.a;

import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiCards;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.layout.LayoutNode;
import com.atom.lib.layout.LayoutSnapshot;
import com.atom.lib.layout.LayoutStyle;
import com.atom.lib.layout.LayoutTree;
import com.atom.lib.layout.Length;
import com.atom.lib.layout.NodeId;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import io.github.humbleui.skija.Canvas;
import java.util.List;

/**
 * Example A: the optional AtomChat design package.
 * Yoga computes every rectangle first; UiCards and SkiaDraw paint it.
 */
public final class ExamplePages {
    public static final float DEFAULT_WIDTH = 360F;
    public static final float DEFAULT_HEIGHT = 200F;

    public static final NodeId ROOT = new NodeId("example.a.root");
    public static final NodeId CARD = new NodeId("example.a.card");
    public static final NodeId TITLE = new NodeId("example.a.title");
    public static final NodeId DIVIDER = new NodeId("example.a.divider");
    public static final NodeId BODY = new NodeId("example.a.body");
    public static final NodeId BUTTON = new NodeId("example.a.button");

    private ExamplePages() {
    }

    /** The page model: one layout tree, painted by {@link ExamplePainter}. */
    public record Page(LayoutTree tree) {
    }

    public static Page settings(float[] width) {
        float pageWidth = width.length == 0 ? DEFAULT_WIDTH : Math.max(240F, width[0]);
        float pagePadding = UiTokens.s(12F);
        float cardPadding = UiTokens.s(16F);
        float cardGap = UiTokens.s(10F);

        LayoutStyle pageStyle = LayoutStyle.column()
                .size(Length.points(pageWidth), Length.points(DEFAULT_HEIGHT))
                .spacing(pagePadding, pagePadding);
        LayoutStyle cardStyle = LayoutStyle.column()
                .size(Length.percent(100F), Length.auto())
                .flex(1F, 1F, Length.auto())
                .spacing(cardPadding, cardGap);
        LayoutStyle titleStyle = LayoutStyle.row()
                .size(Length.percent(100F), Length.points(UiTokens.FONT_TITLE + UiTokens.s(6F)));
        LayoutStyle dividerStyle = LayoutStyle.row()
                .size(Length.percent(100F), Length.points(Math.max(1F, UiTokens.s(1F))));
        LayoutStyle bodyStyle = LayoutStyle.column()
                .size(Length.percent(100F), Length.auto())
                .flex(1F, 1F, Length.auto());
        LayoutStyle buttonStyle = LayoutStyle.row()
                .size(Length.percent(100F), Length.points(UiTokens.BUTTON_H))
                .flex(0F, 0F, Length.points(UiTokens.BUTTON_H));

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

    /** Paints the card, divider and accent plate with the design package. */
    public static void paintSurface(Canvas canvas, LayoutSnapshot layout) {
        var card = layout.box(CARD).border();
        var divider = layout.box(DIVIDER).border();
        var button = layout.box(BUTTON).border();

        UiCards.drawCard(canvas, card.x(), card.y(), card.width(), card.height(),
                UiTokens.cardRadius(), 0F);
        float line = Math.max(1F, UiTokens.s(1F));
        SkiaDraw.drawRoundedRect(canvas, divider.x(), divider.y() + (divider.height() - line) / 2F,
                divider.width(), line, line / 2F, UiTokens.hairline());
        SkiaDraw.drawRoundedRect(canvas, button.x(), button.y(), button.width(), button.height(),
                UiTokens.radius(10F), UiTokens.accentFill());
    }
}
