// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.font;

import java.util.Objects;

/** Registry-local identity; use distinct ids for weight/style variants. */
public record FontId(String value) {
    public FontId {
        Objects.requireNonNull(value);
        if (value.isBlank()) throw new IllegalArgumentException("Empty font id");
    }
}
