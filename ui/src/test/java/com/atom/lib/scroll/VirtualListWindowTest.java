package com.atom.lib.scroll;

import com.atom.lib.geometry.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.t8.T8Host;
import com.atom.lib.view.HostContext;
import io.github.humbleui.skija.Canvas;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class VirtualListWindowTest {
    record Item(int key,float height,long revision) {}
    static final class Rows implements VirtualList.RowAdapter<Integer,Item> {
        int measures,paints;
        public Integer key(Item item){return item.key();}
        public long revision(Item item){return item.revision();}
        public float measure(Item item,float width,HostContext host){measures++;return item.height();}
        public void paint(Item item,Canvas canvas,VirtualList.RowBox<Integer> box,HostContext host,FrameTime time){paints++;}
    }
    private static LayoutBox viewport(){var r=new Rect(0,0,100,100);return new LayoutBox(r,r,r,r,null,null);}
    private static List<Item> items(){return IntStream.range(0,1000).mapToObj(i->new Item(i,20,0)).toList();}
    @Test void onlyTheVisibleWindowAndBufferAreMeasuredAndStaticFramesReuseTheCache(){
        try(var h=new T8Host()){
            var rows=new Rows();var list=new VirtualList<>("list",items(),rows,20,100,2);
            list.layout(h.host);list.geometry(viewport(),h.host);assertTrue(rows.measures<=9);assertTrue(list.visibleRows().size()<=9);
            int measured=rows.measures;list.geometry(viewport(),h.host);assertEquals(measured,rows.measures);
            list.scroll(10000);assertEquals(500,list.visibleRows().stream().filter(r->r.border().y()<=0&&r.border().bottom()>0).findFirst().orElseThrow().key());
            assertTrue(rows.measures<=18);assertTrue(list.children().isEmpty());
        }
    }
    @Test void prependAndRemeasuringABufferRowKeepTheSameAnchorOnScreen(){
        try(var h=new T8Host()){
            var rows=new Rows();var source=new ArrayList<>(items());var list=new VirtualList<>("list",source,rows,20,100,2);
            list.layout(h.host);list.geometry(viewport(),h.host);list.scroll(200);
            var before=list.visibleRows().stream().filter(r->r.key()==10).findFirst().orElseThrow();assertEquals(0,before.border().y());
            source.add(0,new Item(-1,20,0));list.setItems(source);list.geometry(viewport(),h.host);assertEquals(220,list.offset());
            source.set(10,new Item(9,50,1));list.setItems(source);list.geometry(viewport(),h.host);
            assertEquals(250,list.offset());assertEquals(0,list.visibleRows().stream().filter(r->r.key()==10).findFirst().orElseThrow().border().y());
        }
    }
    @Test void duplicateKeysAreRejectedAndFocusRevealAndResidualWheelAreBounded(){
        try(var h=new T8Host()){
            var list=new VirtualList<>("list",items(),new Rows(),20,100,1);list.layout(h.host);list.geometry(viewport(),h.host);
            assertThrows(IllegalArgumentException.class,()->list.setItems(List.of(new Item(1,20,0),new Item(1,30,0))));
            list.revealKey(999);assertEquals(19900,list.offset());assertEquals(50,list.scroll(50));
            list.setItems(List.of());assertEquals(0,list.offset());assertTrue(list.visibleRows().isEmpty());
        }
    }
    @Test void stableKeysActivateTheCurrentItemAndRemovalCancelsTheCapturedRow(){
        try(var h=new T8Host()){
            var activated=new ArrayList<Item>();int[] cancels={0};
            var adapter=new VirtualList.RowAdapter<Integer,Item>(){
                public Integer key(Item item){return item.key();}public long revision(Item item){return item.revision();}
                public float measure(Item item,float width,HostContext host){return 20;}
                public void paint(Item item,Canvas c,VirtualList.RowBox<Integer> box,HostContext host,FrameTime time){}
                public void activate(Item item){activated.add(item);}public void cancel(Item item){cancels[0]++;}
            };
            var list=new VirtualList<>("list",List.of(new Item(1,20,0)),adapter,20,100,1);list.geometry(viewport(),h.host);
            list.press(new com.atom.lib.input.PointerEvent(com.atom.lib.input.PointerEvent.Type.PRESS,5,5,0));
            var changed=new Item(1,20,1);list.setItems(List.of(changed));list.release(new com.atom.lib.input.PointerEvent(com.atom.lib.input.PointerEvent.Type.RELEASE,5,5,0));assertSame(changed,activated.get(0));
            list.press(new com.atom.lib.input.PointerEvent(com.atom.lib.input.PointerEvent.Type.PRESS,5,5,0));list.setItems(List.of());list.release(new com.atom.lib.input.PointerEvent(com.atom.lib.input.PointerEvent.Type.RELEASE,5,5,0));assertEquals(1,activated.size());assertEquals(1,cancels[0]);
        }
    }
    @Test void fontGenerationAndWidthInvalidateOnlyTheNewVisibleMeasurementWindow(){
        try(var h=new T8Host()){
            var rows=new Rows();var list=new VirtualList<>("list",items(),rows,20,100,1);list.geometry(viewport(),h.host);int initial=rows.measures;
            h.fonts.reload();list.geometry(viewport(),h.host);assertEquals(initial*2,rows.measures);
            var r=new Rect(0,0,80,100);list.geometry(new LayoutBox(r,r,r,r,null,null),h.host);assertEquals(initial*3,rows.measures);
        }
    }
}
