// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.devtools.inspect;

import com.atom.lib.component.Label;
import com.atom.lib.geometry.Rect;
import com.atom.lib.layout.*;
import com.atom.lib.view.*;
import io.github.humbleui.skija.*;
import java.util.*;

/** Opt-in, local-only geometry and input diagnostics. It never captures page content. */
public final class LayoutInspector {
    public record Entry(NodeId id,Rect border,Rect hit,Rect clip,NodeId parent,NodeId scrollOwner,
                        boolean focused,boolean captured){}
    public record Metrics(long revision,int nodes,long resourceGeneration,float layoutMillis,
                          float paintMillis,NodeId focused,NodeId captured,List<Entry> entries){}
    private boolean enabled;
    private Metrics latest;
    public void setEnabled(boolean enabled){this.enabled=enabled;if(!enabled)latest=null;}
    public boolean enabled(){return enabled;}
    public Metrics latest(){return latest;}

    public Metrics inspect(UiView view,NodeId selected,long resourceGeneration,long layoutNanos,long paintNanos){
        if(!enabled)return null;
        Objects.requireNonNull(view);Objects.requireNonNull(selected);
        var frame=view.frame();var entries=new ArrayList<Entry>();
        NodeId focused=view.focused(),captured=view.captureOwner();
        for(var id:frame.paintOrder()){
            var b=frame.box(id);entries.add(new Entry(id,b.border(),b.hit(),b.clip(),b.parent(),b.scrollOwner(),
                    id.equals(focused),id.equals(captured)));
        }
        latest=new Metrics(frame.revision(),entries.size(),resourceGeneration,
                layoutNanos/1_000_000f,paintNanos/1_000_000f,focused,captured,List.copyOf(entries));
        return latest;
    }

    /** Draws the selected hit and clip boxes plus a compact text report. */
    public void draw(Canvas canvas,HostContext host,NodeId selected){
        if(!enabled||latest==null)return;
        Entry entry=latest.entries.stream().filter(e->e.id().equals(selected)).findFirst().orElse(null);
        if(entry==null)return;
        int saved=canvas.getSaveCount();canvas.save();
        try(var paint=new Paint().setAntiAlias(true).setMode(PaintMode.STROKE).setStrokeWidth(1)){
            canvas.drawRect(xywh(entry.clip()),paint.setColor(0x99ffb020));
            canvas.drawRect(xywh(entry.hit()),paint.setColor(0xcc4aa3ff));
            var box=entry.border();
            String report=String.format(Locale.ROOT,"rev %d | nodes %d | layout %.2fms | paint %.2fms\n%s hit %.1f,%.1f %.1fx%.1f | focus %s | capture %s",
                    latest.revision(),latest.nodes(),latest.layoutMillis(),latest.paintMillis(),entry.id().value(),
                    box.x(),box.y(),box.width(),box.height(),latest.focused(),latest.captured());
            var layout=host.text().layout(new com.atom.lib.text.TextRequest(report,host.bodyFont(),
                    host.theme().type().caption(),host.theme().palette().text(),6,true),
                    new Constraints(Constraints.Axis.atMost(Math.max(160,box.width()-16)),Constraints.Axis.undefined()));
            try(Paint bg=new Paint().setColor(0xe010141b)){canvas.drawRect(xywh(new Rect(box.x(),box.y(),
                    Math.min(box.width(),layout.size().width()+16),layout.size().height()+16)),bg);}
            host.text().draw(canvas,layout,box.x()+8,box.y()+8);
        }finally{canvas.restoreToCount(saved);}
    }
    private static io.github.humbleui.types.Rect xywh(Rect r){return io.github.humbleui.types.Rect.makeXYWH(r.x(),r.y(),r.width(),r.height());}
}
