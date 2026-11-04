import com.ibm.icu.text.BreakIterator;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

// Experimental only: not part of AtomLib's runtime or its published API.
final class BoundaryProbe {
    enum Kind { LINE, WORD, GRAPHEME }

    static int[] boundaries(String text, Kind kind, Locale locale) {
        Objects.requireNonNull(text);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(locale);
        BreakIterator iterator = switch (kind) {
            case LINE -> BreakIterator.getLineInstance(locale);
            case WORD -> BreakIterator.getWordInstance(locale);
            case GRAPHEME -> BreakIterator.getCharacterInstance(locale);
        };
        iterator.setText(text);
        var positions = new ArrayList<Integer>();
        for (int p = iterator.first(); p != BreakIterator.DONE; p = iterator.next()) positions.add(p);
        return positions.stream().mapToInt(Integer::intValue).toArray();
    }
}
