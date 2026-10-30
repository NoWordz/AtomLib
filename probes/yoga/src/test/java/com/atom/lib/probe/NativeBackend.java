package com.atom.lib.probe;

import static org.lwjgl.util.yoga.Yoga.*;
import org.lwjgl.util.yoga.YGMeasureFunc;

final class NativeBackend extends ProbeBackend {
    final long config = YGConfigNew();
    @Override Node create(){return new NNode();}
    @Override void layout(Node root,float w,float h,float scale){YGConfigSetPointScaleFactor(config,scale);YGNodeCalculateLayout(((NNode)root).node,w,h,YGDirectionLTR);}
    static String mode(int m){return m==YGMeasureModeExactly?"EXACTLY":m==YGMeasureModeAtMost?"AT_MOST":"UNDEFINED";}
    final class NNode extends Node {
        final long node = YGNodeNewWithConfig(config);
        YGMeasureFunc callback;
        @Override Node row(){YGNodeStyleSetFlexDirection(node,YGFlexDirectionRow);return this;}
        @Override Node wrap(){YGNodeStyleSetFlexWrap(node,YGWrapWrap);return this;}
        @Override Node padding(float v){YGNodeStyleSetPadding(node,YGEdgeAll,v);return this;}
        @Override Node width(float v){YGNodeStyleSetWidth(node,v);return this;}
        @Override Node height(float v){YGNodeStyleSetHeight(node,v);return this;}
        @Override Node percentWidth(float v){YGNodeStyleSetWidthPercent(node,v);return this;}
        @Override Node grow(float v){YGNodeStyleSetFlexGrow(node,v);return this;}
        @Override Node shrink(float v){YGNodeStyleSetFlexShrink(node,v);return this;}
        @Override Node basis(float v){YGNodeStyleSetFlexBasis(node,v);return this;}
        @Override Node minWidth(float v){YGNodeStyleSetMinWidth(node,v);return this;}
        @Override Node maxWidth(float v){YGNodeStyleSetMaxWidth(node,v);return this;}
        @Override Node marginRight(float v){YGNodeStyleSetMargin(node,YGEdgeRight,v);return this;}
        @Override Node hidden(){YGNodeStyleSetDisplay(node,YGDisplayNone);return this;}
        @Override Node measure(Measure m){callback=YGMeasureFunc.create((n,w,wm,h,hm,out)->{var size=m.measure(w,mode(wm),h,mode(hm));out.set(size[0],size[1]);});YGNodeSetMeasureFunc(node,callback);return this;}
        @Override void dirty(){YGNodeMarkDirty(node);}
        @Override Node add(Node n){YGNodeInsertChild(node,((NNode)n).node,YGNodeGetChildCount(node));return this;}
        @Override void remove(Node n){YGNodeRemoveChild(node,((NNode)n).node);}
        @Override float x(){return YGNodeLayoutGetLeft(node);}
        @Override float y(){return YGNodeLayoutGetTop(node);}
        @Override float width(){return YGNodeLayoutGetWidth(node);}
        @Override float height(){return YGNodeLayoutGetHeight(node);}
    }
    @Override public void close(){
        if(closed)return;
        for(var n:nodes)YGNodeRemoveAllChildren(((NNode)n).node);
        for(var n:nodes){var nn=(NNode)n;YGNodeSetMeasureFunc(nn.node,(YGMeasureFunc)null);YGNodeFree(nn.node);if(nn.callback!=null)nn.callback.free();}
        nodes.clear();YGConfigFree(config);closed=true;
    }
}
