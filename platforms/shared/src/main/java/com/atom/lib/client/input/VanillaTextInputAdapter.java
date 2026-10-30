// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.input;

import com.atom.lib.component.TextField;
import com.atom.lib.input.*;
import java.util.Objects;

/**
 * Maps Minecraft/GLFW keyboard and committed-character callbacks to a real
 * AtomLib TextField. IME composition remains owned by the host text input
 * carrier; only committed Unicode is inserted here.
 */
public final class VanillaTextInputAdapter {
    private final TextField field;
    public VanillaTextInputAdapter(TextField field) { this.field=Objects.requireNonNull(field); }

    public boolean key(int keyCode,int modifiers) {
        KeyEvent.Key key=switch(keyCode){
            case 256->KeyEvent.Key.ESCAPE; case 257,335->KeyEvent.Key.ENTER;
            case 259->KeyEvent.Key.BACKSPACE; case 261->KeyEvent.Key.DELETE;
            case 262->KeyEvent.Key.RIGHT; case 263->KeyEvent.Key.LEFT; case 264->KeyEvent.Key.DOWN; case 265->KeyEvent.Key.UP;
            case 268->KeyEvent.Key.HOME; case 269->KeyEvent.Key.END; case 32->KeyEvent.Key.SPACE; case 258->KeyEvent.Key.TAB;
            case 65->KeyEvent.Key.A; case 67->KeyEvent.Key.C; case 86->KeyEvent.Key.V; case 88->KeyEvent.Key.X;
            case 90->KeyEvent.Key.Z; case 89->KeyEvent.Key.Y; default->KeyEvent.Key.OTHER;
        };
        if(key==KeyEvent.Key.OTHER)return false;
        return field.key(new KeyEvent(key,true,(modifiers&1)!=0,(modifiers&2)!=0,(modifiers&4)!=0))==InputResult.CONSUMED;
    }

    /** Call only for committed text, after the platform has handled composition. */
    public boolean committedText(String text) {
        if(text==null||text.isEmpty())return false;
        field.textInput(text);return true;
    }
    public void paste(String text) {
        if(text!=null&&!text.isEmpty())field.textInput(text.replace("\r\n"," ").replace('\r',' ').replace('\n',' ').replace('\t',' '));
    }
    /** Route events in the same coordinate space as the owning UiView. */
    public boolean pointer(PointerEvent event) {
        Objects.requireNonNull(event);
        return switch(event.type()){
            case PRESS->field.press(event); case MOVE->{field.drag(event);yield true;}
            case RELEASE->{field.release(event);yield true;} case CANCEL->{field.cancel();yield true;}
        };
    }
    public String text(){return field.text();}
    public int caret(){return field.caret();}
    public void commit(){field.commit();}
    public void cancel(){field.cancel();}
}
