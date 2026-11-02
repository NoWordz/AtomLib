// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.design.atomchat;

/** Immutable, session-local AC palette and geometry configuration. */
public record AtomChatControlStyle(float cornerRadius, boolean halfScreenMode, int accentColor,
                                  int textSecondaryColor, float cardTint, int cardColor,
                                  int panelBackgroundColor, int textColor) implements DesignHost {
    public AtomChatControlStyle {
        if(!Float.isFinite(cornerRadius)||cornerRadius<0||!Float.isFinite(cardTint)||cardTint<0||cardTint>1)
            throw new IllegalArgumentException("Invalid control style");
    }
    public static AtomChatControlStyle defaults(){return from(Design.defaultHost(),0xfff3f4f6);}
    public static AtomChatControlStyle from(DesignHost h,int text){
        return new AtomChatControlStyle(h.cornerRadius(),h.halfScreenMode(),h.accentColor(),h.textSecondaryColor(),h.cardTint(),h.cardColor(),h.panelBackgroundColor(),text);
    }
}
