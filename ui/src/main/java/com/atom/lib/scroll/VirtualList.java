// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.scroll;

import com.atom.lib.component.UiElement;
import com.atom.lib.geometry.Rect;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.style.ThemeSnapshot;
import com.atom.lib.text.TextRequest;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;
import java.util.*;
import java.util.function.*;

/** One Yoga leaf; only visible rows plus a buffer are measured/painted. Offset is owned here. */
public final class VirtualList<K,T> implements UiElement, PointerTarget {
    public record RowBox<K>(K key,int index,Rect border,Rect clip) {}
    public interface RowAdapter<K,T> {
        K key(T item);
        default long revision(T item) { return 0; }
        default Object measurementKey(T item) { return revision(item); }
        float measure(T item,float width,HostContext host);
        void paint(T item,Canvas canvas,RowBox<K> box,HostContext host,FrameTime time);
        default void activate(T item) {}
        default void cancel(T item) {}
    }
    private record Cached(Object revision,float width,long fontGeneration,ThemeSnapshot theme,float height) {}
    private record Anchor<K>(K key,int index,float within) {}
    private final NodeId id;
    private final RowAdapter<K,T> adapter;
    private final float estimate,preferredHeight;
    private final int buffer;
    private List<T> items=List.of();
    private List<K> keys=List.of();
    private Map<K,Integer> indices=Map.of();
    private final Map<K,Cached> cache=new HashMap<>();
    private float[] heights=new float[0],fenwick=new float[1];
    private float offset;
    private Rect bounds=new Rect(0,0,0,0),clip=bounds;
    private HostContext host;
    private List<RowBox<K>> window=List.of();
    private K captured,selected;
    private T capturedItem;
    private final LeafMeasure measure=this::measureNode;
    private MeasuredSize measureNode(Constraints c){return new MeasuredSize(c.width().constrain(320),c.height().constrain(preferredHeight),0);}

