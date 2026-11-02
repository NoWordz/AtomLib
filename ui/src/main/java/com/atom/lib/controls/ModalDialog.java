package com.atom.lib.controls;
import com.atom.lib.layout.*;
import java.util.*;
/** Stable modal definition. Controls retain their normal session ownership rules. */
public final class ModalDialog {
    final NodeId id; final ControlContext context; final ModalOptions options;
    SegmentedChoice navigation; LayoutNode body; List<Control> bodyControls=List.of(); List<ActionButton> actions=List.of();
    UiSession owner;
    public ModalDialog(NodeId id,ControlContext context,ModalOptions options){this.id=Objects.requireNonNull(id);this.context=Objects.requireNonNull(context);this.options=Objects.requireNonNull(options);}
    public void navigation(SegmentedChoice value){navigation=value;}
    public void content(LayoutNode body,List<? extends Control> controls){this.body=Objects.requireNonNull(body);this.bodyControls=List.copyOf(controls);}
    public void actions(List<ActionButton> controls){actions=List.copyOf(controls);}
    List<Control> controls(){var all=new ArrayList<Control>();if(navigation!=null)all.addAll(navigation.controls());all.addAll(bodyControls);all.addAll(actions);return List.copyOf(all);}
}
