package com.atom.lib.devtools.preview;

import com.atom.lib.*;
import com.atom.lib.devtools.gallery.ComponentGallery;
import com.atom.lib.font.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.TextService;
import com.atom.lib.input.*;
import com.atom.lib.layout.NodeId;
import com.atom.lib.view.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import java.nio.file.*;
import java.nio.file.Path;

public final class PreviewRunner {
    private PreviewRunner(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Usage: PreviewRunner <host font> <output PNG>");
        Path font=Path.of(args[0]),output=Path.of(args[1]);
        try(var runtime=AtomLibRuntime.open("gallery");var fonts=new FontRegistry(runtime);var text=new TextService(fonts);var surface=Surface.makeRasterN32Premul(1032,720)){
            var fontId=new FontId("gallery.body");fonts.register(fontId,FontSource.file(font));
            var host=new HostContext(fonts,fontId,text,ComponentGallery.theme(),()->{});
            var canvas=surface.getCanvas();canvas.clear(0xff252b34);
            int x=24;
            for(int width:new int[]{320,640}){
                try(var view=new UiView(host,new ComponentGallery().page(width),new Viewport(width,672,1))){
                    view.paint(canvas,new FrameTime(1,0,0));
                    var row=view.frame().box(new NodeId("layout")).border();
                    view.pointer(new PointerEvent(PointerEvent.Type.MOVE,row.x()+row.width()*.72f,row.y()+row.height()*.5f,-1));
                    view.key(new KeyEvent(KeyEvent.Key.TAB,true,false,false,false));
                    int save=canvas.getSaveCount();canvas.save();
                    try{canvas.translate(x,24);canvas.clipRect(Rect.makeXYWH(0,0,width,672));view.paint(canvas,new FrameTime(1,0,0));}
                    finally{canvas.restoreToCount(save);}
                    System.out.printf("PASS gallery width=%d revision=%d nodes=%d%n",width,view.frame().revision(),view.frame().paintOrder().size());
                }
                x+=width+24;
            }
            try(var image=surface.makeImageSnapshot();var encoded=image.encodeToData(EncodedImageFormat.PNG)){
                if(encoded==null)throw new IllegalStateException("PNG encoding failed");Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,encoded.getBytes());
            }
        }
        System.out.println(output.toAbsolutePath());
    }
}
