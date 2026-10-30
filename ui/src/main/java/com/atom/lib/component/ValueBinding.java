package com.atom.lib.component;

public interface ValueBinding<T> {
    T read();
    void preview(T value);
    void commit(T value);
    void cancel(T valueBeforeGesture);
}
