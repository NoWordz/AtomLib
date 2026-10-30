// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.interaction;

import java.util.Objects;

/** Offsets are logical UI units relative to the chosen anchor; scale is uniform. */
public record HudPlacement(HudAnchor anchor,float offsetX,float offsetY,float scale) {
    public HudPlacement {
        Objects.requireNonNull(anchor);
        if(!Float.isFinite(offsetX)||!Float.isFinite(offsetY)||!Float.isFinite(scale)||scale<.5f||scale>2)
            throw new IllegalArgumentException("Invalid HUD placement");
    }
}
