package com.atom.lib.controls;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfirmationDialogTest {
    @Test void illegalRegistrationAndDuplicateIdsLeaveTheValidSessionUsable(){
        try(var f=new ControlFixture()){
            var colliding=new ConfirmationDialog(new NodeId("width"),f.context,new ConfirmationModel("确认","短说明","取消","确认"));
            assertThrows(IllegalArgumentException.class,()->f.session.setControls(List.of(colliding)));
            assertThrows(IllegalArgumentException.class,()->f.session.open(colliding,r->{}));
            f.show(480);f.press();f.release(f.thumb());assertTrue(f.cancels.isEmpty());
        }
    }
    @Test void closeIsTerminalEvenIfThePendingResultCallbackThrows(){
        try(var f=new ControlFixture()){
            int[] calls={0};var dialog=new ConfirmationDialog(new NodeId("dialog"),f.context,new ConfirmationModel("确认","短说明","取消","确认"));
            f.session.open(dialog,r->{calls[0]++;assertEquals(ConfirmationResult.CANCELLED,r);throw new IllegalStateException("business");});
            assertThrows(IllegalStateException.class,f.session::close);assertDoesNotThrow(f.session::close);assertEquals(1,calls[0]);
            assertThrows(IllegalStateException.class,()->f.session.pointer(new PointerEvent(PointerEvent.Type.PRESS,0,0,0)));
        }
    }
}
