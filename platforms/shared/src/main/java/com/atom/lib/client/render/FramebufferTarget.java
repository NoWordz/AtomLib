// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import java.util.Objects;

/** Borrowed host-owned RGBA8 framebuffer and attachment identity, in physical pixels. */
public record FramebufferTarget(Object identity, int framebuffer, int colorTexture, int width, int height) {
    public FramebufferTarget {
        Objects.requireNonNull(identity, "identity");
        if (framebuffer <= 0 || colorTexture <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Expected a non-default color framebuffer with positive dimensions");
        }
    }
}
