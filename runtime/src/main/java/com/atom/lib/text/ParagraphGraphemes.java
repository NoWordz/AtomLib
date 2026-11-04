// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.text;

import java.util.List;
import java.util.Locale;

/** ICU is used for grapheme alignment, never for Paragraph line breaking. */
final class ParagraphGraphemes {
    private ParagraphGraphemes() {}
    static List<Integer> of(String text) {
        try {
            return UnicodeBoundaries.graphemes(text, Locale.ROOT);
        } catch (LinkageError failure) {
            throw new IllegalStateException("Paragraph text requires a compatible ICU4J runtime", failure);
        }
    }
}
