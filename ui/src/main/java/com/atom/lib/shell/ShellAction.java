// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.shell;
import java.util.Objects;
public record ShellAction(String id,String label,Runnable action) {
    public ShellAction {Objects.requireNonNull(id);Objects.requireNonNull(label);Objects.requireNonNull(action);if(id.isBlank())throw new IllegalArgumentException("Empty action id");}
}
