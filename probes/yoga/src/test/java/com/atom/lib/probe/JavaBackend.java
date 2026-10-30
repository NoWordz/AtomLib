package com.atom.lib.probe;

import org.appliedenergistics.yoga.*;
import org.appliedenergistics.yoga.config.*;
import org.appliedenergistics.yoga.style.*;

final class JavaBackend extends ProbeBackend {
    private final MutableYogaConfig config = YogaConfig.create();
    @Override Node create() { return new JNode(); }
    @Override void layout(Node root,float w,float h,float scale) { config.setPointScaleFactor(scale); ((JNode)root).node.calculateLayout(w,h,YogaDirection.LTR); }
    final class JNode extends Node {
        final YogaNode node = new YogaNode(config);
        @Override Node row(){node.setFlexDirection(YogaFlexDirection.ROW);return this;}
        @Override Node wrap(){node.setWrap(YogaWrap.WRAP);return this;}
        @Override Node padding(float v){node.setPadding(YogaEdge.ALL,StyleLength.points(v));return this;}
        @Override Node width(float v){node.setWidth(StyleSizeLength.points(v));return this;}
        @Override Node height(float v){node.setHeight(StyleSizeLength.points(v));return this;}
        @Override Node percentWidth(float v){node.setWidth(StyleSizeLength.percent(v));return this;}
        @Override Node grow(float v){node.setFlexGrow(v);return this;}
        @Override Node shrink(float v){node.setFlexShrink(v);return this;}
        @Override Node basis(float v){node.setFlexBasis(v);return this;}
        @Override Node minWidth(float v){node.setMinWidth(StyleSizeLength.points(v));return this;}
        @Override Node maxWidth(float v){node.setMaxWidth(StyleSizeLength.points(v));return this;}
        @Override Node marginRight(float v){node.setMargin(YogaEdge.RIGHT,StyleLength.points(v));return this;}
        @Override Node hidden(){node.setDisplay(YogaDisplay.NONE);return this;}
        @Override Node measure(Measure m){node.setMeasureFunction((n,w,wm,h,hm)->{var size=m.measure(w,wm.name(),h,hm.name());return new YogaSize(size[0],size[1]);});return this;}
        @Override void dirty(){node.markDirtyAndPropagate();}
        @Override Node add(Node n){node.addChildAt(((JNode)n).node,node.getChildCount());return this;}
        @Override void remove(Node n){node.removeChildAndInvalidate(((JNode)n).node);}
        @Override float x(){return node.getLayoutX();}
        @Override float y(){return node.getLayoutY();}
        @Override float width(){return node.getLayoutWidth();}
        @Override float height(){return node.getLayoutHeight();}
    }
    @Override public void close() {
        if(closed)return;
        for(var n:nodes){var node=((JNode)n).node;node.clearChildren();node.setMeasureFunction(null);node.setContext(null);}
        nodes.clear();closed=true;
    }
}
