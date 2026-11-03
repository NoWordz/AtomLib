// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.layout;

/** Display-space displacement and opacity, applied to a subtree. */
public record EntrancePose(float x,float y,float opacity) {
    public EntrancePose {
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(opacity)||opacity<0||opacity>1)
            throw new IllegalArgumentException("Invalid entrance pose");
    }
}
