// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.host;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.controls.*;
import com.atom.lib.design.atomchat.*;
import com.atom.lib.font.FontRegistry;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import com.atom.lib.motion.FrameTime;
import com.atom.lib.text.*;
import io.github.humbleui.skija.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A host-provided skin opts one button into Paragraph while retaining normal input routing. */
class ParagraphButtonHostTest {
    @Test void sameParagraphMeasuresAndPaintsClickableButtonLabel() {
        try (var runtime=AtomLibRuntime.open("paragraph-button"); var fonts=new FontRegistry(runtime);
             var oldText=new TextService(fonts); var paragraphs=new ParagraphTextService(fonts);
             var yoga=new YogaLayoutEngine(); var surface=Surface.makeRasterN32Premul(240,100)) {
            fonts.registerDefaults();
            var request=new TextRequest("确认 e\u0301", FontRegistry.DEFAULT_REGULAR, 18, 0xff000000);
            var constraints=new Constraints(Constraints.Axis.atMost(180), Constraints.Axis.undefined());
            try (var label=paragraphs.layout(request,constraints)) {
                var context=new ControlContext(oldText,new ParagraphButtonSkin(label));
                int[] count={0};
                var button=new ActionButton(new NodeId("button"),context,new ActionModel(request.text(),true,ActionModel.Role.NORMAL),()->count[0]++);
                try (var session=new UiSession(List.of(button))) {
                    var viewport=new Viewport(240,100,1); session.prepare(viewport);
                    var tree=new LayoutTree(new LayoutNode(new NodeId("root"),LayoutStyle.column(),
                            List.of(button.node(LayoutStyle.column().size(Length.points(200),Length.points(60)))),null,0));
                    var frame=PresentationFrame.create(tree,yoga.layout(tree,viewport),viewport,Map.of(),new PresentationFrame.Offset(0,0));
                    var textBox=frame.box(new NodeId("button/text")).content();
                    assertEquals(label.size().height(),textBox.height(),2);
                    assertEquals(label.size().width(),textBox.width(),2);
                    surface.getCanvas().clear(0xffffffff);
                    session.paint(surface.getCanvas(),frame,viewport,new FrameTime(1,1,0),false);
                    try (var image=surface.makeImageSnapshot(); var bitmap=new Bitmap()) {
                        bitmap.allocN32Pixels(240,100); assertTrue(image.readPixels(bitmap));
                        int ink=0;
                        for(int y=0;y<100;y++) for(int x=0;x<240;x++) if(bitmap.getColor(x,y)!=0xffffffff){
                            ink++;
                            assertTrue(x>=Math.floor(textBox.x()) && x<Math.ceil(textBox.right())
                                    && y>=Math.floor(textBox.y()) && y<Math.ceil(textBox.bottom()), "ink outside measured label at "+x+","+y);
                        }
                        assertTrue(ink>0);
                    }
                    assertFalse(label.selection(3,5).isEmpty());
                    var box=frame.box(new NodeId("button")).border(); float x=box.x()+box.width()/2,y=box.y()+box.height()/2;
                    assertEquals(InputResult.CONSUMED,session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0)));
                    assertEquals(InputResult.CONSUMED,session.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0)));
                    assertEquals(1,count[0]);
                }
            }
        }
    }

    private static final class ParagraphButtonSkin implements ControlSkin {
        private final ParagraphTextLayout label;
        private final AtomChatControlSkin delegate=new AtomChatControlSkin(AtomChatControlStyle.defaults());
        ParagraphButtonSkin(ParagraphTextLayout label){this.label=label;}
        public ControlMotion motion(){return delegate.motion();}
        public LayoutStyle modalDialogStyle(Viewport v){return delegate.modalDialogStyle(v);}
        public LayoutNode actionNode(NodeId id,ActionModel model,LayoutStyle outer,TextService unused){
            var child=LayoutNode.leaf(id.value()+"/text",LayoutStyle.column(),constraints->label.size(),0);
            return new LayoutNode(id,outer.align(LayoutStyle.Align.CENTER).justify(LayoutStyle.Justify.CENTER),List.of(child),null,0);
        }
        public void paintAction(Canvas canvas,PresentationFrame frame,NodeId id,ActionModel model,ControlVisual visual,TextService unused){
            var box=frame.box(new NodeId(id.value()+"/text")).content(); label.draw(canvas,box.x(),box.y());
        }
        public LayoutNode sliderNode(NodeId id,SliderModel m,String s,LayoutStyle l,TextService t){return delegate.sliderNode(id,m,s,l,t);}
        public void paintSlider(Canvas c,PresentationFrame f,NodeId id,SliderModel m,float x,String s,ControlVisual v,TextService t){delegate.paintSlider(c,f,id,m,x,s,v,t);}
        public LayoutNode toggleNode(NodeId id,ToggleModel m,LayoutStyle l,TextService t){return delegate.toggleNode(id,m,l,t);}
        public void paintToggle(Canvas c,PresentationFrame f,NodeId id,ToggleModel m,ControlVisual v,TextService t){delegate.paintToggle(c,f,id,m,v,t);}
        public LayoutNode dialogNode(NodeId id,ConfirmationModel m,LayoutStyle l,TextService t){return delegate.dialogNode(id,m,l,t);}
        public void paintDialog(Canvas c,PresentationFrame f,NodeId id,ConfirmationModel m,NodeId focused,ControlVisual v,TextService t){delegate.paintDialog(c,f,id,m,focused,v,t);}
    }
}
