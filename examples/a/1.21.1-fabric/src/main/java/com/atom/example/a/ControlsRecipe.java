package com.atom.example.a;
import com.atom.lib.controls.*;
import com.atom.lib.input.KeyEvent;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.client.render.MinecraftRenderBridge;
import io.github.humbleui.skija.Canvas;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Compiled MC callbacks recipe; caller composes and lays out its page. */
public final class ControlsRecipe extends Screen {
    public interface Painter { void paint(Canvas canvas,Viewport viewport,FrameTime time); }
    private final UiSession session;
    private final ControlScreenAdapter input;
    private final Painter painter;
    private final Runnable dispose;
    private long frameId,lastNanos;
    public ControlsRecipe(UiSession session,Painter painter,Runnable dispose){
        super(Text.literal("AtomLib controls"));this.session=session;input=new ControlScreenAdapter(session);this.painter=painter;this.dispose=dispose;
    }
    @Override public void render(DrawContext graphics,int x,int y,float partial){
        var window=client.getWindow();float ratio=(float)window.getFramebufferWidth()/window.getScaledWidth();
        input.windowFocused(client.isWindowFocused());input.hover(x,y);
        var viewport=new Viewport(width,height,ratio);long now=System.nanoTime();var time=new FrameTime(++frameId,now,lastNanos==0?0:(now-lastNanos)/1e9);lastNanos=now;
        session.prepare(viewport);
        graphics.draw();
        MinecraftRenderBridge.get().drawFrame((canvas,borrowed)->{
            int saved=canvas.getSaveCount();canvas.save();
            try{canvas.scale(ratio,ratio);painter.paint(canvas,viewport,time);}finally{canvas.restoreToCount(saved);}
        });
    }
    @Override public boolean mouseClicked(double x,double y,int b){return input.press(x,y,b)||super.mouseClicked(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return input.drag(x,y,b,dx,dy)||super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){return input.release(x,y,b)||super.mouseReleased(x,y,b);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return input.scroll(x,y,dx,dy)||super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean charTyped(char c,int modifiers){return input.character(c)||super.charTyped(c,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){return input.key(event(key,true,modifiers))||super.keyPressed(key,scan,modifiers);}
    @Override public boolean keyReleased(int key,int scan,int modifiers){return input.key(event(key,false,modifiers))||super.keyReleased(key,scan,modifiers);}
    @Override public void removed(){try{input.removed();}finally{try{session.close();}finally{dispose.run();}}super.removed();}
    private KeyEvent event(int key,boolean down,int modifiers){
        var mapped=switch(key){case GLFW.GLFW_KEY_TAB->KeyEvent.Key.TAB;case GLFW.GLFW_KEY_ESCAPE->KeyEvent.Key.ESCAPE;
            case GLFW.GLFW_KEY_LEFT->KeyEvent.Key.LEFT;case GLFW.GLFW_KEY_RIGHT->KeyEvent.Key.RIGHT;case GLFW.GLFW_KEY_UP->KeyEvent.Key.UP;case GLFW.GLFW_KEY_DOWN->KeyEvent.Key.DOWN;
            case GLFW.GLFW_KEY_HOME->KeyEvent.Key.HOME;case GLFW.GLFW_KEY_END->KeyEvent.Key.END;case GLFW.GLFW_KEY_ENTER,GLFW.GLFW_KEY_KP_ENTER->KeyEvent.Key.ENTER;case GLFW.GLFW_KEY_SPACE->KeyEvent.Key.SPACE;default->KeyEvent.Key.OTHER;};
        return new KeyEvent(mapped,down,(modifiers&GLFW.GLFW_MOD_SHIFT)!=0,(modifiers&GLFW.GLFW_MOD_CONTROL)!=0,(modifiers&GLFW.GLFW_MOD_ALT)!=0);
    }
}
