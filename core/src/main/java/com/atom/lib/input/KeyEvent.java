package com.atom.lib.input;

import java.util.Objects;

public record KeyEvent(Key key,boolean pressed,boolean shift,boolean control,boolean alt) {
    public enum Key { TAB, ESCAPE, LEFT, RIGHT, UP, DOWN, HOME, END, ENTER, SPACE, BACKSPACE, DELETE, OTHER, A, C, V, X, Z, Y }
    public KeyEvent { Objects.requireNonNull(key); }
}
