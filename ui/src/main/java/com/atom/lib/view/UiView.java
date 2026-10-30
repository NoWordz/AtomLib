package com.atom.lib.view;

import com.atom.lib.component.*;
import com.atom.lib.geometry.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.overlay.*;
import com.atom.lib.scroll.ScrollView;
import com.atom.lib.style.ThemeSnapshot;
import io.github.humbleui.skija.Canvas;
import java.util.*;
import java.util.function.Supplier;

/** Owns one view's displayed layers, focus and capture; borrows host fonts/text/Canvas. */
public final class UiView implements AutoCloseable {
    private final Thread owner=Thread.currentThread();
    private final LayoutEngine engine=new YogaLayoutEngine();
    private HostContext host;
    private UiElement root;
    private Viewport viewport;
    private PresentationFrame frame;
    private Map<NodeId,UiElement> elements=Map.of();
    private final Map<NodeId,ScrollView> scroll=new HashMap<>();
    private final FocusManager focus=new FocusManager();
    private final InputDispatcher input=new InputDispatcher((x,y)->null);
    private final OverlayHost overlays=new OverlayHost(focus,this::cancelGestures);
    // Metadata/resources only. OverlayHost.snapshot is the authoritative layer order.
    private final Map<NodeId,OverlayEntry> overlayEntries=new LinkedHashMap<>();
    private Set<NodeId> allowed=Set.of();
    private boolean closed,closing,refreshing,suspended,notifyingFocus,layingOut;
    private long structure;
    private PointerTarget keyGesture;
    private UiElement notifiedFocus;

    private static final class OverlayEntry implements AutoCloseable {
        final OverlaySpec spec;
        final NodeId container=new NodeId("atomlib.overlay.viewport."+UUID.randomUUID());
        final YogaLayoutEngine engine=new YogaLayoutEngine();
        OverlayEntry(OverlaySpec spec){this.spec=spec;}
        @Override public void close(){engine.close();}
    }
    public UiView(HostContext host,UiElement root,Viewport viewport){this.host=Objects.requireNonNull(host);this.root=Objects.requireNonNull(root);this.viewport=Objects.requireNonNull(viewport);}
    public void setRoot(UiElement root){
        checkOpen();var next=indexAll(Objects.requireNonNull(root),null);this.root=root;structure++;
        var captured=input.captureOwner();var old=captured==null?null:elements.get(captured);var replacement=next.get(captured);
        if(captured!=null&&(replacement==null||old==null||replacement.pointerTarget()!=old.pointerTarget()))input.cancel();
        if(keyGesture!=null&&next.values().stream().noneMatch(e->e.pointerTarget()==keyGesture)){var target=keyGesture;keyGesture=null;target.cancel();}
    }
    public void setTheme(ThemeSnapshot theme){checkOpen();host=host.withTheme(theme);}
    public void setViewport(Viewport viewport){checkOpen();this.viewport=Objects.requireNonNull(viewport);if(viewport.width()==0||viewport.height()==0)cancelGestures();}
    /** Compatibility bridge for an external host modal. Prefer showOverlay for library content. */
    public void suspendInput(boolean suspended){checkOpen();boolean cancel=suspended&&!this.suspended;this.suspended=suspended;if(cancel)cancelGestures();}
    public NodeId captureOwner(){return input.captureOwner();}
    public NodeId focused(){return focus.focused();}
    public boolean isClosed(){return closed||closing;}
    public PresentationFrame frame(){refresh();return frame;}

