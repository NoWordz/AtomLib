package com.atom.lib.controls;

import com.atom.lib.input.*;
import com.atom.lib.layout.NodeId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewInputBoundaryTest {
    private ConfirmationDialog dialog(ControlFixture f){
        return new ConfirmationDialog(new NodeId("boundary"),f.context,
            new ConfirmationModel("确认？","卡片内的说明不触发结果。","取消","确认"));
    }
    private void pointer(ControlFixture f,PointerEvent.Type type,float x,float y){
        assertEquals(InputResult.CONSUMED,f.pointer(type,x,y,0));
    }
    @Test void cardTitleIconBodyAndPaddingOnlyConsumeTheirGestures(){
        try(var f=new ControlFixture()){
            var results=new ArrayList<ConfirmationResult>();f.show(480);f.session.open(dialog(f),results::add);f.show(480);
            for(String role:List.of("title","icon","body")){
                var r=f.frame.box(new NodeId("boundary/"+role)).border();float x=r.x()+r.width()/2,y=r.y()+r.height()/2;
                pointer(f,PointerEvent.Type.PRESS,x,y);pointer(f,PointerEvent.Type.RELEASE,x,y);assertTrue(results.isEmpty(),role);
            }
            var card=f.frame.box(new NodeId("boundary")).border();
            pointer(f,PointerEvent.Type.PRESS,card.x()+2,card.y()+2);pointer(f,PointerEvent.Type.RELEASE,card.x()+2,card.y()+2);
            assertTrue(results.isEmpty(),"Padding is part of the card, not the backdrop");
        }
    }
    @Test void backdropPressReleasedInsideCardDoesNotCancelButANewBackdropClickDoes(){
        try(var f=new ControlFixture()){
            var results=new ArrayList<ConfirmationResult>();f.show(480);f.session.open(dialog(f),results::add);f.show(480);
            var r=f.frame.box(new NodeId("boundary/body")).border();
            pointer(f,PointerEvent.Type.PRESS,1,1);pointer(f,PointerEvent.Type.RELEASE,r.x()+1,r.y()+1);assertTrue(results.isEmpty());
            pointer(f,PointerEvent.Type.PRESS,1,1);pointer(f,PointerEvent.Type.RELEASE,1,1);assertEquals(List.of(ConfirmationResult.CANCELLED),results);
        }
    }
    @Test void cardPressReleasedOnBackdropDoesNotCreateABackdropGesture(){
        try(var f=new ControlFixture()){
            var results=new ArrayList<ConfirmationResult>();f.show(480);f.session.open(dialog(f),results::add);f.show(480);
            var r=f.frame.box(new NodeId("boundary/body")).border();
            pointer(f,PointerEvent.Type.PRESS,r.x()+1,r.y()+1);pointer(f,PointerEvent.Type.RELEASE,1,1);assertTrue(results.isEmpty());
        }
    }
    @Test void actionButtonsStillResolveTheirOwnCompleteMouseGestures(){
        for(String role:List.of("cancel","confirm"))try(var f=new ControlFixture()){
            var results=new ArrayList<ConfirmationResult>();f.show(480);f.session.open(dialog(f),results::add);f.show(480);
            var r=f.frame.box(new NodeId("boundary/"+role)).border();float x=r.x()+r.width()/2,y=r.y()+r.height()/2;
            pointer(f,PointerEvent.Type.PRESS,x,y);pointer(f,PointerEvent.Type.RELEASE,x,y);
            assertEquals(List.of(role.equals("confirm")?ConfirmationResult.CONFIRMED:ConfirmationResult.CANCELLED),results);
        }
    }
    @Test void densityCancellationEatsAllHeldAdjustmentRepeatsUntilEachRelease(){
        try(var f=new ControlFixture()){
            f.show(480);f.key(KeyEvent.Key.TAB,true);f.key(KeyEvent.Key.TAB,false);
            f.key(KeyEvent.Key.RIGHT,true);f.key(KeyEvent.Key.UP,true);
            f.session.prepare(new com.atom.lib.geometry.Viewport(480,860,1.5f));
            f.key(KeyEvent.Key.RIGHT,true);f.key(KeyEvent.Key.RIGHT,false);f.key(KeyEvent.Key.UP,true);f.key(KeyEvent.Key.UP,false);
            assertEquals(List.of(480f),f.cancels);assertTrue(f.commits.isEmpty());assertEquals(480,f.slider.previewValue());
            f.key(KeyEvent.Key.RIGHT,true);f.key(KeyEvent.Key.RIGHT,false);assertEquals(List.of(490f),f.commits);
        }
    }
}
