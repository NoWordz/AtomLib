package com.atom.lib.component;

import com.atom.lib.input.KeyEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextFieldEditingTest {
    private static KeyEvent key(KeyEvent.Key k,boolean shift){return new KeyEvent(k,true,shift,false,false);}
    static final class Binding implements ValueBinding<String> {
        String value; int commits,cancels; Runnable preview=()->{},commit=()->{},cancel=()->{};
        Binding(String value){this.value=value;}
        public String read(){return value;}
        public void preview(String v){value=v;preview.run();}
        public void commit(String v){value=v;commits++;commit.run();}
        public void cancel(String v){value=v;cancels++;cancel.run();}
    }
    static final class Clip implements TextField.Clipboard {
        String value=""; public String read(){return value;} public void write(String value){this.value=value;}
    }
    @Test void backspaceAndSelectionNeverSplitCombiningEmojiOrZwjClusters(){
        var b=new Binding("A😀é👩‍💻B");var f=new TextField("edit",b,new Clip());f.focus();
        f.key(key(KeyEvent.Key.BACKSPACE,false));assertEquals("A😀é👩‍💻",f.text());
        f.key(key(KeyEvent.Key.BACKSPACE,false));assertEquals("A😀é",f.text());
        f.key(key(KeyEvent.Key.BACKSPACE,false));assertEquals("A😀",f.text());
        f.key(key(KeyEvent.Key.LEFT,true));f.insertText("中");assertEquals("A中",f.text());
        assertThrows(IllegalArgumentException.class,()->f.select(0,100));f.cancel();assertEquals("A😀é👩‍💻B",b.value);
    }
    @Test void clipboardCommandsUseTheSameSelectionAndCommitOnlyOnce(){
        var b=new Binding("A😀B");var clip=new Clip();var f=new TextField("edit",b,clip);f.focus();f.select(1,3);
        f.command(TextField.Command.COPY);assertEquals("😀",clip.value);
        f.command(TextField.Command.CUT);assertEquals("AB",f.text());
        clip.value="é";f.command(TextField.Command.PASTE);assertEquals("AéB",f.text());
        f.commit();f.commit();assertEquals(1,b.commits);assertEquals(0,b.cancels);assertFalse(f.isEditing());
    }
    @Test void previewFailureRollsBackAndPreservesCleanupAsSuppressed(){
        var b=new Binding("before");var f=new TextField("edit",b,new Clip());f.focus();
        var primary=new IllegalStateException("preview");var cleanup=new IllegalStateException("cleanup");
        b.preview=()->{throw primary;};b.cancel=()->{throw cleanup;};
        assertSame(primary,assertThrows(IllegalStateException.class,()->f.insertText("!")));
        assertEquals("before",f.text());assertFalse(f.isEditing());assertEquals(1,b.cancels);assertSame(cleanup,primary.getSuppressed()[0]);
    }
    @Test void commitFailureRestoresMemoryAndEscapeCancelsInsteadOfSubmitting(){
        var b=new Binding("initial");var f=new TextField("edit",b,new Clip());f.focus();f.insertText("!");
        b.commit=()->{throw new IllegalStateException("store failed");};assertThrows(IllegalStateException.class,f::commit);
        assertEquals("initial",b.value);assertFalse(f.isEditing());assertEquals(1,b.cancels);
        b.commit=()->{};f.focus();f.insertText("?");f.key(key(KeyEvent.Key.ESCAPE,false));assertEquals("initial",b.value);assertEquals(2,b.cancels);
    }
    @Test void malformedInputAndNonBoundarySelectionsDoNotChangeTheDraft(){
        var b=new Binding("😀");var f=new TextField("edit",b,new Clip());f.focus();
        assertThrows(IllegalArgumentException.class,()->f.select(0,1));
        assertThrows(IllegalArgumentException.class,()->f.insertText(String.valueOf((char)0xd800)));
        assertThrows(IllegalArgumentException.class,()->f.insertText("\n"));assertEquals("😀",f.text());
    }
    @Test void previewCanSynchronouslyCancelWithoutRestartingTheTransaction(){
        var b=new Binding("initial");var f=new TextField("edit",b,new Clip());f.focus();b.preview=f::cancel;
        f.insertText("!");assertFalse(f.isEditing());assertEquals("initial",f.text());assertEquals(0,b.commits);assertEquals(1,b.cancels);
    }
}
