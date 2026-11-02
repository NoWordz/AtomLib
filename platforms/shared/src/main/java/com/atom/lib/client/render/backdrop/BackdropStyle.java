// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render.backdrop;
public record BackdropStyle(float blurRadius,float cornerRadius,float opacity,int fallbackColor){
    public BackdropStyle{if(!Float.isFinite(blurRadius)||blurRadius<0||!Float.isFinite(cornerRadius)||cornerRadius<0||!Float.isFinite(opacity)||opacity<0||opacity>1)throw new IllegalArgumentException("Invalid backdrop style");}
}
