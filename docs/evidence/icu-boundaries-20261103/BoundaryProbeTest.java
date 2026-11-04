import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;

public class BoundaryProbeTest {
    static void equal(int[] expected, int[] actual) {
        if (!Arrays.equals(expected, actual)) throw new AssertionError(Arrays.toString(actual));
    }
    static void valid(String text, int[] offsets) {
        if (offsets.length == 0 || offsets[0] != 0 || offsets[offsets.length - 1] != text.length()) throw new AssertionError();
        for (int i = 1; i < offsets.length; i++) {
            int p = offsets[i];
            if (p <= offsets[i - 1] || p > text.length()) throw new AssertionError();
            if (p < text.length() && Character.isHighSurrogate(text.charAt(p - 1)) && Character.isLowSurrogate(text.charAt(p))) throw new AssertionError();
        }
    }
    public static void main(String[] args) throws Exception {
        equal(new int[]{0}, BoundaryProbe.boundaries("", BoundaryProbe.Kind.GRAPHEME, Locale.ROOT));
        equal(new int[]{0, 2, 4, 9}, BoundaryProbe.boundaries("e\u0301😀👩‍💻", BoundaryProbe.Kind.GRAPHEME, Locale.ROOT));
        equal(new int[]{0, 5, 6, 11}, BoundaryProbe.boundaries("hello world", BoundaryProbe.Kind.WORD, Locale.ROOT));
        for (String text : new String[]{"中文，标点。", "hello world", "ภาษาไทยไม่มีช่องว่าง", "e\u0301😀👩‍💻", ""}) {
            for (BoundaryProbe.Kind kind : BoundaryProbe.Kind.values()) valid(text, BoundaryProbe.boundaries(text, kind, Locale.ROOT));
        }
        int[] thai = BoundaryProbe.boundaries("ภาษาไทยไม่มีช่องว่าง", BoundaryProbe.Kind.WORD, Locale.forLanguageTag("th"));
        valid("ภาษาไทยไม่มีช่องว่าง", thai);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
            for (int i = 0; i < 200; i++) tasks.add(() -> {
                equal(new int[]{0, 2, 4, 9}, BoundaryProbe.boundaries("e\u0301😀👩‍💻", BoundaryProbe.Kind.GRAPHEME, Locale.ROOT));
                return null;
            });
            for (var result : pool.invokeAll(tasks)) result.get();
        } finally { pool.shutdown(); }
        System.out.println("PASS thai=" + Arrays.toString(thai));
    }
}
