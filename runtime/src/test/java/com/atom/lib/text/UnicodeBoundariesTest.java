// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.text;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnicodeBoundariesTest {
    @Test void utf16BoundariesCoverEmptyCombiningAndEmoji() {
        assertEquals(List.of(0), UnicodeBoundaries.graphemes("", Locale.ROOT));
        assertEquals(List.of(0, 2, 4, 9), UnicodeBoundaries.graphemes("e\u0301😀👩‍💻", Locale.ROOT));
        for (String value : List.of("中文，标点。", "hello world", "ภาษาไทยไม่มีช่องว่าง", "e\u0301😀👩‍💻", "")) {
            for (List<Integer> positions : List.of(UnicodeBoundaries.lines(value, Locale.ROOT),
                    UnicodeBoundaries.words(value, Locale.ROOT), UnicodeBoundaries.graphemes(value, Locale.ROOT))) {
                assertEquals(0, positions.get(0));
                assertEquals(value.length(), positions.get(positions.size() - 1));
                for (int i = 1; i < positions.size(); i++) {
                    int p = positions.get(i);
                    assertTrue(p > positions.get(i - 1));
                    assertFalse(p < value.length() && Character.isHighSurrogate(value.charAt(p - 1)) && Character.isLowSurrogate(value.charAt(p)));
                }
                assertThrows(UnsupportedOperationException.class, () -> positions.add(123));
            }
        }
    }

    @Test void wordAndLineBoundariesUseLocaleAndIndependentIterators() throws Exception {
        assertEquals(List.of(0, 5, 6, 11), UnicodeBoundaries.words("hello world", Locale.ROOT));
        assertEquals(List.of(0, 6, 11), UnicodeBoundaries.lines("hello world", Locale.ROOT));
        assertEquals(List.of(0, 4, 7, 12, 16, 20), UnicodeBoundaries.words("ภาษาไทยไม่มีช่องว่าง", Locale.forLanguageTag("th")));
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<List<Integer>>>();
            for (int i = 0; i < 200; i++) tasks.add(() -> UnicodeBoundaries.graphemes("e\u0301😀👩‍💻", Locale.ROOT));
            for (var result : executor.invokeAll(tasks)) assertEquals(List.of(0, 2, 4, 9), result.get());
        } finally { executor.shutdownNow(); }
    }
}
