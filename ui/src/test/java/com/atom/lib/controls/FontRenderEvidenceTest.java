package com.atom.lib.controls;
import com.atom.lib.*;
import com.atom.lib.font.*;
import com.atom.lib.design.atomchat.*;
import io.github.humbleui.skija.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FontRenderEvidenceTest {
    @Test void exportNativeWeightAndDensityComparisons() throws Exception {
        var output=java.nio.file.Path.of("../build/controls-candidate/evidence/fonts");Files.createDirectories(output);
        try(var runtime=AtomLibRuntime.open("font-evidence");var fonts=new FontRegistry(runtime)){
            fonts.registerDefaults();AtomChatFonts.register(fonts);
            StringBuilder manifest=new StringBuilder("density,role,weight,size,edging,hinting,subpixel\n");
            for(float density:new float[]{1,1.5f,2}){
                try(var surface=Surface.makeRasterN32Premul(Math.round(640*density),Math.round(200*density))){
                    var canvas=surface.getCanvas();canvas.scale(density,density);canvas.clear(0xff20242c);
                    int row=0;for(int weight:new int[]{100,400,500,700}){
                        var id=new FontId("probe-"+weight);fonts.register(id,weight==700?FontSource.bundledBold():FontSource.bundledRegular(),FontOptions.weight(weight));
                        try(var font=fonts.openFont(id,15,"中文");var face=font.getTypeface();var paint=new Paint().setColor(0xffeeeeee)){
                            assertEquals(weight,face.getFontStyle().getWeight());canvas.drawString("Noto "+weight+"  中文说明。这里包含开关、确认窗。",15,30+row++*40,font,paint);
                            manifest.append(density+",Noto,"+weight+",15,"+font.getEdging()+","+font.getHinting()+","+font.isSubpixel()+"\n");
                        }
                    }
                    try(var font=fonts.openFont(AtomChatFonts.BODY,15,"中文");var face=font.getTypeface();var paint=new Paint().setColor(0xffeeeeee)){
                        assertEquals(500,face.getFontStyle().getWeight());canvas.drawString("AC BODY 500  中文说明。这里包含开关、确认窗。",15,190,font,paint);
                    }
                    try(var image=surface.makeImageSnapshot();var data=image.encodeToData(EncodedImageFormat.PNG)){Files.write(output.resolve("weights-"+density+".png"),data.getBytes());}
                }
                try(var font=fonts.openFont(AtomChatFonts.BODY,15,"中文");var face=font.getTypeface()){assertEquals(500,face.getFontStyle().getWeight());manifest.append(density+",AC-BODY,500,15,"+font.getEdging()+","+font.getHinting()+","+font.isSubpixel()+"\n");}
            }
            Files.writeString(output.resolve("font-settings.csv"),manifest);
        }
    }
}
