// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;
import com.atom.lib.layout.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SettingsRowTest {
    @Test void confirmationIconSharesTitleBand(){
        try(var f=new ControlFixture()){
            f.show(560);f.session.open(new ConfirmationDialog(new NodeId("dialog"),f.context,new ConfirmationModel("确认重置？","仅用于内存验收。","取消","确认")),v->{});f.show(560);
            var icon=f.frame.box(new NodeId("dialog/icon")).border();var title=f.frame.box(new NodeId("dialog/title")).border();
            assertTrue(icon.right()<=title.x());assertTrue(icon.y()<title.bottom()&&title.y()<icon.bottom());
            assertEquals(462.5f,f.frame.box(new NodeId("dialog")).border().width(),.51);
        }
    }
    @Test void rowSliderPlacesValueBesideTitleAndDoesNotPaintOwnCard(){
        try(var f=new ControlFixture()){
            f.slider.presentation(ControlPresentation.SETTINGS_ROW);f.show(480);
            var title=f.frame.box(new NodeId("width/title")).border();var value=f.frame.box(new NodeId("width/value")).border();
            assertEquals(title.y(),value.y(),1,"Value must share the title band");
            assertTrue(value.x()>title.x());
        }
    }
    @Test void rowTogglePlacesSwitchToTheRightOfDescription(){
        try(var f=new ControlFixture()){
            var toggle=new Toggle(new NodeId("half"),f.context,new ToggleModel("半屏模式","手机与半屏分别记忆宽度。",false,true),v->{});
            toggle.presentation(ControlPresentation.SETTINGS_ROW);f.session.setControls(List.of(toggle));f.show(480);
            var body=f.frame.box(new NodeId("half/body")).border();var sw=f.frame.box(new NodeId("half/switch")).border();
            assertTrue(sw.x()>=body.right(),"Switch belongs to right column");assertTrue(sw.y()<body.bottom());
        }
    }
}
