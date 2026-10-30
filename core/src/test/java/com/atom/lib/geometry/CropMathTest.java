// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.geometry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CropMathTest {
    @Test void centreZoomKeepsSourcePointAndCoverClampHidesNoEdges() {
        float base=CropMath.baseScale(400,200,100,100);
        assertEquals(.5f,base);
        float[] zoom=CropMath.zoomAtCentre(400,200,1,50,50,50,50,70,50,2,base);
        assertArrayEquals(new float[]{2,90,50},zoom,.001f);
        assertEquals(-20,(50-zoom[1])/zoom[0],.001);
        float[] covered=CropMath.clampCover(400,200,.5f,50,50,50,50,10000,-10000);
        assertArrayEquals(new float[]{100,50},covered,.001f);
        assertArrayEquals(new float[]{0,0,200,200},CropMath.visibleRect(400,200,.5f,50,50,50,50,covered[0],covered[1]),.001f);
    }
}
