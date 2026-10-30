// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.text;

import com.atom.lib.font.FontId;
import java.util.Objects;

/** Plain LTR text. Font variants are explicit FontIds, color is ARGB. */
public record TextRequest(String text, FontId font, float size, int color, int maxLines, boolean ellipsis) {
    public TextRequest {
        Objects.requireNonNull(text); Objects.requireNonNull(font);
        if (!Float.isFinite(size) || size <= 0 || maxLines < 1) throw new IllegalArgumentException("Invalid text style");
        if (text.length() > 32768) throw new IllegalArgumentException("Text exceeds 32768 UTF-16 units");
        for (int cp : text.codePoints().toArray()) {
            if (cp >= 0xd800 && cp <= 0xdfff) throw new IllegalArgumentException("Unpaired UTF-16 surrogate");
            byte direction = Character.getDirectionality(cp);
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                    || (cp >= 0x202a && cp <= 0x202e) || (cp >= 0x2066 && cp <= 0x2069))
                throw new IllegalArgumentException("Bidi text requires a paragraph engine");
            if (Character.isISOControl(cp) && cp != '\n' && cp != '\r')
                throw new IllegalArgumentException("Unsupported control character");
        }
    }
    public TextRequest(String text, FontId font, float size, int color) {
        this(text, font, size, color, Integer.MAX_VALUE, false);
    }
}
