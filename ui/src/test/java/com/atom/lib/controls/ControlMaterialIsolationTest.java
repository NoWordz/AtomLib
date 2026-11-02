package com.atom.lib.controls;
import com.atom.lib.design.atomchat.*;
import io.github.humbleui.skija.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ControlMaterialIsolationTest {
    @Test void explicitCardsNeverReadOrSwapTheGlobalHost(){
        var a=new AtomChatControlStyle(20,false,0xff3366ff,0xffaaaaaa,1,0xff202020,0xff101010,0xffffffff);
        var b=new AtomChatControlStyle(0,false,0xffff3300,0xff333333,1,0xffeeeeee,0xffffffff,0xff000000);
        byte[] first=render(a);var global=Design.host();
        assertFalse(Arrays.equals(first,render(b)));assertArrayEquals(first,render(a));assertSame(global,Design.host());
    }
    private byte[] render(DesignHost style){
        try(var surface=Surface.makeRasterN32Premul(120,80)){
            UiCards.drawCard(surface.getCanvas(),10,10,100,60,8,1,style,(x,y,w,h,r,s)->{});
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){return data.getBytes();}
        }
    }
}
