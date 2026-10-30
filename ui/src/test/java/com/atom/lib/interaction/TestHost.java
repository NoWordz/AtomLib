// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.interaction;
import com.atom.lib.*;
import com.atom.lib.font.*;
import com.atom.lib.style.*;
import com.atom.lib.text.TextService;
import com.atom.lib.view.HostContext;
import java.nio.file.Path;
public final class TestHost implements AutoCloseable {
    public final RuntimeHandle runtime=AtomLibRuntime.open("interaction-shell-test");
    public final FontRegistry fonts=new FontRegistry(runtime);
    public final TextService text=new TextService(fonts);
    public final HostContext host;
    public TestHost(){
        String path=System.getProperty("atomlib.testFont");if(path==null)throw new IllegalStateException("Explicit host test font is required");
        FontId id=new FontId("body");fonts.register(id,FontSource.file(Path.of(path)));
        host=new HostContext(fonts,id,text,new ThemeSnapshot(new Palette(0xffeeeeee,0xffffffff,0xffcccccc,0xff2277cc,0xffffffff,0xff111111,0xff777777,0xffcc2222),new TypeScale(16,22,14),new SpacingScale(4,8,16,40),new RadiusScale(12,8),new MotionPolicy(false)),()->{});
    }
    @Override public void close(){text.close();fonts.close();runtime.close();}
}
