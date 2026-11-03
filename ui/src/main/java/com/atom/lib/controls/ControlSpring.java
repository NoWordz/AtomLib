// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;

/** Skin-owned spring parameters in the control's position domain. */
public record ControlSpring(float stiffness, float dampingRatio, float settlePosition, float settleVelocity, double staleAfterSeconds) {
    public ControlSpring {
        if(!Float.isFinite(stiffness)||stiffness<=0||!Float.isFinite(dampingRatio)||dampingRatio<0
                ||!Float.isFinite(settlePosition)||settlePosition<=0||!Float.isFinite(settleVelocity)||settleVelocity<=0
                ||!Double.isFinite(staleAfterSeconds)||staleAfterSeconds<=0)
            throw new IllegalArgumentException("Invalid control spring");
    }
}
