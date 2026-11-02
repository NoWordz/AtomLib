// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;
import com.atom.lib.layout.*;
import java.util.*;
import java.util.function.Consumer;
/** Composition of the same activation controls; disabled options are skipped by session focus. */
public final class SegmentedChoice {
    private final NodeId id;private final List<ActionButton> buttons;private ChoiceModel model;
    public SegmentedChoice(NodeId id,ControlContext context,ChoiceModel model,Consumer<String> change){
        this.id=Objects.requireNonNull(id);this.model=Objects.requireNonNull(model);Objects.requireNonNull(change);
        buttons=model.options().stream().map(o->new ActionButton(new NodeId(id.value()+"/"+o.id()),context,action(o),()->{if(!this.model.selected().equals(o.id()))change.accept(o.id());})).toList();
    }
    private ActionModel action(ChoiceModel.Option o){return new ActionModel(o.label(),o.enabled(),ActionModel.Role.SEGMENT,model.selected().equals(o.id()));}
    public List<ActionButton> controls(){return buttons;}
    public void update(ChoiceModel next){if(!model.options().stream().map(ChoiceModel.Option::id).toList().equals(next.options().stream().map(ChoiceModel.Option::id).toList()))throw new IllegalArgumentException("Option identities are stable");model=next;for(int i=0;i<buttons.size();i++)buttons.get(i).update(action(next.options().get(i)));}
    public LayoutNode node(LayoutStyle style){return new LayoutNode(id,style.direction(LayoutStyle.Direction.ROW),buttons.stream().map(b->b.node(LayoutStyle.column().flex(1,1,Length.points(0)))).toList(),null,0);}
}
