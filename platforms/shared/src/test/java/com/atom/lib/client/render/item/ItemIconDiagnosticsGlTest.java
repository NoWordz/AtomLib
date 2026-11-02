package com.atom.lib.client.render.item;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.client.render.HiddenGlFixture;
import com.atom.lib.client.render.GlStateScope;
import com.atom.lib.client.render.SkijaRenderBridge;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL33C.*;

/** Independent synthetic measurement; no Minecraft classes or game assets are used. */
class ItemIconDiagnosticsGlTest extends HiddenGlFixture {
    private static void pattern(List<ItemIconAtlas.Tile> tiles, int h) {
        for (var tile : tiles) {
            glEnable(GL_SCISSOR_TEST);
            int x=tile.x(), y=h-tile.y()-tile.size(), s=tile.size();
            glScissor(x,y+s/2,s,s-s/2); glClearColor(1,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
            glScissor(x,y,s,s/2); glClearColor(0,0,1,1); glClear(GL_COLOR_BUFFER_BIT);
            glScissor(x,y,1,s); glClearColor(0,0,0,0); glClear(GL_COLOR_BUFFER_BIT);
            glScissor(x+1,y,1,s); glClearColor(.5f,0,0,.5f); glClear(GL_COLOR_BUFFER_BIT);
        }
    }
    @Test void compareQueriesAndCpuIntervals() throws Exception {
        var out = new StringBuilder("round,mode,kind,iteration,allocation_count,build_ns,prepare_ns,pass_ns,close_ns,total_ns,query_read_ns,gpu_ns\n");
        var info = new StringBuilder("scope=synthetic GL patterns, no Minecraft\n");
        info.append("vendor=").append(glGetString(GL_VENDOR)).append("\nrenderer=").append(glGetString(GL_RENDERER)).append("\nversion=").append(glGetString(GL_VERSION)).append('\n');
        try (var runtime=AtomLibRuntime.open("item-diagnostics"); var bridge=new SkijaRenderBridge(runtime)) {
            for(int round=1;round<=3;round++) for(String mode:List.of("uninstrumented","none","delayed","sync","empty")) {
                try(var atlas=new ItemIconAtlas(bridge)) {
                    int[] queries=new int[49];
                    if(mode.equals("delayed")||mode.equals("sync")) for(int i=0;i<queries.length;i++) queries[i]=glGenQueries();
                    try {
                        for(int i=0;i<49;i++) {
                            long t=System.nanoTime(); var entries=new ArrayList<ItemIconAtlas.Entry>();
                            for(int j=0;j<16;j++) entries.add(new ItemIconAtlas.Entry("pattern/"+j,32,false));
                            long built=System.nanoTime(); final long[] passNs={0}; final int q=queries[i];
                            long start=System.nanoTime();
                            var frame=atlas.prepare(entries,i+1,(tiles,w,h)->{
                                long p=mode.equals("uninstrumented")?0:System.nanoTime();
                                if(q!=0)glBeginQuery(GL_TIME_ELAPSED,q);
                                try { if(!mode.equals("empty")) pattern(tiles,h); }
                                finally { if(q!=0)glEndQuery(GL_TIME_ELAPSED); }
                                if(p!=0)passNs[0]=System.nanoTime()-p;
                            });
                            long prepared=System.nanoTime(); frame.close(); long closed=System.nanoTime();
                            long queryStart=System.nanoTime(), gpu=-1;
                            if(mode.equals("sync"))gpu=glGetQueryObjectui64(q,GL_QUERY_RESULT);
                            if(mode.equals("delayed") && i>=4) {
                                int prior=queries[i-4];
                                if(glGetQueryObjecti(prior,GL_QUERY_RESULT_AVAILABLE)!=0)gpu=glGetQueryObjectui64(prior,GL_QUERY_RESULT);
                            }
                            long queryEnd=System.nanoTime();
                            String kind=i==0?"cold":i<17?"warmup":"warm";
                            out.append(round).append(',').append(mode).append(',').append(kind).append(',').append(i).append(',').append(atlas.allocationCount()).append(',').append(built-t).append(',').append(prepared-start).append(',').append(passNs[0]).append(',').append(closed-prepared).append(',').append(closed-start).append(',').append(queryEnd-queryStart).append(',').append(gpu).append('\n');
                        }
                    } finally { for(int q:queries) if(q!=0)glDeleteQueries(q); }
                }
            }
        }
        var dir=Path.of(System.getProperty("atomlib.itemIconEvidence")); Files.createDirectories(dir);
        Files.writeString(dir.resolve("diagnostic-samples.csv"),out); Files.writeString(dir.resolve("diagnostic-environment.txt"),info);
    }

    @Test void measureIsolatedStateAndContextOperations() throws Exception {
        var out=new StringBuilder("round,iteration,capture_ns,restore_ns,context_empty_ns,context_flush_ns\n");
        try(var runtime=AtomLibRuntime.open("isolated-state-diagnostics");var bridge=new SkijaRenderBridge(runtime)) {
            for(int r=1;r<=3;r++) for(int i=0;i<48;i++) {
                long a=System.nanoTime();var state=GlStateScope.capture();long b=System.nanoTime();state.close();long c=System.nanoTime();
                bridge.withContext(context->null);long d=System.nanoTime();
                bridge.withContext(context->{context.flushAndSubmit(false);return null;});long e=System.nanoTime();
                out.append(r).append(',').append(i).append(',').append(b-a).append(',').append(c-b).append(',').append(d-c).append(',').append(e-d).append('\n');
            }
        }
        var dir=Path.of(System.getProperty("atomlib.itemIconEvidence"));Files.createDirectories(dir);Files.writeString(dir.resolve("isolated-state-samples.csv"),out);
    }
}
