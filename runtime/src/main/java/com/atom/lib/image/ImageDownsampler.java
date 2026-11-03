// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.image;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Objects;

/** Host-owned, thread-confined raster preprocessing cache for borrowed images. */
public final class ImageDownsampler implements AutoCloseable {
    private record Entry(WeakReference<Image> source, int width, int height, Image image) { }
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final Thread owner=Thread.currentThread();
    private final long pixelBudget;
    private final int entryBudget;
    private final float minReduction;
    private long pixels;
    private boolean closed;

    public ImageDownsampler(long pixelBudget, int entryBudget, float minReduction) {
        if (pixelBudget < 1 || entryBudget < 1 || !Float.isFinite(minReduction) || minReduction <= 1)
            throw new IllegalArgumentException("Invalid preprocessing budget or reduction");
        this.pixelBudget=pixelBudget; this.entryBudget=entryBudget; this.minReduction=minReduction;
    }

    /** Returns a borrowed image. Draw it before another cache operation; never close it. */
    public Image select(Image source, int targetWidth, int targetHeight, boolean pixelArt) {
        requireOpen(); Objects.requireNonNull(source);
        if(source.isClosed() || targetWidth < 1 || targetHeight < 1) throw new IllegalArgumentException("Invalid image or target size");
        prune();
        int width=source.getWidth(),height=source.getHeight();
        long area=(long)targetWidth*targetHeight;
        if(pixelArt || area>pixelBudget || width/(double)targetWidth<minReduction || height/(double)targetHeight<minReduction) return source;
        int firstWidth=Math.max(targetWidth,width/2+width%2),firstHeight=Math.max(targetHeight,height/2+height%2);
        long scratchLimit=pixelBudget>Long.MAX_VALUE/4?Long.MAX_VALUE:pixelBudget*4;
        if((long)firstWidth*firstHeight>scratchLimit)return source;
        // Never silently rasterize a GPU-only or deferred image without its context.
        if(source.peekPixels()==null)return source;
        for(int i=0;i<entries.size();i++) {
            var entry=entries.get(i);
            if(entry.source().get()==source && entry.width()==targetWidth && entry.height()==targetHeight) {
                entries.remove(i);entries.add(entry);return entry.image();
            }
        }
        Image scaled=source;
        try {
            while(width>targetWidth || height>targetHeight) {
                int nextWidth=Math.max(targetWidth,width/2+width%2),nextHeight=Math.max(targetHeight,height/2+height%2);
                Image next;
                try(var surface=Surface.makeRasterN32Premul(nextWidth,nextHeight);var paint=new Paint()) {
                    surface.getCanvas().drawImageRect(scaled,Rect.makeXYWH(0,0,width,height),Rect.makeXYWH(0,0,nextWidth,nextHeight),SamplingMode.LINEAR,paint,false);
                    next=surface.makeImageSnapshot();
                }
                if(scaled!=source)scaled.close();
                scaled=next;width=nextWidth;height=nextHeight;
            }
        } catch(RuntimeException|Error failure) {
            if(scaled!=source)scaled.close();
            throw failure;
        }
        while(entries.size()>=entryBudget || pixels>pixelBudget-area) remove(0);
        entries.add(new Entry(new WeakReference<>(source),targetWidth,targetHeight,scaled));pixels+=area;
        return scaled;
    }
    public int size() { requireOpen();prune();return entries.size(); }
    public long pixels() { requireOpen();prune();return pixels; }
    public void clear() { requireOpen();while(!entries.isEmpty())remove(entries.size()-1); }
    @Override public void close() { checkThread();if(!closed){clear();closed=true;} }
    private void prune() {
        for(int i=entries.size()-1;i>=0;i--){var e=entries.get(i);var source=e.source().get();if(source==null||source.isClosed()||e.image().isClosed())remove(i);}
    }
    private void remove(int index) {var e=entries.remove(index);pixels-=(long)e.width()*e.height();if(!e.image().isClosed())e.image().close();}
    private void requireOpen() {checkThread();if(closed)throw new IllegalStateException("Image preprocessing cache closed");}
    private void checkThread() {if(Thread.currentThread()!=owner)throw new IllegalStateException("Image preprocessing cache belongs to another thread");}
}
