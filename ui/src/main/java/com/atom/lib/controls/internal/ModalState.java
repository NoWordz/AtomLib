package com.atom.lib.controls.internal;
import com.atom.lib.controls.*;
import com.atom.lib.layout.NodeId;
import com.atom.lib.input.KeyEvent;
import java.util.function.Consumer;
/** One visible modal and its completion, independent of base control capture. */
public final class ModalState {
    public enum Phase { OPENING, ACTIVE, CLOSING }
    public final ConfirmationDialog dialog;
    public final Consumer<ConfirmationResult> callback;
    public final NodeId savedFocus;
    public final FocusState focus=new FocusState();
    public Phase phase=Phase.OPENING;
    public float opacity;
    public boolean shown;
    public float press;
    public KeyEvent.Key activation;
    public NodeId activationTarget;
    public ModalState(ConfirmationDialog dialog,Consumer<ConfirmationResult> callback,NodeId savedFocus){this.dialog=dialog;this.callback=callback;this.savedFocus=savedFocus;}
    public boolean resolve(){if(phase==Phase.CLOSING)return false;phase=Phase.CLOSING;activation=null;activationTarget=null;return true;}
    public void advance(double delta,double duration,boolean motion){
        float target=phase==Phase.CLOSING?0:1;
        opacity=!motion?target:opacity+Math.copySign(Math.min(Math.abs(target-opacity),(float)(delta/duration)),target-opacity);
        if(phase==Phase.OPENING&&opacity>=1)phase=Phase.ACTIVE;
    }
}
