package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.*;
import com.atom.lib.text.*;
import com.atom.lib.layout.*;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FontWeightTest {
    @Test void realWeightsSurviveReloadAndInvalidReplacementIsAtomic(){
        try(var runtime=AtomLibRuntime.open("weight");var fonts=new FontRegistry(runtime);var text=new TextService(fonts)){
            fonts.registerDefaults();
            var id=new FontId("body");fonts.register(id,FontSource.bundledRegular(),FontOptions.weight(500));
            var bold=new FontId("bold");fonts.register(bold,FontSource.bundledBold(),FontOptions.weight(700));
            assertWeight(fonts,id,500);assertWeight(fonts,bold,700);
            long before=fonts.generation();
            assertThrows(IllegalArgumentException.class,()->fonts.register(id,FontSource.bundledRegular(),FontOptions.weight(950)));
            assertThrows(IllegalArgumentException.class,()->fonts.register(id,FontSource.bundledBold(),FontOptions.weight(500)));
            assertEquals(before,fonts.generation());assertWeight(fonts,id,500);
            var old=text.layout(new TextRequest("中文",id,15,-1),new Constraints(Constraints.Axis.undefined(),Constraints.Axis.undefined()));
            fonts.reload();assertWeight(fonts,id,500);assertWeight(fonts,bold,700);
            try(var surface=Surface.makeRasterN32Premul(100,100)){
                assertThrows(IllegalStateException.class,()->text.draw(surface.getCanvas(),old,0,0));
            }
            fonts.register(id,FontSource.bundledRegular());assertWeight(fonts,id,100);
        }
    }
    @Test void failedReloadKeepsAllOldFacesAndGeneration(){
        try(var runtime=AtomLibRuntime.open("reload");var fonts=new FontRegistry(runtime)){
            boolean[] fail={false};var id=new FontId("changing");
            fonts.register(id,()->{if(fail[0])return new byte[]{1};return FontSource.bundledRegular().read();},FontOptions.weight(500));
            fonts.registerDefaults();long generation=fonts.generation();fail[0]=true;
            assertThrows(IllegalArgumentException.class,fonts::reload);
            assertEquals(generation,fonts.generation());assertWeight(fonts,id,500);assertWeight(fonts,FontRegistry.DEFAULT_REGULAR,400);
        }
    }
    @Test void optionsRejectInvalidValues(){
        for(float w:new float[]{0,-1,1001,Float.NaN,Float.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->FontOptions.weight(w));
    }
    private static void assertWeight(FontRegistry fonts,FontId id,int weight){
        try(var font=fonts.openFont(id,15,"中文");var face=font.getTypeface()){
            assertEquals(weight,face.getFontStyle().getWeight());assertEquals(FontEdging.ANTI_ALIAS,font.getEdging());assertEquals(FontHinting.NORMAL,font.getHinting());assertTrue(font.isSubpixel());
        }
    }
}
