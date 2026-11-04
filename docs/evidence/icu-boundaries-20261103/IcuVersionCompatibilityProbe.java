package com.atom.lib.text;

import java.util.Locale;

/** Run the compiled adapter against each candidate ICU jar in a fresh JVM. */
public class IcuVersionCompatibilityProbe {
    public static void main(String[] args) {
        if (!UnicodeBoundaries.graphemes("e\u0301😀👩‍💻", Locale.ROOT).toString().equals("[0, 2, 4, 9]")) throw new AssertionError();
        if (!UnicodeBoundaries.words("hello world", Locale.ROOT).toString().equals("[0, 5, 6, 11]")) throw new AssertionError();
        if (!UnicodeBoundaries.lines("hello world", Locale.ROOT).toString().equals("[0, 6, 11]")) throw new AssertionError();
        System.out.println("PASS ICU=" + com.ibm.icu.util.VersionInfo.ICU_VERSION);
    }
}
