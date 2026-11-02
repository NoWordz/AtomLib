// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.font;

/** An explicit real font weight, or the source's default instance. */
public record FontOptions(Float weight) {
    public FontOptions {
        if(weight!=null&&(!Float.isFinite(weight)||weight<1||weight>1000))
            throw new IllegalArgumentException("Weight must be finite in 1..1000");
    }
    public static FontOptions sourceDefault(){return new FontOptions(null);}
    public static FontOptions weight(float weight){return new FontOptions(weight);}
}
