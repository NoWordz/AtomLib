// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;
import com.atom.lib.layout.*;
import java.util.*;
import java.util.function.Consumer;
/** Host controls expanded state; children are omitted from the next displayed frame when collapsed. */
public final class Disclosure {
    private final ActionButton button;private final String title,icon;private boolean expanded,enabled;
    private List<Control> children=List.of();private boolean bound;
    /** A SettingsGroup binds its stable children once; collapse cancels before the next host layout. */
    public void bindChildren(List<? extends Control> children){if(bound)throw new IllegalStateException("Disclosure already owns a group");this.children=List.copyOf(children);bound=true;for(var c:this.children)c.suspend(!expanded);}
    public Disclosure(NodeId id,ControlContext context,String title,String icon,boolean expanded,boolean enabled,Consumer<Boolean> change){this.title=Objects.requireNonNull(title);this.icon=Objects.requireNonNull(icon);this.expanded=expanded;this.enabled=enabled;button=new ActionButton(id,context,model(),()->change.accept(!this.expanded));}
    private ActionModel model(){return new ActionModel(title,enabled,ActionModel.Role.DISCLOSURE,expanded,icon);}
    public void update(boolean expanded,boolean enabled){this.expanded=expanded;this.enabled=enabled;button.update(model());Throwable failure=null;for(var c:children)try{c.suspend(!expanded);}catch(RuntimeException|Error e){if(failure==null)failure=e;else if(e!=failure)failure.addSuppressed(e);}if(failure instanceof RuntimeException e)throw e;if(failure instanceof Error e)throw e;}
    public ActionButton control(){return button;}
    public LayoutNode node(LayoutStyle style){return button.node(style);}
    public boolean expanded(){return expanded;}
}
