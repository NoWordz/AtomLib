package com.atom.lib.controls;
import com.atom.lib.layout.*;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.text.TextService;
import io.github.humbleui.skija.Canvas;
public interface ControlSkin {
    default LayoutNode actionNode(NodeId id,ActionModel model,LayoutStyle outer,TextService text,float iconSize){throw new UnsupportedOperationException("This skin does not support image slots");}
    default void paintAction(Canvas canvas,PresentationFrame frame,NodeId id,ActionModel model,ControlVisual visual,TextService text,ControlIcon icon,float iconSize){throw new UnsupportedOperationException("This skin does not support image slots");}
    default LayoutNode actionNode(NodeId id,ActionModel m,LayoutStyle outer,TextService text){throw new UnsupportedOperationException("This skin does not support action controls");}
    default void paintAction(Canvas c,PresentationFrame f,NodeId id,ActionModel m,ControlVisual v,TextService text){throw new UnsupportedOperationException("This skin does not support action controls");}
    default LayoutNode sliderNode(NodeId id,SliderModel model,String value,LayoutStyle style,TextService text,ControlPresentation p){return sliderNode(id,model,value,style,text);}
    default void paintSlider(Canvas c,PresentationFrame f,NodeId id,SliderModel m,float value,String label,ControlVisual v,TextService text,ControlPresentation p){paintSlider(c,f,id,m,value,label,v,text);}
    default LayoutNode toggleNode(NodeId id,ToggleModel model,LayoutStyle style,TextService text,ControlPresentation p){return toggleNode(id,model,style,text);}
    default void paintToggle(Canvas c,PresentationFrame f,NodeId id,ToggleModel m,ControlVisual v,TextService text,ControlPresentation p){paintToggle(c,f,id,m,v,text);}
    default LayoutNode modalTitle(NodeId id,String title,TextService text){throw new UnsupportedOperationException("This skin does not support composable modals");}
    default void paintModalSurface(Canvas canvas,PresentationFrame frame,NodeId id,String title,TextService text){throw new UnsupportedOperationException("This skin does not support composable modals");}
    ControlMotion motion();
    LayoutStyle modalDialogStyle(Viewport viewport);
    LayoutNode sliderNode(NodeId id,SliderModel model,String valueText,LayoutStyle outerStyle,TextService text);
    void paintSlider(Canvas canvas,PresentationFrame frame,NodeId id,SliderModel model,float previewValue,String valueText,ControlVisual visual,TextService text);
    LayoutNode toggleNode(NodeId id,ToggleModel model,LayoutStyle outerStyle,TextService text);
    void paintToggle(Canvas canvas,PresentationFrame frame,NodeId id,ToggleModel model,ControlVisual visual,TextService text);
    LayoutNode dialogNode(NodeId id,ConfirmationModel model,LayoutStyle outerStyle,TextService text);
    void paintDialog(Canvas canvas,PresentationFrame frame,NodeId id,ConfirmationModel model,NodeId focusedAction,ControlVisual visual,TextService text);
}
