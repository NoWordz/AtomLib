package com.atom.lib.component;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;
import java.util.List;
public interface UiElement {
    NodeId id();
    LayoutNode layout(HostContext host);
    void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time);
    default List<UiElement> children(){return List.of();}
    default PointerTarget pointerTarget(){return null;}
    default InputResult key(KeyEvent event){return InputResult.PASS;}
    default void textInput(String text){}
    default void focusChanged(boolean focused){}
    /** Called once when this element is removed as an overlay content root. */
    default void overlayClosed(){}
    default void geometry(LayoutBox box,HostContext host){}
}
