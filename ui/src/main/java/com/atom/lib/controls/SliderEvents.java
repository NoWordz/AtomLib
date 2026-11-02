package com.atom.lib.controls;
public interface SliderEvents {
    void preview(float value);
    void commit(float initialValue,float value);
    void cancel(float value,CancelReason reason);
}
