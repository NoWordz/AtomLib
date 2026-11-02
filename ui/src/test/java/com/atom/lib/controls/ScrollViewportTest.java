// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;
import com.atom.lib.layout.*;
import com.atom.lib.geometry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ScrollViewportTest {
    @Test void naturalOverflowCanBeScrolledToLastRowAndClamped(){
        try(var f=new ControlFixture()){
            var rows=new ArrayList<LayoutNode>();for(int i=0;i<10;i++)rows.add(LayoutNode.leaf("row"+i,LayoutStyle.column().size(Length.auto(),Length.points(80)).flex(0,0,Length.auto()),null,0));
            var tree=new LayoutTree(new LayoutNode(new NodeId("content"),LayoutStyle.column().size(Length.points(300),Length.auto()).behavior(false,false,true,true),rows,null,0));
            var vp=new Viewport(480,860,1);var natural=f.engine.layoutIntrinsicHeight(tree,vp);var scroll=new ScrollViewport();var bounds=new Rect(40,150,300,200);
            var before=scroll.present(tree,natural,vp,bounds,1);scroll.publish(before);assertNull(before.topAt(Set.of(new NodeId("row9")),60,800));
            assertTrue(scroll.scrollBy(10000));assertEquals(600,scroll.offset());var after=scroll.present(tree,natural,vp,bounds,1);scroll.publish(after);
            assertEquals(new NodeId("row9"),after.topAt(Set.of(new NodeId("row9")),60,330));assertFalse(scroll.scrollBy(1));
            assertTrue(scroll.ensureVisible(after.box(new NodeId("row0")).border()));assertEquals(0,scroll.offset());
        }
    }
}
