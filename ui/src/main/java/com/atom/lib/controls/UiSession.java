package com.atom.lib.controls;
import com.atom.lib.controls.internal.*;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Canvas;
import java.util.*;

/** Page-local input/focus coordinator. Borrows all rendering and text services. */
public final class UiSession implements AutoCloseable {
    private List<Control> controls=List.of();
    private Map<NodeId,Control> controlsById=Map.of();
    private final Map<Control,ControlTarget> targets=new IdentityHashMap<>();
    private final FocusState focus=new FocusState();
    private final InputDispatcher dispatcher=new InputDispatcher(this::topAt);
    private PresentationFrame displayed;
    private Set<Control> displayedInstances=Collections.newSetFromMap(new IdentityHashMap<>());
    private Viewport displayedViewport;
    private Float preparedPixelsPerUnit;
    private Viewport preparedViewport;
    private CancelReason cancelling=CancelReason.CLOSED;
    private long lastFrame=Long.MIN_VALUE,lastNow=Long.MIN_VALUE;
    private boolean closed;
    private boolean replacingControls;
    private ModalState modal;
    private Composite composite;
    private com.atom.lib.layout.internal.YogaLayoutEngine legacyModalLayout;
    private final boolean modalChild;
    private static final class Composite {
        final ModalDialog dialog; final java.util.function.Consumer<ModalCloseReason> callback; final NodeId savedFocus;
        final UiSession child; final ModalLayout layout=new ModalLayout();
        boolean shown,closing,backdropPressed,initialFocusApplied; float opacity;
        Composite(ModalDialog dialog,java.util.function.Consumer<ModalCloseReason> callback,NodeId savedFocus){this.dialog=dialog;this.callback=callback;this.savedFocus=savedFocus;child=new UiSession(List.of(),true);child.scrollViewport(layout.scroll);}
    }
    private ScrollViewport scroll;
    private boolean draggingScroll;
    private float scrollY,coordinateScale=1;
    public void scrollViewport(ScrollViewport value){requireOpen();if(scroll!=value){cancel(CancelReason.COORDINATE_SPACE_CHANGED);scroll=value;}}
    public void prepareCoordinates(float scale){requireOpen();if(!Float.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Invalid coordinate scale");if(scale!=coordinateScale){coordinateScale=scale;cancel(CancelReason.COORDINATE_SPACE_CHANGED);}}
    private final Map<NodeId,PointerTarget> modalTargets=new LinkedHashMap<>();
    private final Set<Integer> buttonsDown=new HashSet<>(),swallowedButtons=new HashSet<>();
    private final Set<KeyEvent.Key> keysDown=EnumSet.noneOf(KeyEvent.Key.class),swallowedKeys=EnumSet.noneOf(KeyEvent.Key.class);
    public UiSession(List<? extends Control> controls){this(controls,false);}
    private UiSession(List<? extends Control> controls,boolean modalChild){this.modalChild=modalChild;setControls(controls);}
    public List<Control> controls(){return controls;}
    /** Call before model update and Yoga layout, so density cancellation's
     * rollback participates in that layout rather than changing it during draw. */
    public void prepare(Viewport viewport){
        requireOpen();Objects.requireNonNull(viewport);
        Float previous=preparedPixelsPerUnit!=null?preparedPixelsPerUnit:displayedViewport==null?null:displayedViewport.pixelsPerUnit();
        var previousViewport=preparedViewport;preparedViewport=viewport;preparedPixelsPerUnit=viewport.pixelsPerUnit();
        if(previous!=null&&previous!=viewport.pixelsPerUnit())cancel(CancelReason.COORDINATE_SPACE_CHANGED);
        else if(previousViewport!=null&&(previousViewport.width()!=viewport.width()||previousViewport.height()!=viewport.height()))cancelResizeGestures();
    }
    private void cancelResizeGestures(){
        if(composite==null)return; // Existing page controls retain their continuous-resize contract.
        swallowedButtons.addAll(buttonsDown);swallowedKeys.addAll(keysDown);
        composite.child.cancel(CancelReason.COORDINATE_SPACE_CHANGED);
    }
    public void setControls(List<? extends Control> next){
        if(replacingControls)throw new IllegalStateException("Cannot recursively replace controls while cancelling a replacement");
        requireOpen();List<Control> copy=List.copyOf(next);var ids=new HashSet<NodeId>();
        for(var c:copy){
            if(c instanceof ConfirmationDialog)throw new IllegalArgumentException("Dialogs must use open()");
            if(c.owner!=null&&c.owner!=this)throw new IllegalArgumentException("Control belongs to another session");
            for(var id:c.slots())if(!ids.add(id))throw new IllegalArgumentException("Duplicate or reserved control ID: "+id);
        }
        if(modal!=null)for(var id:modal.dialog.slots())if(ids.contains(id))throw new IllegalArgumentException("Control ID collides with modal: "+id);
        if(composite!=null)for(var id:compositeLayoutIds(composite.dialog))if(ids.contains(id))throw new IllegalArgumentException("Control ID collides with modal: "+id);
        var index=new HashMap<NodeId,Control>();for(var c:copy)index.put(c.id(),c);
        var retained=new HashSet<>(copy);
        replacingControls=true;
        Throwable failure=null;
        try{
            var old=controls;controls=copy;controlsById=Map.copyOf(index);
            for(var c:old)if(!retained.contains(c)){
                try{cancelControl(c,CancelReason.REMOVED);}
                catch(RuntimeException|Error e){if(failure==null)failure=e;else if(e!=failure)failure.addSuppressed(e);}
                finally{c.owner=null;targets.remove(c);}
            }
            if(!closed){
                for(var c:copy){c.owner=this;targets.computeIfAbsent(c,this::target);}
                refreshTargets();refreshFocus();
            }
        }finally{replacingControls=false;}
        if(failure instanceof RuntimeException e)throw e;
        if(failure instanceof Error e)throw e;
    }
    private ControlTarget target(Control c){return new ControlTarget(new PointerTarget(){
        public boolean enabled(){return c.available()&&visible(c);}
        public boolean press(PointerEvent e){return c.press(e,displayed);}
        public void drag(PointerEvent e){c.drag(e,displayed);}
        public void release(PointerEvent e){c.release(e,displayed);}
        public void cancel(){c.cancel(cancelling);}
    });}
    private void refreshTargets(){
        if(modal!=null){dispatcher.setTargets(modalTargets);return;}
        var map=new LinkedHashMap<NodeId,PointerTarget>();for(var c:controls)map.put(c.id(),targets.get(c));dispatcher.setTargets(map);
    }
    private boolean visible(Control c){return visible(c,displayed,displayedInstances);}
    private boolean visible(Control c,PresentationFrame frame,Set<Control> instances){
        if(c.suspended()||frame==null||!instances.contains(c)||!frame.contains(c.id()))return false;
        var box=frame.box(c.id());var shown=box.hit().intersect(box.clip());return shown.width()>0&&shown.height()>0;
    }
    private NodeId topAt(float x,float y){
        if(modal!=null){
            if(displayed==null||!modal.shown||modal.phase==ModalState.Phase.CLOSING)return null;
            var id=displayed.topAt(modalTargets.keySet(),x,y);
            return id!=null&&modalHit(modal,id,x,y)?id:null;
        }
        if(displayed==null)return null;var ids=new HashSet<NodeId>();for(var c:controls)if(c.available()&&visible(c))ids.add(c.id());return displayed.topAt(ids,x,y);
    }
    private Control byId(NodeId id){return id==null?null:controlsById.get(id);}
    private void refreshFocus(){
        if(modal!=null)return;
        var previous=focus.current();
        focus.candidates(displayed==null?List.of():displayed.paintOrder().stream().filter(id->{var c=byId(id);return c!=null&&c.available()&&(visible(c)||(scroll!=null&&scroll.contains(id)));}).toList());
        if(!Objects.equals(previous,focus.current())){swallowedKeys.addAll(keysDown);var old=byId(previous);if(old!=null)cancelControl(old,CancelReason.FOCUS_LOST);}
    }
    private void select(NodeId id){var previous=focus.current();if(Objects.equals(previous,id))return;swallowedKeys.addAll(keysDown);var c=byId(previous);if(c!=null)cancelControl(c,CancelReason.FOCUS_LOST);focus.select(id);}
    public InputResult pointer(PointerEvent event){
        requireOpen();Objects.requireNonNull(event);
        if(event.type()==PointerEvent.Type.PRESS)buttonsDown.add(event.button());
        if(event.type()==PointerEvent.Type.RELEASE)buttonsDown.remove(event.button());
        boolean swallowed=swallowedButtons.contains(event.button())||(event.type()==PointerEvent.Type.MOVE&&!swallowedButtons.isEmpty());
        if(event.type()==PointerEvent.Type.RELEASE)swallowedButtons.remove(event.button());
        if(swallowed)return InputResult.CONSUMED;
        if(composite!=null){
            var state=composite;
            if(state.closing){swallowedButtons.addAll(buttonsDown);return InputResult.CONSUMED;}
            if(event.type()==PointerEvent.Type.CANCEL){cancel(CancelReason.FOCUS_LOST);return InputResult.CONSUMED;}
            if(!state.shown)return InputResult.CONSUMED;
            var card=displayed.box(state.dialog.id).border();
            if(event.type()==PointerEvent.Type.PRESS&&event.button()==0&&!card.contains(event.x(),event.y())){state.backdropPressed=true;return InputResult.CONSUMED;}
            if(state.backdropPressed){if(event.type()==PointerEvent.Type.RELEASE&&event.button()==0){state.backdropPressed=false;if(!card.contains(event.x(),event.y())&&state.dialog.options.dismissOnBackdrop())closeModal(ModalCloseReason.CANCELLED);}return InputResult.CONSUMED;}
            state.child.pointer(event);return InputResult.CONSUMED;
        }
        if(modal!=null){
            if(modal.phase==ModalState.Phase.CLOSING)swallowedButtons.addAll(buttonsDown);
            if(event.type()==PointerEvent.Type.CANCEL){cancel(CancelReason.FOCUS_LOST);return InputResult.CONSUMED;}
            if(event.type()==PointerEvent.Type.PRESS&&event.button()==0){var id=topAt(event.x(),event.y());if(id!=null&&!id.equals(slot(modal.dialog,"overlay"))){modal.activation=null;modal.activationTarget=null;swallowedKeys.addAll(keysDown);modal.focus.select(id);}}
            if(modal.phase!=ModalState.Phase.CLOSING)dispatcher.dispatch(event);
            return InputResult.CONSUMED;
        }
        if(draggingScroll){if(event.type()==PointerEvent.Type.MOVE){scroll.dragThumb(event.y()-scrollY);scrollY=event.y();}if((event.type()==PointerEvent.Type.RELEASE&&event.button()==0)||event.type()==PointerEvent.Type.CANCEL)draggingScroll=false;return InputResult.CONSUMED;}
        if(scroll!=null&&event.type()==PointerEvent.Type.PRESS&&event.button()==0&&scroll.thumb().height()>0&&scroll.thumb().contains(event.x(),event.y())){cancel(CancelReason.COORDINATE_SPACE_CHANGED);draggingScroll=true;scrollY=event.y();return InputResult.CONSUMED;}
        if(event.type()==PointerEvent.Type.CANCEL){boolean captured=dispatcher.captureOwner()!=null;cancel(CancelReason.FOCUS_LOST);return captured?InputResult.CONSUMED:InputResult.PASS;}
        if(event.type()==PointerEvent.Type.PRESS&&event.button()==0){refreshFocus();var id=topAt(event.x(),event.y());if(id!=null){select(id);if(dispatcher.captureOwner()==null)cancelControl(byId(id),CancelReason.FOCUS_LOST);}}
        return dispatcher.dispatch(event);
    }
    public InputResult key(KeyEvent event){
        requireOpen();Objects.requireNonNull(event);refreshFocus();
        boolean freshPress=event.pressed()&&keysDown.add(event.key());if(!event.pressed())keysDown.remove(event.key());
        if(swallowedKeys.contains(event.key())){if(!event.pressed())swallowedKeys.remove(event.key());return InputResult.CONSUMED;}
        if(composite!=null){var state=composite;if(state.closing){swallowedKeys.addAll(keysDown);return InputResult.CONSUMED;}if(event.key()==KeyEvent.Key.ESCAPE&&event.pressed()){closeModal(ModalCloseReason.CANCELLED);return InputResult.CONSUMED;}if(state.shown)state.child.key(event);return InputResult.CONSUMED;}
        if(modal!=null){if(modal.phase==ModalState.Phase.CLOSING)swallowedKeys.addAll(keysDown);modalKey(event,freshPress);return InputResult.CONSUMED;}
        if(event.key()==KeyEvent.Key.ESCAPE&&event.pressed()){boolean active=draggingScroll||controls.stream().anyMatch(Control::interacting);cancel(CancelReason.ESCAPE);return active?InputResult.CONSUMED:InputResult.PASS;}
        if(event.key()==KeyEvent.Key.TAB){if(event.pressed()){var previous=focus.current();var id=focus.step(event.shift());if(!Objects.equals(previous,id)){swallowedKeys.addAll(keysDown);var c=byId(previous);if(c!=null)cancelControl(c,CancelReason.FOCUS_LOST);}if(id!=null&&scroll!=null&&scroll.contains(id))scroll.ensureVisible(displayed.box(id).border());}return InputResult.CONSUMED;}
        var c=byId(focus.current());return c!=null&&c.available()&&c.key(event)?InputResult.CONSUMED:InputResult.PASS;
    }
    public InputResult wheel(float x,float y,float dx,float dy){requireOpen();if(composite!=null){if(!composite.closing&&composite.shown)composite.child.wheel(x,y,dx,dy);return InputResult.CONSUMED;}if(modal!=null)return InputResult.CONSUMED;if(scroll==null||!scroll.bounds().contains(x,y)||dy==0)return InputResult.PASS;cancel(CancelReason.COORDINATE_SPACE_CHANGED);scroll.scrollBy(-dy*40);return InputResult.CONSUMED;}
    public InputResult character(int codePoint){requireOpen();return modal==null&&composite==null?InputResult.PASS:InputResult.CONSUMED;}
    public void hover(float x,float y){requireOpen();if(composite!=null){if(composite.shown&&!composite.closing)composite.child.hover(x,y);return;}if(scroll!=null)scroll.hover(x,y);var id=topAt(x,y);for(var c:controls)c.hovered=c.id().equals(id);}
    public void paint(Canvas canvas,PresentationFrame frame,Viewport viewport,FrameTime time,boolean motionEnabled){
        paintInternal(canvas,frame,viewport,time,motionEnabled,Map.of(),false);
    }
    /** Paints decorations in frame order inside the same entrance layer as their descendants. */
    public void paint(Canvas canvas,PresentationFrame frame,Viewport viewport,FrameTime time,boolean motionEnabled,Map<NodeId,java.util.function.Consumer<Canvas>> decorations){
        paintInternal(canvas,frame,viewport,time,motionEnabled,Map.copyOf(decorations),true);
    }
    private static NodeId entranceRoot(PresentationFrame frame,NodeId id){
        if(!frame.hasEntrance(id))return null;
        var parent=frame.box(id).parent();while(parent!=null&&frame.hasEntrance(parent)){id=parent;parent=frame.box(id).parent();}return id;
    }
    private void paintInternal(Canvas canvas,PresentationFrame frame,Viewport viewport,FrameTime time,boolean motionEnabled,Map<NodeId,java.util.function.Consumer<Canvas>> decorations,boolean groupEntrances){
        requireOpen();Objects.requireNonNull(canvas);Objects.requireNonNull(frame);Objects.requireNonNull(viewport);Objects.requireNonNull(time);
        if(!decorations.keySet().stream().allMatch(frame::contains))throw new IllegalArgumentException("Decoration missing from presentation frame");
        if(time.frameId()<lastFrame||time.nowNanos()<lastNow)throw new IllegalArgumentException("Nonmonotonic frame time");
        for(var c:controls)validate(c,frame,viewport);
        if(modal!=null)validate(modal.dialog,frame,viewport);
        prepare(viewport);
        boolean advance=time.frameId()!=lastFrame;
        // Animation is sampled once even when a painter later fails. Hit
        // geometry is still published only after the entire paint succeeds.
        lastFrame=time.frameId();lastNow=time.nowNanos();
        int saved=canvas.getSaveCount();canvas.save();
        try{
            NodeId activeEntrance=null;int entranceSaved=-1;
            for(var id:frame.paintOrder()){
                if(groupEntrances){
                    var root=entranceRoot(frame,id);
                    if(!Objects.equals(root,activeEntrance)){
                        if(entranceSaved>=0){canvas.restoreToCount(entranceSaved);entranceSaved=-1;}
                        activeEntrance=root;
                        if(root!=null&&frame.opacity(root)<1){entranceSaved=canvas.getSaveCount();canvas.saveLayerAlpha(null,Math.round(frame.opacity(root)*255));}
                    }
                    var decoration=decorations.get(id);if(decoration!=null){int decorationSaved=canvas.getSaveCount();canvas.save();try{decoration.accept(canvas);}finally{canvas.restoreToCount(decorationSaved);}}
                }
                var c=byId(id);if(c==null||c.suspended())continue;
                if(advance||!motionEnabled)c.advance(time,motionEnabled);
                var clip=frame.box(c.id()).clip();int count=canvas.getSaveCount();canvas.save();
                try{canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(clip.x(),clip.y(),clip.width(),clip.height()));if(!groupEntrances&&frame.opacity(c.id())<1)canvas.saveLayerAlpha(null,Math.round(frame.opacity(c.id())*255));float scale=frame.renderScale(c.id());canvas.scale(scale,scale);c.paint(canvas,frame.forPainting(c.id()),new ControlVisual(c.id().equals(focus.current()),c.hover,c.press,c.position,1));}
                finally{canvas.restoreToCount(count);}
            }
            if(entranceSaved>=0)canvas.restoreToCount(entranceSaved);
            if(scroll!=null&&scroll.scrollbarVisible()&&scroll.thumb(frame).height()>0){var r=scroll.thumb(frame);try(var p=new io.github.humbleui.skija.Paint().setColor(0x88989ba5)){canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(r.x(),r.y(),r.width(),r.height(),r.width()/2),p);}}
            if(composite!=null){
                var state=composite;
                if(!frame.contains(state.dialog.id))throw new IllegalArgumentException("Visible composable modal missing from frame");
                if(advance){float target=state.closing?0:1;state.opacity=Control.approach(state.opacity,target,time.deltaSeconds(),state.dialog.context.skin().motion().modal(),motionEnabled);}
                int count=canvas.getSaveCount();canvas.saveLayerAlpha(io.github.humbleui.types.Rect.makeXYWH(0,0,viewport.width(),viewport.height()),Math.round(255*state.opacity));
                try{
                    try(var paint=new io.github.humbleui.skija.Paint().setColor(0xaa000000)){canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(0,0,viewport.width(),viewport.height()),paint);}
                    state.dialog.context.skin().paintModalSurface(canvas,frame,state.dialog.id,state.dialog.options.title(),state.dialog.context.text());
                    state.child.paint(canvas,frame,viewport,time,motionEnabled);
                }finally{canvas.restoreToCount(count);}
            }
            if(modal!=null){
                if(!frame.contains(modal.dialog.id()))throw new IllegalArgumentException("Visible modal missing from frame");
                if(advance){
                    var timing=modal.dialog.context.skin().motion();modal.advance(time.deltaSeconds(),timing.modal(),motionEnabled);
                    var action=modalTargets.get(modal.focus.current());
                    boolean pressed=modal.activation!=null||(action instanceof ActionTarget target&&target.pressed());
                    modal.press=Control.approach(modal.press,pressed?1:0,time.deltaSeconds(),timing.hover(),motionEnabled);
                }
                int count=canvas.getSaveCount();canvas.saveLayerAlpha(io.github.humbleui.types.Rect.makeXYWH(0,0,viewport.width(),viewport.height()),Math.round(255*modal.opacity));
                try{
                    // Scrim is part of the modal layer; underlying geometry is unchanged.
                    try(var paint=new io.github.humbleui.skija.Paint().setColor(0xaa000000)){canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(0,0,viewport.width(),viewport.height()),paint);}
                    float scale=frame.renderScale(modal.dialog.id());canvas.scale(scale,scale);
                    modal.dialog.paint(canvas,frame.forPainting(modal.dialog.id()),modal.focus.current(),new ControlVisual(false,0,modal.press,0,modal.opacity));
                }finally{canvas.restoreToCount(count);}
            }
        }finally{canvas.restoreToCount(saved);}
        // Cancellation callbacks can throw. Complete them before publishing any new hit boxes.
        Set<Control> nextInstances=Collections.newSetFromMap(new IdentityHashMap<>());nextInstances.addAll(controls);
        for(var c:controls)if(!visible(c,frame,nextInstances))cancelControl(c,CancelReason.REMOVED);
        displayed=frame;displayedViewport=viewport;lastFrame=time.frameId();lastNow=time.nowNanos();
        if(scroll!=null)scroll.publish(frame);
        displayedInstances=nextInstances;
        if(composite!=null){var state=composite;state.shown=true;if(!state.initialFocusApplied){state.child.focus.restore(state.dialog.options.initialFocus());state.initialFocusApplied=true;}if(state.closing&&state.opacity==0)finishComposite();}
        if(modal!=null)modal.shown=true;
        if(modal!=null&&modal.phase==ModalState.Phase.CLOSING&&modal.opacity==0)finishModal();
        refreshTargets();refreshFocus();
    }
    private void validate(Control c,PresentationFrame f,Viewport viewport){
        if(!f.contains(c.id()))return; // hidden controls have no active roles
        for(var id:c.slots()){
            var box=f.box(id);var clip=box.clip();
            if(clip.x()<0||clip.y()<0||clip.right()>viewport.width()+.01||clip.bottom()>viewport.height()+.01)throw new IllegalArgumentException("Clip outside viewport: "+id);
        }
        var root=f.unanimatedBorder(c.id());
        if((scroll==null||!scroll.contains(f,c.id()))&&(root.bottom()>viewport.height()+.01||root.right()>viewport.width()+.01||root.x()<0||root.y()<0))throw new IllegalArgumentException("Control does not fit viewport: "+c.id());
    }
    void cancelControl(Control c,CancelReason reason){
        var old=cancelling;cancelling=Objects.requireNonNull(reason);
        if(c.interacting()||c.id().equals(dispatcher.captureOwner()))swallowedKeys.addAll(keysDown);
        try{if(c.id().equals(dispatcher.captureOwner()))dispatcher.cancel();else c.cancel(reason);}finally{cancelling=old;}
    }
    // Window deactivation may lose keyup entirely; ordinary cancellation must
    // instead retain suppression until the held gesture actually ends.
    void resetPhysicalInputTracking(){keysDown.clear();swallowedKeys.clear();buttonsDown.clear();swallowedButtons.clear();}
    public void cancel(CancelReason reason){
        requireOpen();if(draggingScroll)swallowedButtons.addAll(buttonsDown);draggingScroll=false;Throwable failure=null;
        for(var c:List.copyOf(controls))try{cancelControl(c,reason);}catch(RuntimeException|Error e){if(failure==null)failure=e;else failure.addSuppressed(e);}
        if(composite!=null&&reason!=CancelReason.MODAL_OPENED){
            try{closeModal(switch(reason){case FOCUS_LOST->ModalCloseReason.FOCUS_LOST;case COORDINATE_SPACE_CHANGED->ModalCloseReason.COORDINATE_SPACE_CHANGED;case CLOSED,REMOVED->ModalCloseReason.HOST_CLOSED;default->ModalCloseReason.CANCELLED;});}
            catch(RuntimeException|Error e){if(failure==null)failure=e;else if(failure!=e)failure.addSuppressed(e);}
            finally{if(composite!=null&&(reason==CancelReason.FOCUS_LOST||reason==CancelReason.COORDINATE_SPACE_CHANGED||reason==CancelReason.CLOSED||reason==CancelReason.REMOVED))try{finishComposite();}catch(RuntimeException|Error e){if(failure==null)failure=e;else if(failure!=e)failure.addSuppressed(e);}}
        }
        if(modal!=null&&reason!=CancelReason.MODAL_OPENED)try{resolve(ConfirmationResult.CANCELLED);}catch(RuntimeException|Error e){if(failure==null)failure=e;else failure.addSuppressed(e);}
        if(failure instanceof RuntimeException e)throw e;if(failure instanceof Error e)throw e;
    }
    private void requireOpen(){if(closed)throw new IllegalStateException("UI session closed");}
    @Override public void close(){
        if(closed)return;
        try{cancel(CancelReason.CLOSED);}finally{if(composite!=null)finishComposite();if(legacyModalLayout!=null)legacyModalLayout.close();closed=true;displayed=null;if(modal!=null){modal.dialog.owner=null;modal=null;}modalTargets.clear();for(var c:controls)c.owner=null;controls=List.of();controlsById=Map.of();targets.clear();dispatcher.setTargets(Map.of());buttonsDown.clear();keysDown.clear();swallowedKeys.clear();swallowedButtons.clear();}
    }
    private NodeId slot(Control c,String role){return ControlSlots.slot(c.id(),role);}
    public void open(ConfirmationDialog dialog,java.util.function.Consumer<ConfirmationResult> onResult){
        requireOpen();Objects.requireNonNull(dialog);Objects.requireNonNull(onResult);
        if(modalChild||modal!=null||composite!=null)throw new IllegalStateException("A modal is already visible or closing");
        if(dialog.owner!=null)throw new IllegalArgumentException("Dialog already owned");
        var reserved=new HashSet<NodeId>();for(var c:controls)reserved.addAll(c.slots());
        for(var id:dialog.slots())if(reserved.contains(id))throw new IllegalArgumentException("Duplicate modal ID: "+id);
        var state=new ModalState(dialog,onResult,focus.current());modal=state;dialog.owner=this;
        swallowedButtons.addAll(buttonsDown);swallowedKeys.addAll(keysDown);
        state.focus.candidates(List.of(slot(dialog,"cancel"),slot(dialog,"confirm")));state.focus.restore(slot(dialog,"cancel"));
        modalTargets.clear();
        for(String role:List.of("overlay","cancel","confirm")){
            var id=slot(dialog,role);
            modalTargets.put(id,new ActionTarget(()->modal==state&&state.shown&&state.phase!=ModalState.Phase.CLOSING&&displayed!=null&&displayed.contains(id),
                e->modalHit(state,id,e.x(),e.y()),
                ()->resolve(role.equals("confirm")?ConfirmationResult.CONFIRMED:ConfirmationResult.CANCELLED)));
        }
        try{cancel(CancelReason.MODAL_OPENED);}finally{refreshTargets();}
    }
    public LayoutNode modalNode(Viewport viewport){
        requireOpen();Objects.requireNonNull(viewport);if(composite!=null)throw new IllegalStateException("Composable modals require modalFrame(viewport)");if(modal==null)return null;
        var dialog=modal.dialog.node(modal.dialog.context.skin().modalDialogStyle(viewport));
        return new LayoutNode(slot(modal.dialog,"overlay"),LayoutStyle.column().size(Length.points(viewport.width()),Length.points(viewport.height())).align(LayoutStyle.Align.CENTER).justify(LayoutStyle.Justify.CENTER),List.of(dialog),null,0);
    }
    public void open(ModalDialog dialog,java.util.function.Consumer<ModalCloseReason> onClosed){
        requireOpen();Objects.requireNonNull(dialog);Objects.requireNonNull(onClosed);
        if(modalChild||modal!=null||composite!=null)throw new IllegalStateException("A modal is already visible or closing");
        if(dialog.owner!=null)throw new IllegalArgumentException("Dialog already owned");
        var state=new Composite(dialog,onClosed,focus.current());
        try{syncComposite(state);cancel(CancelReason.MODAL_OPENED);}
        catch(RuntimeException|Error e){state.child.close();state.layout.close();throw e;}
        composite=state;dialog.owner=this;swallowedButtons.addAll(buttonsDown);swallowedKeys.addAll(keysDown);dispatcher.cancel();
    }
    private Set<NodeId> compositeLayoutIds(ModalDialog dialog){
        var ids=new HashSet<NodeId>();ids.add(dialog.id);
        for(var suffix:List.of("title","header","scroll","footer","actions"))ids.add(new NodeId(dialog.id.value()+"/"+suffix));
        var roots=new ArrayList<LayoutNode>();
        if(dialog.navigation!=null)roots.add(dialog.navigation.node(LayoutStyle.row()));
        if(dialog.body!=null)roots.add(dialog.body);
        for(var action:dialog.actions)roots.add(action.node(LayoutStyle.column()));
        for(var root:roots)for(var node:new LayoutTree(root).nodes())if(!ids.add(node.id()))throw new IllegalArgumentException("Duplicate or reserved modal ID: "+node.id());
        for(var control:dialog.controls())for(var id:control.slots())if(!ids.contains(id))throw new IllegalArgumentException("Modal control slot missing from layout: "+id);
        return ids;
    }
    private void syncComposite(Composite state){
        var ids=new HashSet<NodeId>();for(var c:controls)ids.addAll(c.slots());
        for(var id:compositeLayoutIds(state.dialog))if(ids.contains(id))throw new IllegalArgumentException("Modal ID collides with base control: "+id);
        state.child.setControls(state.dialog.controls());
    }
    public void closeModal(ModalCloseReason reason){
        requireOpen();Objects.requireNonNull(reason);var state=composite;if(state==null||state.closing)return;
        state.closing=true;swallowedButtons.addAll(buttonsDown);swallowedKeys.addAll(keysDown);
        Throwable failure=null;try{state.child.cancel(CancelReason.CLOSED);}catch(RuntimeException|Error e){failure=e;}
        try{state.callback.accept(reason);}catch(RuntimeException|Error e){if(failure==null)failure=e;else if(failure!=e)failure.addSuppressed(e);}
        if(failure!=null){try{finishComposite();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
        if(failure instanceof RuntimeException e)throw e;if(failure instanceof Error e)throw e;
    }
    private void finishComposite(){
        var state=composite;if(state==null)return;composite=null;state.dialog.owner=null;
        try{state.child.close();}finally{state.layout.close();swallowedButtons.addAll(buttonsDown);swallowedKeys.addAll(keysDown);refreshFocus();focus.restore(state.savedFocus);}
    }
    public PresentationFrame modalFrame(Viewport viewport){
        requireOpen();Objects.requireNonNull(viewport);
        if(composite!=null){syncComposite(composite);return composite.layout.layout(composite.dialog,viewport);}
        var root=modalNode(viewport);if(root==null)return null;
        if(legacyModalLayout==null)legacyModalLayout=new com.atom.lib.layout.internal.YogaLayoutEngine();
        var tree=new LayoutTree(root);var snapshot=legacyModalLayout.layout(tree,viewport);var dialog=root.children().get(0);var bounds=snapshot.box(dialog.id()).border();
        float fit=Math.min(1,Math.min(viewport.width()/Math.max(1,bounds.width()),viewport.height()/Math.max(1,bounds.height())));
        if(fit==1||fit<=0)return PresentationFrame.create(tree,snapshot,viewport,Map.of(),new PresentationFrame.Offset(0,0));
        var overlay=new LayoutNode(root.id(),root.style(),List.of(),null,0);
        var background=PresentationFrame.create(new LayoutTree(overlay),snapshot,viewport,Map.of(),new PresentationFrame.Offset(0,0));
        var logical=new Viewport(viewport.width()/fit,viewport.height()/fit,viewport.pixelsPerUnit()*fit);
        var offset=new PresentationFrame.Offset((logical.width()-bounds.width())/2-bounds.x(),(logical.height()-bounds.height())/2-bounds.y());
        return PresentationFrame.compose(List.of(background,PresentationFrame.create(new LayoutTree(dialog),snapshot,logical,Map.of(),offset).scaled(fit)));
    }
    private boolean modalHit(ModalState state,NodeId id,float x,float y){
        if(displayed==null||!displayed.contains(id))return false;
        var box=displayed.box(id);
        if(!box.hit().contains(x,y)||!box.clip().contains(x,y))return false;
        if(id.equals(slot(state.dialog,"overlay"))){
            var card=displayed.box(state.dialog.id());
            if(card.border().contains(x,y)&&card.clip().contains(x,y))return false;
        }
        return true;
    }
    private void modalKey(KeyEvent e,boolean freshPress){
        var state=modal;if(state.phase==ModalState.Phase.CLOSING)return;
        if(e.key()==KeyEvent.Key.ESCAPE&&e.pressed()){resolve(ConfirmationResult.CANCELLED);return;}
        if(!state.shown)return;
        if(e.key()==KeyEvent.Key.TAB){if(e.pressed()){state.activation=null;state.activationTarget=null;state.focus.step(e.shift());}return;}
        if(e.key()!=KeyEvent.Key.ENTER&&e.key()!=KeyEvent.Key.SPACE)return;
        if(e.pressed()){if(state.activation==null&&freshPress){state.activation=e.key();state.activationTarget=state.focus.current();}return;}
        if(state.activation!=e.key())return;
        var target=state.activationTarget;state.activation=null;state.activationTarget=null;
        if(Objects.equals(target,state.focus.current()))resolve(target.equals(slot(state.dialog,"confirm"))?ConfirmationResult.CONFIRMED:ConfirmationResult.CANCELLED);
    }
    private void resolve(ConfirmationResult result){
        var state=modal;if(state==null||!state.resolve())return;
        swallowedButtons.addAll(buttonsDown);swallowedKeys.addAll(keysDown);dispatcher.cancel();
        state.callback.accept(result);
    }
    private void finishModal(){
        var state=modal;dispatcher.cancel();modalTargets.clear();modal=null;state.dialog.owner=null;
        swallowedButtons.addAll(buttonsDown);swallowedKeys.addAll(keysDown);
        refreshFocus();focus.restore(state.savedFocus);
    }
}