    public List<OverlaySpec> overlaySnapshot(){
        checkThread();return overlays.snapshot().stream().map(o->overlayEntries.get(o.id()).spec).toList();
    }
    public void showOverlay(OverlaySpec spec){
        checkOpen();checkNotLayingOut();Objects.requireNonNull(spec);indexAll(root,spec);
        if(!refreshing)refresh();
        if(spec.anchor()!=null&&(frame==null||!frame.paintOrder().contains(spec.anchor())))throw new IllegalArgumentException("Unavailable overlay anchor: "+spec.anchor());
        // Finish user callbacks before OverlayHost.open pushes its new focus layer.
        if(spec.modal())cancelGestures();if(isClosed())return;indexAll(root,spec);
        var entry=new OverlayEntry(spec);overlayEntries.put(spec.id(),entry);
        try{
            var members=new LinkedHashMap<NodeId,UiElement>();index(spec.content(),members);
            overlays.open(new OverlayHost.Overlay(spec.id(),spec.modal(),List.copyOf(members.keySet())));structure++;
        }catch(RuntimeException|Error failure){overlayEntries.remove(spec.id());try{entry.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
        try{
            if(!refreshing&&!isClosed()){
                refresh();
                // An overlay root can itself be focusable. Opening it should leave an
                // oversized dialog at its top; revealing applies to a nested control
                // selected by Tab (or by focus traversal), not the content root.
                var top=overlays.snapshot().get(overlays.snapshot().size()-1);
                if(spec.modal()&&!spec.content().id().equals(focus.focused())&&top.modal())revealFocus();
            }
        }
        catch(RuntimeException|Error failure){
            // Failed content must not retain a hidden modal or its native owner.
            while(!isClosed()&&overlayEntries.get(spec.id())==entry){var cleanup=closeLayer();if(cleanup!=null&&cleanup!=failure)failure.addSuppressed(cleanup);}
            if(!isClosed())try{refresh();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    public boolean closeTopOverlay(){
        checkOpen();checkNotLayingOut();if(overlays.snapshot().isEmpty())return false;
        Throwable failure=closeLayer();
        if(!refreshing&&!isClosed())failure=attempt(failure,this::refresh);
        rethrow(failure);return true;
    }
    private Throwable closeLayer(){return closeLayer(true);}
    private Throwable closeLayer(boolean cancelFocused){
        var top=overlays.snapshot().get(overlays.snapshot().size()-1);var entry=overlayEntries.remove(top.id());structure++;
        var old=focus.focused()==null?null:elements.get(focus.focused());var captured=input.captureOwner();
        // closeTop removes the old focus layer before cancel callbacks may open a replacement.
        Throwable failure=attempt(null,overlays::closeTop);
        if(cancelFocused&&old!=null&&!old.id().equals(captured)&&contains(entry.spec.content(),old.id())&&old.pointerTarget()!=null)failure=attempt(failure,old.pointerTarget()::cancel);
        failure=attempt(failure,entry.spec.content()::overlayClosed);
        failure=attempt(failure,entry::close);return failure;
    }
    public void refresh(){
        checkOpen();if(refreshing)throw new IllegalStateException("Cannot recursively refresh a view");refreshing=true;
        try{refreshFrame();}finally{refreshing=false;}
        notifyFocus();
    }
    private void refreshFrame(){
        // Closing an invalid anchor may synchronously open its replacement from cancel().
        // Rebuild the layer list after that callback rather than publishing a half-old frame.
        while(true){
            long start=structure;
            var indexed=indexAll(root,null);elements=Collections.unmodifiableMap(indexed);
            var liveScroll=new HashSet<NodeId>();
            var baseTree=new LayoutTree(root.layout(host));
            var base=present(baseTree,solve(()->engine.layout(baseTree,viewport)),new PresentationFrame.Offset(0,0),liveScroll);
            var layers=new ArrayList<PresentationFrame>();layers.add(base);
            PresentationFrame combined=base;
            boolean retry=false;
            for(var overlay:overlays.snapshot()){
                var entry=overlayEntries.get(overlay.id());var spec=entry.spec;
                var content=spec.content().layout(host);
                if(content.style().hidden()||(spec.anchor()!=null&&!anchorAvailable(spec.anchor(),combined))){
                    // Descendants above the invalid layer cannot outlive it.
                    Throwable failure=null;
                    var ids=overlays.snapshot().stream().map(OverlayHost.Overlay::id).toList();
                    int index=ids.indexOf(spec.id());
                    for(int i=ids.size()-1;i>=index;i--)failure=merge(failure,closeLayer());
                    rethrow(failure);retry=true;break;
                }
                var anchor=spec.anchor()==null?null:combined.box(spec.anchor()).border();
                float width=preferredWidth(spec,anchor);var s=content.style();
                if(s.width().unit()==Length.Unit.POINTS)width=Math.min(width,s.width().value());
                width=Math.min(width,s.maxWidth());
                // Preserve content height and layout semantics, but don't let flex shrink eat overflow.
                s=s.flex(s.grow(),0,s.basis()).limits(Math.min(s.minWidth(),width),s.minHeight(),Math.min(s.maxWidth(),width),s.maxHeight());
                content=new LayoutNode(content.id(),s,content.children(),content.measure(),content.measureRevision());
                var containerStyle=LayoutStyle.column().size(Length.points(width),Length.auto()).behavior(false,false,true,true);
                var tree=new LayoutTree(new LayoutNode(entry.container,containerStyle,List.of(content),null,0));
                var localViewport=new Viewport(width,viewport.height(),viewport.pixelsPerUnit());
                var raw=PresentationFrame.constrainRootHeight(solve(()->entry.engine.layoutIntrinsicHeight(tree,localViewport)),entry.container,viewport.height());
                var size=raw.box(entry.container).border();
                var offset=placement(spec,anchor,size);
                layers.add(present(tree,raw,offset,liveScroll));combined=PresentationFrame.compose(layers);
            }
            if(retry||structure!=start)continue;
            scroll.keySet().retainAll(liveScroll);frame=combined;
            var targets=new LinkedHashMap<NodeId,PointerTarget>();
            for(var id:frame.paintOrder()){
                var element=elements.get(id);if(element==null)continue;
                element.geometry(frame.box(id),host);var target=element.pointerTarget();
                if(target!=null&&target.enabled())targets.put(id,target);
            }
            var stack=overlays.snapshot();var active=new LinkedHashSet<>(targets.keySet());
            int modal=-1;for(int i=0;i<stack.size();i++)if(stack.get(i).modal())modal=i;
            if(modal>=0){active.clear();for(int i=modal;i<stack.size();i++)collectIds(overlayEntries.get(stack.get(i).id()).spec.content(),active);active.retainAll(targets.keySet());}
            allowed=Set.copyOf(active);
            var dispatchTargets=new LinkedHashMap<NodeId,PointerTarget>();targets.forEach((id,target)->{if(allowed.contains(id))dispatchTargets.put(id,target);});
            input.setHitTester((x,y)->frame.topAt(dispatchTargets.keySet(),x,y));input.setTargets(dispatchTargets);
            if(isClosed())return;
            if(structure!=start)continue;
            focus.setOrder(List.copyOf(targets.keySet()));
            var modalOrders=new ArrayList<List<NodeId>>();
            for(int i=0;i<stack.size();i++)if(stack.get(i).modal()){
                var ids=new LinkedHashSet<NodeId>();
                for(int j=i;j<stack.size();j++)collectIds(overlayEntries.get(stack.get(j).id()).spec.content(),ids);
                modalOrders.add(targets.keySet().stream().filter(ids::contains).toList());
            }
            focus.updateModalOrders(modalOrders);return;
        }
    }
    private PresentationFrame present(LayoutTree tree,LayoutSnapshot raw,PresentationFrame.Offset translation,Set<NodeId> live){
        var visible=Set.copyOf(raw.paintOrder());
        for(var id:raw.paintOrder()){
            var node=tree.node(id);if(!node.style().scroll())continue;
            live.add(id);var box=raw.box(id);float extent=0;
            for(var child:node.children())if(visible.contains(child.id()))extent=Math.max(extent,raw.box(child.id()).border().bottom()-box.content().y());
            scroll.computeIfAbsent(id,k->new ScrollView()).setExtent(extent,box.content().height());
        }
        var offsets=new HashMap<NodeId,PresentationFrame.Offset>();scroll.forEach((id,state)->offsets.put(id,new PresentationFrame.Offset(0,state.offset())));
        return PresentationFrame.create(tree,raw,viewport,offsets,translation);
    }
    private boolean anchorAvailable(NodeId id,PresentationFrame in){
        if(!in.paintOrder().contains(id))return false;
        var box=in.box(id);var visible=box.border().intersect(box.clip());
        var element=elements.get(id);return visible.width()>0&&visible.height()>0&&element!=null&&(element.pointerTarget()==null||element.pointerTarget().enabled());
    }
    private float preferredWidth(OverlaySpec spec,Rect anchor){
        var spacing=host.theme().spacing();
        float preferred=switch(spec.size()){
            case COMPACT->spacing.controlHeight()*6;case STANDARD->spacing.controlHeight()*9;case WIDE->spacing.controlHeight()*14;
            case ANCHOR->anchor==null?spacing.controlHeight()*9:anchor.width();
        };
        return Math.min(viewport.width(),preferred);
    }
    private PresentationFrame.Offset placement(OverlaySpec spec,Rect anchor,Rect size){
        float x=(viewport.width()-size.width())/2,y=(viewport.height()-size.height())/2;
        if(spec.placement()!=OverlayPlacement.CENTER){
            x=anchor.x();boolean above=spec.placement()==OverlayPlacement.ABOVE_ANCHOR;
            float belowY=anchor.bottom(),aboveY=anchor.y()-size.height();
            if(above)y=aboveY>=0?aboveY:belowY;
            else y=belowY+size.height()<=viewport.height()?belowY:aboveY;
        }
        return new PresentationFrame.Offset(Math.max(0,Math.min(viewport.width()-size.width(),x)),Math.max(0,Math.min(viewport.height()-size.height(),y)));
    }
    public void paint(Canvas canvas,FrameTime time){
        refresh();
        // A focus callback during refresh may synchronously close this view.
        if(isClosed())return;
        int baseline=canvas.getSaveCount();var displayed=frame;var paintedElements=elements;long start=structure;
        try{
            for(var id:displayed.paintOrder()){
                var element=paintedElements.get(id);if(element==null)continue;var box=displayed.box(id);int save=canvas.getSaveCount();canvas.save();
                try{var clip=box.clip();canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(clip.x(),clip.y(),clip.width(),clip.height()));element.paint(canvas,box,host,time);}
                finally{canvas.restoreToCount(save);}
                if(isClosed()||structure!=start)break;
            }
        }finally{canvas.restoreToCount(baseline);}
    }
    public InputResult pointer(PointerEvent event){
        refresh();if(suspended)return InputResult.CONSUMED;
        var result=input.dispatch(event);if(isClosed())return result;
        if(event.type()==PointerEvent.Type.PRESS&&input.captureOwner()!=null&&allowed.contains(input.captureOwner()))focus.focus(input.captureOwner(),false);
        refresh();return overlays.blocking()?InputResult.CONSUMED:result;
    }
    public InputResult key(KeyEvent event){
        refresh();if(suspended)return InputResult.CONSUMED;
        boolean wasBlocking=overlays.blocking();
        if(event.pressed()&&event.key()==KeyEvent.Key.TAB){focus.tab(event.shift());notifyFocus();if(!isClosed())revealFocus();return InputResult.CONSUMED;}
        if(event.pressed()&&event.key()==KeyEvent.Key.ESCAPE){
            if(closeTopOverlay())return InputResult.CONSUMED;
            if(input.captureOwner()!=null){input.cancel();return InputResult.CONSUMED;}
        }
        var element=focusedElement();keyGesture=element==null?null:element.pointerTarget();InputResult result;
        try{result=element==null?InputResult.PASS:element.key(event);}catch(RuntimeException|Error failure){abortGesture(failure);throw failure;}finally{keyGesture=null;}
        if(!isClosed())refresh();return wasBlocking||(!isClosed()&&overlays.blocking())?InputResult.CONSUMED:result;
    }
    public InputResult textInput(String text){
        Objects.requireNonNull(text);refresh();if(suspended)return InputResult.CONSUMED;
        var element=focusedElement();keyGesture=element==null?null:element.pointerTarget();
        try{if(element!=null)element.textInput(text);}catch(RuntimeException|Error failure){abortGesture(failure);throw failure;}finally{keyGesture=null;}
        if(!isClosed())refresh();return element!=null||(!isClosed()&&overlays.blocking())?InputResult.CONSUMED:InputResult.PASS;
    }
    public float wheel(float x,float y,float distance){
        refresh();if(suspended)return 0;
        var candidates=new HashSet<>(scroll.keySet());
        if(overlays.blocking()){
            var stack=overlays.snapshot();int modal=0;for(int i=0;i<stack.size();i++)if(stack.get(i).modal())modal=i;
            var ids=new HashSet<NodeId>();for(int i=modal;i<stack.size();i++){var entry=overlayEntries.get(stack.get(i).id());ids.add(entry.container);collectIds(entry.spec.content(),ids);}candidates.retainAll(ids);
        }
        NodeId id=frame.topAt(candidates,x,y);float remaining=distance;
        while(id!=null){remaining=scroll.get(id).scroll(remaining);id=frame.box(id).scrollOwner();}
        refresh();return overlays.blocking()?0:remaining;
    }
    private void revealFocus(){
        if(focus.focused()==null)return;var owner=frame.box(focus.focused()).scrollOwner();
        while(owner!=null&&!isClosed()&&focus.focused()!=null){var box=frame.box(focus.focused());var state=scroll.get(owner);if(state==null)return;var parent=frame.box(owner).content();var next=frame.box(owner).scrollOwner();state.reveal(box.border().y()-parent.y()+state.offset(),box.border().bottom()-parent.y()+state.offset(),parent.height());refresh();owner=next;}
    }
    private UiElement focusedElement(){var id=focus.focused();return id==null||!allowed.contains(id)?null:elements.get(id);}
    private void notifyFocus(){
        if(notifyingFocus)return;notifyingFocus=true;Throwable failure=null;
        try{
            UiElement next=isClosed()?null:focusedElement();
            while(notifiedFocus!=next){
                var old=notifiedFocus;notifiedFocus=null;if(old!=null)failure=attempt(failure,()->old.focusChanged(false));
                next=isClosed()?null:focusedElement();notifiedFocus=next;var gained=next;
                if(gained!=null)failure=attempt(failure,()->gained.focusChanged(true));
                next=isClosed()?null:focusedElement();
            }
        }finally{notifyingFocus=false;}
        rethrow(failure);
    }
    private Map<NodeId,UiElement> indexAll(UiElement base,OverlaySpec additional){
        var next=new LinkedHashMap<NodeId,UiElement>();index(base,next);
        for(var overlay:overlays.snapshot())index(overlayEntries.get(overlay.id()).spec.content(),next);
        if(additional!=null)index(additional.content(),next);
        for(var entry:overlayEntries.values())if(next.containsKey(entry.container))throw new IllegalArgumentException("Component ID collides with overlay viewport: "+entry.container);
        return next;
    }
    private static void index(UiElement element,Map<NodeId,UiElement> elements){if(elements.putIfAbsent(element.id(),element)!=null)throw new IllegalArgumentException("Duplicate component ID: "+element.id());for(var child:element.children())index(child,elements);}
    private static void collectIds(UiElement element,Set<NodeId> ids){ids.add(element.id());for(var child:element.children())collectIds(child,ids);}
    private static boolean contains(UiElement element,NodeId id){if(element.id().equals(id))return true;for(var child:element.children())if(contains(child,id))return true;return false;}
    private void checkOpen(){checkThread();if(isClosed())throw new IllegalStateException("UiView is closed");}
    private void checkNotLayingOut(){if(layingOut)throw new IllegalStateException("Cannot mutate overlays during native layout");}
    private LayoutSnapshot solve(Supplier<LayoutSnapshot> operation){layingOut=true;try{return operation.get();}finally{layingOut=false;}}
    private void checkThread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("UiView belongs to "+owner.getName());}
    private void abortGesture(Throwable failure){try{cancelGestures();}catch(RuntimeException|Error cleanup){if(failure!=cleanup)failure.addSuppressed(cleanup);}}
    private void cancelGestures(){var key=keyGesture;keyGesture=null;var captured=input.captureOwner();var capturedElement=captured==null?null:elements.get(captured);Throwable failure=attempt(null,input::cancel);if(key!=null&&(capturedElement==null||key!=capturedElement.pointerTarget()))failure=attempt(failure,key::cancel);rethrow(failure);}
    private static Throwable merge(Throwable prior,Throwable failure){if(failure==null)return prior;if(prior==null)return failure;if(prior!=failure)prior.addSuppressed(failure);return prior;}
    private static Throwable attempt(Throwable prior,Runnable action){try{action.run();}catch(RuntimeException|Error failure){return merge(prior,failure);}return prior;}
    private static void rethrow(Throwable failure){if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;}
    @Override public void close(){
        checkThread();if(isClosed())return;if(refreshing)throw new IllegalStateException("Cannot close during view layout");closing=true;
        var captured=input.captureOwner();
        var capturedElement=captured==null?null:elements.get(captured);
        var capturedTarget=capturedElement==null?null:capturedElement.pointerTarget();
        Throwable failure=attempt(null,this::cancelGestures);
        var old=notifiedFocus;
        if(old!=null&&old.pointerTarget()!=null&&old.pointerTarget()!=capturedTarget)failure=attempt(failure,old.pointerTarget()::cancel);
        // close() already cancelled the single captured/focused gesture above. Layer
        // teardown must release its stack and native owners without notifying that
        // same target a second time.
        while(!overlays.snapshot().isEmpty())failure=merge(failure,closeLayer(false));
        for(var entry:overlayEntries.values())failure=attempt(failure,entry::close);overlayEntries.clear();
        failure=attempt(failure,this::notifyFocus);failure=attempt(failure,engine::close);
        closed=true;closing=false;focus.clear();scroll.clear();elements=Map.of();allowed=Set.of();frame=null;rethrow(failure);
    }
}
