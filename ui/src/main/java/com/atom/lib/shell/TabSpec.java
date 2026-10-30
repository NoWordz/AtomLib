// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.shell;
import java.util.Objects;
public record TabSpec(String id,String label) {
    public TabSpec {Objects.requireNonNull(id);Objects.requireNonNull(label);if(id.isBlank())throw new IllegalArgumentException("Empty tab id");}
}
