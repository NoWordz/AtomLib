// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.interaction;

/** Zoom relative to cover scale; image-centre offsets measured in source pixels. */
public record CropState(float zoom,float offsetX,float offsetY) {
    public CropState {
        if(!Float.isFinite(zoom)||zoom<1||zoom>4||!Float.isFinite(offsetX)||!Float.isFinite(offsetY))throw new IllegalArgumentException("Invalid crop state");
    }
}
