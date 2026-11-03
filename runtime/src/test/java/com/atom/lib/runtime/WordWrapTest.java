package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.*;
import com.atom.lib.layout.Constraints;
import com.atom.lib.text.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WordWrapTest {
    @Test void wordBoundaryPreservesAllCharactersAndFallsBackForLongWords() {
        var policy = TextBreakPolicy.WORD;
        try (var runtime = AtomLibRuntime.open("word-wrap");
             var fonts = new FontRegistry(runtime);
             var text = new TextService(fonts, policy)) {
            var id = new FontId("body");
            fonts.register(id, FontSource.file(FontIsolationTest.fontPath()));
            var unlimited = new Constraints(Constraints.Axis.undefined(), Constraints.Axis.undefined());
            float width = text.layout(new TextRequest("hello wo", id, 20, 0), unlimited).size().width();
            String input = "hello world";
            var result = text.layout(new TextRequest(input, id, 20, 0),
                    new Constraints(Constraints.Axis.atMost(width), Constraints.Axis.undefined()));
            assertEquals(2, result.lines().size());
            assertEquals("hello ", input.substring(result.lines().get(0).start(), result.lines().get(0).end()));
            assertEquals("world", input.substring(result.lines().get(1).start(), result.lines().get(1).end()));
            String longWord = "abcdefghijk";
            var split = text.layout(new TextRequest(longWord, id, 20, 0),
                    new Constraints(Constraints.Axis.atMost(1), Constraints.Axis.undefined()));
            assertEquals(longWord.length(), split.lines().size());
            assertEquals(longWord.length(), split.lines().get(split.lines().size()-1).end());
            String clusters = "e\u0301e\u0301";
            var graphemes = text.layout(new TextRequest(clusters, id, 20, 0),
                    new Constraints(Constraints.Axis.atMost(1), Constraints.Axis.undefined()));
            assertEquals(2, graphemes.lines().size());
            assertEquals(2, graphemes.lines().get(0).end());

            String spaced = "hello  world\nnext";
            var multiple = text.layout(new TextRequest(spaced, id, 20, 0),
                    new Constraints(Constraints.Axis.atMost(width), Constraints.Axis.undefined()));
            assertEquals("hello  ", spaced.substring(multiple.lines().get(0).start(), multiple.lines().get(0).end()));
            assertEquals("world", spaced.substring(multiple.lines().get(1).start(), multiple.lines().get(1).end()));
            assertEquals("next", spaced.substring(multiple.lines().get(2).start(), multiple.lines().get(2).end()));
            try (var defaultText = new TextService(fonts)) {
                var original = defaultText.layout(new TextRequest(input, id, 20, 0),
                        new Constraints(Constraints.Axis.atMost(width), Constraints.Axis.undefined()));
                assertTrue(original.lines().get(0).end() > 6, "default grapheme policy still uses available width");
            }
        }
    }
}
