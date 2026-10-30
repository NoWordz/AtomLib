// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.interaction;

public enum HudAnchor {
    TOP_LEFT(0,0),TOP_CENTER(.5f,0),TOP_RIGHT(1,0),
    CENTER_LEFT(0,.5f),CENTER(.5f,.5f),CENTER_RIGHT(1,.5f),
    BOTTOM_LEFT(0,1),BOTTOM_CENTER(.5f,1),BOTTOM_RIGHT(1,1);
    private final float x,y;
    HudAnchor(float x,float y){this.x=x;this.y=y;}
    public float x(){return x;}
    public float y(){return y;}
}
