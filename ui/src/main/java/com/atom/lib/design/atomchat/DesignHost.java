package com.atom.lib.design.atomchat;

/**
 * Host-supplied state the AtomChat look reads while painting.
 *
 * <p>Tokens never reach into a host's config directly. A consumer that wants
 * the AtomChat look either accepts {@link Design}'s defaults or installs its
 * own host so the palette follows its own settings.
 */
public interface DesignHost {
    /** Corner radius on the 1080p basis. {@code 20} is AtomChat's default. */
    float cornerRadius();

    /** True while the panel is attached to a screen edge, which squares off
     *  the outer corners. */
    boolean halfScreenMode();

    int accentColor();

    int textSecondaryColor();

    /** Card fill opacity, {@code 0..1}. */
    float cardTint();

    int cardColor();

    int panelBackgroundColor();
}
