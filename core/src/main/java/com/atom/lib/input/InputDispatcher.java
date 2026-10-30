package com.atom.lib.input;

import com.atom.lib.layout.NodeId;
import java.util.*;

/** A captured gesture ends exactly once, even if layout or the target list changes. */
public final class InputDispatcher {
    private HitTester hitTester;
    private Map<NodeId,PointerTarget> targets=Map.of();
    private NodeId capture;
    private PointerTarget capturedTarget;
    private int button;
    private boolean delivering;
    public InputDispatcher(HitTester hitTester){setHitTester(hitTester);}
    public void setHitTester(HitTester tester){hitTester=Objects.requireNonNull(tester);}
    public void setTargets(Map<NodeId,? extends PointerTarget> next){
        targets=Map.copyOf(next);
        if(capture!=null&&(targets.get(capture)!=capturedTarget||!capturedTarget.enabled()))cancel();
    }
    public NodeId captureOwner(){return capture;}
    public InputResult dispatch(PointerEvent event){
        if(delivering){if(event.type()==PointerEvent.Type.CANCEL){cancel();return InputResult.CONSUMED;}throw new IllegalStateException("Cannot dispatch recursively during an input callback");}
        delivering=true;
        try{return deliver(event);}finally{delivering=false;}
    }
    private InputResult deliver(PointerEvent event){
        if(event.type()==PointerEvent.Type.CANCEL){boolean active=capture!=null;cancel();return active?InputResult.CONSUMED:InputResult.PASS;}
        if(capture!=null&&!capturedTarget.enabled())cancel();
        if(capture!=null){
            if(event.type()==PointerEvent.Type.MOVE){try{capturedTarget.drag(event);}catch(RuntimeException|Error failure){abort(failure);throw failure;}return InputResult.CONSUMED;}
            if(event.type()==PointerEvent.Type.RELEASE){
                if(event.button()!=button)return InputResult.PASS;
                var target=capturedTarget;
                try{target.release(event);}catch(RuntimeException|Error failure){abort(failure);throw failure;}
                finally{if(capturedTarget==target)clearCapture();}
                return InputResult.CONSUMED;
            }
            return InputResult.CONSUMED;
        }
        if(event.type()!=PointerEvent.Type.PRESS)return InputResult.PASS;
        var id=hitTester.topAt(event.x(),event.y());var target=id==null?null:targets.get(id);
        if(target==null||!target.enabled())return InputResult.PASS;
        // Establish ownership before callbacks: press may synchronously open a
        // modal, remove the target, or close the view and cancel this gesture.
        capture=id;capturedTarget=target;button=event.button();
        try {
            if(!target.press(event)){if(capturedTarget==target)clearCapture();return InputResult.PASS;}
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
        return InputResult.CONSUMED;
    }
    public void cancel(){var target=capturedTarget;clearCapture();if(target!=null)target.cancel();}
    private void abort(Throwable original){try{cancel();}catch(RuntimeException|Error cleanup){if(cleanup!=original)original.addSuppressed(cleanup);}}
    private void clearCapture(){capture=null;capturedTarget=null;button=-1;}
}
