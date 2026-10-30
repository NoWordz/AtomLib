package com.atom.lib.component;
import com.atom.lib.geometry.Rect;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
final class UiPaint {
    private UiPaint(){}
    static void rounded(Canvas canvas,Rect box,float radius,int color){if(box.width()==0||box.height()==0)return;try(var paint=new Paint().setColor(color)){canvas.drawRRect(RRect.makeXYWH(box.x(),box.y(),box.width(),box.height(),Math.min(radius,Math.min(box.width(),box.height())/2)),paint);}}
}