    public VirtualList(String id,List<? extends T> items,RowAdapter<K,T> adapter,float estimatedRowHeight,float viewportHeight,int bufferRows) {
        this.id=new NodeId(id);this.adapter=Objects.requireNonNull(adapter);
        if(!Float.isFinite(estimatedRowHeight)||estimatedRowHeight<=0||!Float.isFinite(viewportHeight)||viewportHeight<0||bufferRows<0)throw new IllegalArgumentException("Invalid virtual list dimensions");
        estimate=estimatedRowHeight;preferredHeight=viewportHeight;buffer=bufferRows;setItems(items);
    }
    @Override public NodeId id(){return id;}
    @Override public PointerTarget pointerTarget(){return this;}
    public float offset(){return offset;}
    public float totalHeight(){return prefix(items.size());}
    public List<RowBox<K>> visibleRows(){return window;}
    public K selectedKey(){return selected;}
    public void setItems(List<? extends T> next) {
        var copy=List.<T>copyOf(next);var nextIndices=new LinkedHashMap<K,Integer>();var nextKeys=new ArrayList<K>();
        for(int i=0;i<copy.size();i++){K key=Objects.requireNonNull(adapter.key(copy.get(i)),"Null row key");if(nextIndices.putIfAbsent(key,i)!=null)throw new IllegalArgumentException("Duplicate row key: "+key);nextKeys.add(key);}
        var anchor=anchor();items=copy;keys=List.copyOf(nextKeys);indices=Collections.unmodifiableMap(nextIndices);cache.keySet().retainAll(indices.keySet());
        heights=new float[items.size()];fenwick=new float[items.size()+1];
        for(int i=0;i<items.size();i++){var cached=cache.get(keys.get(i));heights[i]=cached==null?estimate:cached.height();add(i,heights[i]);}
        restore(anchor);if(selected!=null&&!indices.containsKey(selected))selected=null;
        window=List.of();
        if(captured!=null){if(!indices.containsKey(captured))cancel();else capturedItem=items.get(indices.get(captured));}
        refreshWindow();
    }
    /** Returns distance not consumed by this viewport, for an outer scroll owner. */
    public float scroll(float distance) {
        if(!Float.isFinite(distance))throw new IllegalArgumentException("Invalid scroll distance");
        float before=offset;offset=clamp(offset+distance);refreshWindow();return distance-(offset-before);
    }
    /** Component-level wheel hook; no outer offset is applied a second time. */
    public float wheel(float x,float y,float distance){
        if(!Float.isFinite(distance))throw new IllegalArgumentException("Invalid scroll distance");
        return bounds.contains(x,y)&&clip.contains(x,y)?scroll(distance):distance;
    }
    public void revealKey(K key) {
        Integer index=indices.get(key);if(index==null)throw new IllegalArgumentException("Unknown row key: "+key);
        float top=prefix(index),bottom=top+heights[index];
        if(top<offset)offset=top;else if(bottom>offset+bounds.height())offset=bottom-bounds.height();
        offset=clamp(offset);refreshWindow();
    }
    @Override public LayoutNode layout(HostContext host){return LayoutNode.leaf(id.value(),LayoutStyle.column().size(Length.auto(),Length.points(preferredHeight)).flex(0,0,Length.auto()),measure,0);}
    @Override public void geometry(LayoutBox box,HostContext host){this.host=Objects.requireNonNull(host);bounds=box.content();clip=box.clip().intersect(bounds);offset=clamp(offset);refreshWindow();}
    private Anchor<K> anchor(){if(items.isEmpty())return null;int at=indexAt(offset);return new Anchor<>(keys.get(at),at,offset-prefix(at));}
    private void restore(Anchor<K> anchor){if(items.isEmpty()){offset=0;return;}if(anchor==null){offset=clamp(offset);return;}int at=indices.getOrDefault(anchor.key(),Math.min(anchor.index(),items.size()-1));offset=clamp(prefix(at)+Math.min(anchor.within(),heights[at]));}
    private void refreshWindow() {
        if(host==null||items.isEmpty()||bounds.width()==0||bounds.height()==0){window=List.of();return;}
        var anchor=anchor();long generation=host.fonts().generation();ThemeSnapshot theme=host.theme();
        boolean measured;
        do {
            measured=false;int first=Math.max(0,indexAt(offset)-buffer),end=Math.min(items.size(),indexAt(Math.nextDown(offset+bounds.height()))+1+buffer);
            for(int i=first;i<end;i++){
                T item=items.get(i);K key=keys.get(i);Object revision=adapter.measurementKey(item);Cached old=cache.get(key);
                if(old!=null&&Objects.equals(old.revision(),revision)&&old.width()==bounds.width()&&old.fontGeneration()==generation&&old.theme().equals(theme))continue;
                float height=adapter.measure(item,bounds.width(),host);
                if(!Float.isFinite(height)||height<=0)throw new IllegalArgumentException("Row height must be positive and finite: "+key);
                cache.put(key,new Cached(revision,bounds.width(),generation,theme,height));float delta=height-heights[i];heights[i]=height;add(i,delta);measured=true;
            }
            restore(anchor);
        }while(measured);
        int first=Math.max(0,indexAt(offset)-buffer),end=Math.min(items.size(),indexAt(Math.nextDown(offset+bounds.height()))+1+buffer);
        var visible=new ArrayList<RowBox<K>>();
        for(int i=first;i<end;i++){var border=new Rect(bounds.x(),bounds.y()+prefix(i)-offset,bounds.width(),heights[i]);visible.add(new RowBox<>(keys.get(i),i,border,clip.intersect(border)));}
        window=List.copyOf(visible);
    }
    private float clamp(float value){return Math.max(0,Math.min(Math.max(0,totalHeight()-bounds.height()),value));}
    private void add(int index,float delta){for(int i=index+1;i<fenwick.length;i+=i&-i){fenwick[i]+=delta;if(!Float.isFinite(fenwick[i]))throw new IllegalArgumentException("Virtual list extent overflow");}}
    private float prefix(int end){float sum=0;for(int i=end;i>0;i-=i&-i)sum+=fenwick[i];return sum;}
    private int indexAt(float y){int index=0;float sum=0;for(int step=Integer.highestOneBit(items.size());step!=0;step>>=1){int next=index+step;if(next<fenwick.length&&sum+fenwick[next]<=y){index=next;sum+=fenwick[next];}}return Math.min(index,Math.max(0,items.size()-1));}
    @Override public void paint(Canvas canvas,LayoutBox box,HostContext host,FrameTime time){
        for(var row:window){Integer index=indices.get(row.key());if(index==null||row.clip().width()==0||row.clip().height()==0)continue;
            int save=canvas.getSaveCount();canvas.save();try{var c=row.clip();canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(c.x(),c.y(),c.width(),c.height()));adapter.paint(items.get(index),canvas,row,host,time);}finally{canvas.restoreToCount(save);}
        }
    }
    private RowBox<K> hit(float x,float y){for(var row:window)if(row.border().contains(x,y)&&row.clip().contains(x,y))return row;return null;}
    @Override public boolean press(PointerEvent event){if(event.button()!=0)return false;var row=hit(event.x(),event.y());if(row==null)return false;captured=selected=row.key();capturedItem=items.get(indices.get(captured));return true;}
    @Override public void drag(PointerEvent event){}
    @Override public void release(PointerEvent event){if(captured==null)return;K key=captured;T item=capturedItem;captured=null;capturedItem=null;var row=hit(event.x(),event.y());if(row!=null&&row.key().equals(key)&&indices.containsKey(key))adapter.activate(item);else adapter.cancel(item);}
    @Override public void cancel(){if(captured==null)return;T item=capturedItem;captured=null;capturedItem=null;adapter.cancel(item);}
    @Override public InputResult key(KeyEvent event){
        if(!event.pressed()||items.isEmpty())return InputResult.PASS;
        int at=selected==null?-1:indices.getOrDefault(selected,-1);
        switch(event.key()){
            case UP->at=Math.max(0,at-1);case DOWN->at=Math.min(items.size()-1,at+1);case HOME->at=0;case END->at=items.size()-1;
            case ENTER,SPACE->{if(at>=0)adapter.activate(items.get(at));return InputResult.CONSUMED;}
            default->{return InputResult.PASS;}
        }
        selected=keys.get(at);revealKey(selected);return InputResult.CONSUMED;
    }
    /** Usable text rows without caller-supplied coordinates. Custom adapters can draw richer rows. */
    public static <K,T> RowAdapter<K,T> textRows(Function<T,K> key,Function<T,String> text,Consumer<T> activate){
        Objects.requireNonNull(key);Objects.requireNonNull(text);Objects.requireNonNull(activate);
        return new RowAdapter<>(){
            public K key(T item){return key.apply(item);}
            public Object measurementKey(T item){return text.apply(item);}
            private com.atom.lib.text.TextLayout layout(T item,float width,HostContext host){float padding=host.theme().spacing().small();return host.text().layout(new TextRequest(text.apply(item),host.bodyFont(),host.theme().type().body(),host.theme().palette().text()),new Constraints(Constraints.Axis.atMost(Math.max(0,width-padding*2)),Constraints.Axis.undefined()));}
            public float measure(T item,float width,HostContext host){return Math.max(host.theme().spacing().controlHeight(),layout(item,width,host).size().height()+host.theme().spacing().small()*2);}
            public void paint(T item,Canvas canvas,RowBox<K> row,HostContext host,FrameTime time){float pad=host.theme().spacing().small();host.text().draw(canvas,layout(item,row.border().width(),host),row.border().x()+pad,row.border().y()+pad);}
            public void activate(T item){activate.accept(item);}
        };
    }
}
