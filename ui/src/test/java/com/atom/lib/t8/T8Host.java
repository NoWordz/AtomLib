package com.atom.lib.t8;

import com.atom.lib.*;
import com.atom.lib.font.*;
import com.atom.lib.style.*;
import com.atom.lib.text.TextService;
import com.atom.lib.view.HostContext;
import java.nio.file.Path;

/** Host-only font fixture; nothing here enters an AtomLib runtime artifact. */
public final class T8Host implements AutoCloseable {
    public final RuntimeHandle runtime=AtomLibRuntime.open();
    public final FontRegistry fonts=new FontRegistry(runtime);
    public final TextService text=new TextService(fonts);
    public final HostContext host;
    public T8Host(){
        var id=new FontId("body");String font=System.getProperty("atomlib.testFont");
        if(font==null)throw new IllegalStateException("Pass -PatomlibTestFont=<host font path>");
        fonts.register(id,FontSource.file(Path.of(font)));
        host=new HostContext(fonts,id,text,new ThemeSnapshot(new Palette(0xff12161c,0xff222831,0xff343b46,0xff4a90e2,0xffffffff,0xffeeeeee,0xffaaaaaa,0xffeb4034),new TypeScale(16,22,14),new SpacingScale(5,10,20,40),new RadiusScale(20,12),new MotionPolicy(false)),()->{});
    }
    public void close(){text.close();fonts.close();runtime.close();}
}
