// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.design.atomchat;

import com.atom.lib.font.*;

/** Opt-in AC font roles; never replaces generic defaults or fallback order. */
public final class AtomChatFonts {
    public static final FontId BODY=new FontId("atomlib/atomchat-body");
    public static final FontId HEADING=new FontId("atomlib/atomchat-heading");
    private AtomChatFonts(){}
    public static void register(FontRegistry fonts){
        fonts.register(BODY,FontSource.bundledRegular(),FontOptions.weight(500));
        fonts.register(HEADING,FontSource.bundledBold(),FontOptions.weight(700));
    }
}
