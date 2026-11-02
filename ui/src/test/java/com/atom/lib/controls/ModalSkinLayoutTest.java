package com.atom.lib.controls;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.FontRegistry;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import com.atom.lib.text.TextService;
import io.github.humbleui.skija.Canvas;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Non-AC layout-only skin; no design.atomchat imports or delegation. */
class ModalSkinLayoutTest {
    @Test void skinOwnsDialogWidthAndInsetsWhileSessionOnlyCentersIt(){
        try(var runtime=AtomLibRuntime.open("non-ac-modal");var fonts=new FontRegistry(runtime);var text=new TextService(fonts);var engine=new YogaLayoutEngine()){
            for(float width:new float[]{219,303})for(float viewportWidth:new float[]{280,480})try(var session=new UiSession(List.of())){
                var viewport=new Viewport(viewportWidth,320,1);var skin=new IndependentSkin(width,31);
                var dialog=new ConfirmationDialog(new NodeId("independent"),new ControlContext(text,skin),new ConfirmationModel("Title","Body","Cancel","OK"));
                session.open(dialog,r->{});var tree=new LayoutTree(session.modalNode(viewport));var frame=engine.layout(tree,viewport);var box=frame.box(dialog.id()).border();
                float expected=Math.min(width,viewportWidth-62);
                assertEquals(expected,box.width(),.01);assertEquals((viewportWidth-expected)/2,box.x(),1.01);assertEquals(95,box.height(),.01);assertEquals((320-95)/2f,box.y(),1.01);
            }
        }
    }
    private static final class IndependentSkin implements ControlSkin {
        private final float width,inset;
        IndependentSkin(float width,float inset){this.width=width;this.inset=inset;}
        @Override public LayoutStyle modalDialogStyle(Viewport viewport){return LayoutStyle.column().size(Length.points(Math.min(width,viewport.width()-2*inset)),Length.points(95)).flex(0,0,Length.auto());}
        public ControlMotion motion(){return new ControlMotion(.05,.05,.05);}
        public LayoutNode dialogNode(NodeId id,ConfirmationModel m,LayoutStyle style,TextService text){return new LayoutNode(id,style,List.of(),null,0);}
        public LayoutNode sliderNode(NodeId id,SliderModel m,String value,LayoutStyle style,TextService text){throw new AssertionError("Layout test has no slider");}
        public LayoutNode toggleNode(NodeId id,ToggleModel m,LayoutStyle style,TextService text){throw new AssertionError("Layout test has no toggle");}
        public void paintSlider(Canvas c,PresentationFrame f,NodeId id,SliderModel m,float p,String value,ControlVisual v,TextService t){throw new AssertionError("Layout-only test");}
        public void paintToggle(Canvas c,PresentationFrame f,NodeId id,ToggleModel m,ControlVisual v,TextService t){throw new AssertionError("Layout-only test");}
        public void paintDialog(Canvas c,PresentationFrame f,NodeId id,ConfirmationModel m,NodeId focused,ControlVisual v,TextService t){throw new AssertionError("Layout-only test");}
    }
}
