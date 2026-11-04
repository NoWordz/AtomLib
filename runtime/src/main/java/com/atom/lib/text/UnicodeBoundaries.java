// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.text;

import com.ibm.icu.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** ICU boundaries are UTF-16 offsets; they do not control Skia Paragraph line breaking. */
final class UnicodeBoundaries {
    private UnicodeBoundaries() {}

    static List<Integer> lines(String text, Locale locale) {
        return collect(BreakIterator.getLineInstance(Objects.requireNonNull(locale)), text);
    }

    static List<Integer> words(String text, Locale locale) {
        return collect(BreakIterator.getWordInstance(Objects.requireNonNull(locale)), text);
    }

    static List<Integer> graphemes(String text, Locale locale) {
        return collect(BreakIterator.getCharacterInstance(Objects.requireNonNull(locale)), text);
    }

    private static List<Integer> collect(BreakIterator iterator, String text) {
        iterator.setText(Objects.requireNonNull(text));
        var result = new ArrayList<Integer>();
        for (int index = iterator.first(); index != BreakIterator.DONE; index = iterator.next()) result.add(index);
        return List.copyOf(result);
    }
}
