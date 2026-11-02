// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.design.atomchat;
/** Business configuration consumed by the shell; no global current page or clock. */
public record SettingsPageStyle(float width,boolean halfScreen,float uiScale,float contentScale,float opacity,float cardTint){
    public SettingsPageStyle{for(float v:new float[]{width,uiScale,contentScale})if(!Float.isFinite(v)||v<=0)throw new IllegalArgumentException("Invalid page size");for(float v:new float[]{opacity,cardTint})if(!Float.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("Invalid opacity");}
    public AtomChatControlStyle controls(){return new AtomChatControlStyle(20,halfScreen,0xff4a90e2,0xe8b7bdca,cardTint,-1,(Math.round(opacity*255)<<24)|0x16191f,0xfff3f4f6);}
}
