package com.atom.lib.design;

import com.atom.lib.design.atomchat.AtomChatControlSkin;
import com.atom.lib.design.atomchat.Design;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtomDefaultTest {
    @Test void presetsPreserveTheTwelveSourcePalettesAndMaterialVotes() throws Exception {
        Class<?> api = assertDoesNotThrow(() -> Class.forName("com.atom.lib.design.atomdefault.AtomDefault"));
        List<?> presets = (List<?>) api.getMethod("presets").invoke(null);
        Object[][] expected = {
            {"minimal",0xff2563d8,0xfff7f8fa,0xff151a22,0xff596273,0xffffffff,0xff6b7280,1f,false},
            {"summer",0xff84cc16,0xfffafcf3,0xff192521,0xff56634c,0xffffffff,0xff6b7280,1f,false},
            {"elegant",0xffbd6043,0xfff5f0e8,0xff332e23,0xff71685c,0xffe8ddd0,0xff8a8377,1f,false},
            {"raven",0xffe3b341,0xff0d1117,0xfff0f4f8,0xffa4afbd,0xff161b27,0xff8b949e,1f,false},
            {"hub",0xffff9000,0xff0a0a0a,0xffffffff,0xffb0b9c7,0xff141414,0xff9ca3af,1f,false},
            {"vivid",0xffca3f70,0xfffdf0f4,0xff36212b,0xff785666,0xffffffff,0xff9a7f8c,1f,false},
            {"dusk",0xffa78bfa,0xff232633,0xfff5f6fa,0xffb3b8cd,0xff2b2e3d,0xff9ba0b5,.88f,true},
            {"mint",0xff5eead4,0xff0f2622,0xfff3fcf8,0xffb2cec3,0xff16332d,0xff9db8af,.9f,true},
            {"azure_glass",0xff82beff,0xff12263b,0xfff5f8ff,0xffbccde1,0xff29425d,0xffa9c6e8,.70f,true},
            {"plum_glass",0xffe1aff1,0xff2d1c31,0xfffff4fd,0xffd5bdda,0xff49304e,0xffd9b6e0,.72f,true},
            {"ice_glass",0xff246889,0xffe4f0f5,0xff152d3b,0xff425f70,0xfff2f8fb,0xff536e7d,.74f,true},
            {"sand_glass",0xff926027,0xfff2e7d6,0xff352819,0xff69543b,0xfffff8eb,0xff826c50,.76f,true}
        };
        assertEquals(expected.length,presets.size());
        String[] names={"id","accentColor","panelBackgroundColor","textColor","textSecondaryColor","cardColor","outlineColor","panelOpacity","blurEnabled"};
        for(int i=0;i<expected.length;i++) for(int j=0;j<names.length;j++)
            assertEquals(expected[i][j],presets.get(i).getClass().getMethod(names[j]).invoke(presets.get(i)),expected[i][0]+" "+names[j]);
        assertThrows(UnsupportedOperationException.class,presets::clear);
    }

    @Test void independentSkinsKeepTheirOwnPaletteWhenOtherHostChanges() throws Exception {
        Class<?> api=assertDoesNotThrow(()->Class.forName("com.atom.lib.design.atomdefault.AtomDefault"));
        List<?> presets=(List<?>)api.getMethod("presets").invoke(null);
        Class<?> palette=presets.get(0).getClass();
        var skinFactory=api.getMethod("skin",palette);
        AtomChatControlSkin a=(AtomChatControlSkin)skinFactory.invoke(null,presets.get(0));
        AtomChatControlSkin b=(AtomChatControlSkin)skinFactory.invoke(null,presets.get(3));
        var global=Design.host();
        assertEquals(0xff2563d8,a.style().accentColor());
        assertEquals(0xffe3b341,b.style().accentColor());
        a.updateStyle(b.style());
        assertEquals(0xffe3b341,b.style().accentColor());
        assertSame(global,Design.host());
        assertNotSame(a,b);
    }
}
