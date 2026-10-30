package com.atom.lib.overlay;

import com.atom.lib.input.FocusManager;
import com.atom.lib.layout.NodeId;
import java.util.*;

/** This stack is the single source of truth for both painting and interception. */
public final class OverlayHost {
    public record Overlay(NodeId id,boolean modal,List<NodeId> interactive){public Overlay{Objects.requireNonNull(id);interactive=List.copyOf(interactive);}}
    private final List<Overlay> overlays=new ArrayList<>();
    private final FocusManager focus;
    private final Runnable cancelCapture;
    public OverlayHost(FocusManager focus,Runnable cancelCapture){this.focus=Objects.requireNonNull(focus);this.cancelCapture=Objects.requireNonNull(cancelCapture);}
    public List<Overlay> snapshot(){return List.copyOf(overlays);}
    public boolean blocking(){return overlays.stream().anyMatch(Overlay::modal);}
    public void open(Overlay overlay){
        if(overlays.stream().anyMatch(o->o.id().equals(overlay.id())))throw new IllegalArgumentException("Duplicate overlay: "+overlay.id());
        if(overlay.modal()){cancelCapture.run();focus.pushModal(overlay.interactive());}overlays.add(overlay);
    }
    public boolean escape(){if(overlays.isEmpty())return false;closeTop();return true;}
    public void closeTop(){
        if(overlays.isEmpty())throw new IllegalStateException("No overlay");var top=overlays.remove(overlays.size()-1);Throwable failure=null;
        // Complete both structural changes before host callbacks can open a
        // replacement modal; otherwise popModal would remove the new layer.
        try{if(top.modal())focus.popModal();}catch(RuntimeException|Error error){failure=error;}
        try{cancelCapture.run();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
        rethrow(failure);
    }
    public boolean allows(NodeId id){if(id==null)return false;for(int i=overlays.size()-1;i>=0;i--){var overlay=overlays.get(i);if(overlay.interactive().contains(id))return true;if(overlay.modal())return false;}return true;}
    public void clear(){Throwable failure=null;while(!overlays.isEmpty()){try{closeTop();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}}rethrow(failure);}
    private static void rethrow(Throwable failure){if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;}
}
