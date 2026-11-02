package com.atom.lib.controls;
import com.atom.lib.geometry.*;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import java.util.*;
/** Owns modal measurements and scrolling; all returned boxes are the painted boxes. */
final class ModalLayout implements AutoCloseable {
    private final YogaLayoutEngine surfaceEngine=new YogaLayoutEngine(), headerEngine=new YogaLayoutEngine(), bodyEngine=new YogaLayoutEngine(), footerEngine=new YogaLayoutEngine();
    final ScrollViewport scroll=new ScrollViewport();
    private NodeId id(ModalDialog d,String suffix){return new NodeId(d.id.value()+"/"+suffix);}
    private LayoutTree tree(NodeId id,float width,List<LayoutNode> children,boolean scroll){
        return new LayoutTree(new LayoutNode(id,LayoutStyle.column().size(Length.points(width),Length.auto()).spacing(0,8).behavior(false,false,scroll,scroll),children,null,0));
    }
    PresentationFrame layout(ModalDialog d,Viewport v){
        float margin=Math.min(d.options.margin(),Math.min(v.width(),v.height())/4);
        float w=Math.max(1,Math.min(d.options.preferredWidth(),v.width()-2*margin));
        float padding=Math.min(16,w/8), inner=Math.max(1,w-2*padding);
        var title=d.context.skin().modalTitle(id(d,"title"),d.options.title(),d.context.text());
        var head=new ArrayList<LayoutNode>();head.add(title);if(d.navigation!=null)head.add(d.navigation.node(LayoutStyle.row().size(Length.auto(),Length.auto())));
        var body=d.body==null?List.<LayoutNode>of():List.of(d.body);
        var buttons=d.actions.stream().map(b->b.node(LayoutStyle.column().flex(1,1,Length.points(0)))).toList();
        var footer=buttons.isEmpty()?List.<LayoutNode>of():List.of(new LayoutNode(id(d,"actions"),LayoutStyle.row().spacing(0,8),buttons,null,0));
        var hv=new Viewport(inner,Math.max(1,v.height()),v.pixelsPerUnit());
        var ht=tree(id(d,"header"),inner,head,false);var hs=headerEngine.layoutIntrinsicHeight(ht,hv);float hh=hs.box(ht.root().id()).border().height();
        var ft=tree(id(d,"footer"),inner,footer,false);var fs=footerEngine.layoutIntrinsicHeight(ft,hv);float fh=fs.box(ft.root().id()).border().height();
        var bt=tree(id(d,"scroll"),inner,body,true);var bs=bodyEngine.layoutIntrinsicHeight(bt,hv);float bh=bs.box(bt.root().id()).border().height();
        float available=Math.max(0,v.height()-2*margin-2*padding), gap=8;
        boolean allScroll=hh+fh+gap*2>available;
        float h=Math.min(v.height()-2*margin,2*padding+hh+fh+bh+gap*2);h=Math.max(1,h);
        float x=(v.width()-w)/2,y=(v.height()-h)/2;
        var surface=new LayoutTree(LayoutNode.leaf(d.id.value(),LayoutStyle.column().size(Length.points(w),Length.points(h)),null,0));
        var sf=PresentationFrame.create(surface,surfaceEngine.layout(surface,v),v,Map.of(),new PresentationFrame.Offset(x,y));
        if(allScroll){
            var children=new ArrayList<LayoutNode>();children.addAll(head);children.addAll(body);children.addAll(footer);
            bt=tree(id(d,"scroll"),inner,children,true);bs=bodyEngine.layoutIntrinsicHeight(bt,hv);
            return PresentationFrame.compose(List.of(sf,scroll.present(bt,bs,v,new Rect(x+padding,y+padding,inner,Math.max(0,h-2*padding)),1)));
        }
        var hf=PresentationFrame.create(ht,hs,v,Map.of(),new PresentationFrame.Offset(x+padding,y+padding));
        float top=y+padding+hh+gap, footerY=y+h-padding-fh;
        var bf=scroll.present(bt,bs,v,new Rect(x+padding,top,inner,Math.max(0,footerY-gap-top)),1);
        var ff=PresentationFrame.create(ft,fs,v,Map.of(),new PresentationFrame.Offset(x+padding,footerY));
        return PresentationFrame.compose(List.of(sf,hf,bf,ff));
    }
    public void close(){surfaceEngine.close();headerEngine.close();bodyEngine.close();footerEngine.close();}
}
