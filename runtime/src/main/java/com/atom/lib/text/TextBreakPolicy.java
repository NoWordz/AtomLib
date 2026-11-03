package com.atom.lib.text;
/** Policies operate on grapheme boundaries and do not implement a full paragraph engine. */
public enum TextBreakPolicy {
    GRAPHEME,
    CJK_UI,
    /** Prefer whitespace boundaries; preserve whitespace and split oversized words by grapheme. */
    WORD
}
