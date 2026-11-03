// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.design.atomdefault;

import com.atom.lib.design.atomchat.AtomChatControlStyle;
import java.util.Objects;

/** Immutable host-independent colours and optional panel material. */
public record AtomDefaultPalette(String id, int accentColor, int panelBackgroundColor,
        int textColor, int textSecondaryColor, int cardColor, int outlineColor,
        float panelOpacity, boolean blurEnabled) {
    public AtomDefaultPalette {
        Objects.requireNonNull(id, "id");
        if (id.isBlank() || !Float.isFinite(panelOpacity) || panelOpacity < 0 || panelOpacity > 1)
            throw new IllegalArgumentException("Invalid palette");
    }

    /** Card tint and geometry are host choices, independent of the palette. */
    public AtomChatControlStyle controlStyle(float cornerRadius, boolean halfScreenMode, float cardTint) {
        return new AtomChatControlStyle(cornerRadius, halfScreenMode, accentColor,
                textSecondaryColor, cardTint, cardColor, panelBackgroundColor, textColor);
    }
}
