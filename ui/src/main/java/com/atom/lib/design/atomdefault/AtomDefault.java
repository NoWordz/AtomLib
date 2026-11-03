// SPDX-License-Identifier: MIT
// Copyright (c) 2026 E33EPUS
// Palette values adapted from AtomChat main@6452920, shared/src/main/java/com/atom/chat/theme/ThemeService.java.
// Changes: host-independent semantic colours and material values; chat and configuration fields excluded.
// Full license: licenses/AtomChat-MIT.txt.
package com.atom.lib.design.atomdefault;

import com.atom.lib.design.atomchat.AtomChatControlSkin;
import com.atom.lib.design.atomchat.AtomChatControlStyle;
import java.util.List;
import java.util.Objects;

/** Optional default skin and immutable, host-owned palette choices. */
public final class AtomDefault {
    private AtomDefault() {}

    // Semantic colour values adapted from AtomChat ThemeService, MIT; see licenses/AtomChat-MIT.txt.
    private static final List<AtomDefaultPalette> PRESETS = List.of(
        new AtomDefaultPalette("minimal",0xff2563d8,0xfff7f8fa,0xff151a22,0xff596273,0xffffffff,0xff6b7280,1f,false),
        new AtomDefaultPalette("summer",0xff84cc16,0xfffafcf3,0xff192521,0xff56634c,0xffffffff,0xff6b7280,1f,false),
        new AtomDefaultPalette("elegant",0xffbd6043,0xfff5f0e8,0xff332e23,0xff71685c,0xffe8ddd0,0xff8a8377,1f,false),
        new AtomDefaultPalette("raven",0xffe3b341,0xff0d1117,0xfff0f4f8,0xffa4afbd,0xff161b27,0xff8b949e,1f,false),
        new AtomDefaultPalette("hub",0xffff9000,0xff0a0a0a,0xffffffff,0xffb0b9c7,0xff141414,0xff9ca3af,1f,false),
        new AtomDefaultPalette("vivid",0xffca3f70,0xfffdf0f4,0xff36212b,0xff785666,0xffffffff,0xff9a7f8c,1f,false),
        new AtomDefaultPalette("dusk",0xffa78bfa,0xff232633,0xfff5f6fa,0xffb3b8cd,0xff2b2e3d,0xff9ba0b5,.88f,true),
        new AtomDefaultPalette("mint",0xff5eead4,0xff0f2622,0xfff3fcf8,0xffb2cec3,0xff16332d,0xff9db8af,.9f,true),
        new AtomDefaultPalette("azure_glass",0xff82beff,0xff12263b,0xfff5f8ff,0xffbccde1,0xff29425d,0xffa9c6e8,.70f,true),
        new AtomDefaultPalette("plum_glass",0xffe1aff1,0xff2d1c31,0xfffff4fd,0xffd5bdda,0xff49304e,0xffd9b6e0,.72f,true),
        new AtomDefaultPalette("ice_glass",0xff246889,0xffe4f0f5,0xff152d3b,0xff425f70,0xfff2f8fb,0xff536e7d,.74f,true),
        new AtomDefaultPalette("sand_glass",0xff926027,0xfff2e7d6,0xff352819,0xff69543b,0xfffff8eb,0xff826c50,.76f,true)
    );

    public static List<AtomDefaultPalette> presets() { return PRESETS; }

    /** Retains the existing shipped control appearance; no preset is selected implicitly. */
    public static AtomChatControlSkin skin() { return new AtomChatControlSkin(AtomChatControlStyle.defaults(), false, true); }

    /** Uses the source presets' card tint; host remains responsible for panel opacity and blur. */
    public static AtomChatControlSkin skin(AtomDefaultPalette palette) {
        return new AtomChatControlSkin(Objects.requireNonNull(palette, "palette").controlStyle(20, false, 1), true, true);
    }
}
