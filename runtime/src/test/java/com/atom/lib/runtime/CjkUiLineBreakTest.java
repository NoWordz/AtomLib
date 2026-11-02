package com.atom.lib.runtime;
import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.*;
import com.atom.lib.layout.*;
import com.atom.lib.text.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CjkUiLineBreakTest {
    @Test void punctuationBacktracksWithoutLossAndRespectsExplicitNewlines(){
        try(var runtime=AtomLibRuntime.open("cjk");var fonts=new FontRegistry(runtime);var text=new TextService(fonts,TextBreakPolicy.CJK_UI)){
            fonts.registerDefaults();
            String value="中文说明。这里包含开关、确认窗（测试）、组合é文字。";
            for(int width=60;width<=220;width+=5){
                var result=text.layout(new TextRequest(value,FontRegistry.DEFAULT_REGULAR,15,-1),new Constraints(Constraints.Axis.atMost(width),Constraints.Axis.undefined()));
                StringBuilder full=new StringBuilder();
                for(var line:result.lines()){
                    String s=value.substring(line.start(),line.end());full.append(s);
                    assertFalse("。，、；：！？）】》」』〕〉”’".contains(s.substring(0,1)),s);
                    assertFalse("（【《「『〔〈“‘".contains(s.substring(s.length()-1)),s);
                    assertTrue(line.width()<=width+.01f);
                }assertEquals(value,full.toString());
            }
            var explicit=text.layout(new TextRequest("中文\r\n确认",FontRegistry.DEFAULT_REGULAR,15,-1),new Constraints(Constraints.Axis.undefined(),Constraints.Axis.undefined()));assertEquals(2,explicit.lines().size());
            var truncated=text.layout(new TextRequest(value,FontRegistry.DEFAULT_REGULAR,15,-1,1,true),new Constraints(Constraints.Axis.atMost(60),Constraints.Axis.undefined()));assertTrue(truncated.truncated());assertTrue(truncated.lines().get(0).width()<=60);
            var narrow=text.layout(new TextRequest("中。",FontRegistry.DEFAULT_REGULAR,15,-1),new Constraints(Constraints.Axis.atMost(16),Constraints.Axis.undefined()));assertEquals(2,narrow.lines().size());
        }
    }
}
