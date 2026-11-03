// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.bench;

import com.atom.lib.AtomLibRuntime;
import com.atom.lib.font.FontRegistry;
import com.atom.lib.image.BackdropBlur;
import com.atom.lib.layout.Constraints;
import com.atom.lib.text.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Isolated, synthetic raster workload; no game or chat classes. */
public final class GenericListBenchmark {
    public record Sample(long frameNanos,long cpuNanos,long allocatedBytes,long intervalNanos,int reused,int visible,int offset) { }
    public static List<Sample> run(int count,boolean scroll,boolean cold,boolean blur,int warmup,int samples) {
        if(count<13||warmup<0||samples<1)throw new IllegalArgumentException("Invalid workload");
        var bean=ManagementFactory.getThreadMXBean();
        var allocation=bean instanceof com.sun.management.ThreadMXBean a && a.isThreadAllocatedMemorySupported() ? (com.sun.management.ThreadMXBean)bean : null;
        if(allocation!=null&&!allocation.isThreadAllocatedMemoryEnabled())allocation.setThreadAllocatedMemoryEnabled(true);
        if(bean.isCurrentThreadCpuTimeSupported()&&!bean.isThreadCpuTimeEnabled())bean.setThreadCpuTimeEnabled(true);
        try(var runtime=AtomLibRuntime.open("synthetic-list-benchmark");var fonts=new FontRegistry(runtime);
            var surface=Surface.makeRasterN32Premul(480,320);var backdrop=Surface.makeRasterN32Premul(480,320)) {
            fonts.registerDefaults();
            var requests=new ArrayList<TextRequest>();
            for(int i=0;i<count;i++)requests.add(new TextRequest("Synthetic row "+i+" / example content",FontRegistry.DEFAULT_REGULAR,14,0xffeeeeee));
            var constraints=new Constraints(Constraints.Axis.atMost(430),Constraints.Axis.undefined());
            backdrop.getCanvas().clear(0xff617389);
            try(var bg=backdrop.makeImageSnapshot()) {
                var results=new ArrayList<Sample>();var previous=new TextLayout[count];
                TextService text=new TextService(fonts,count+1,1000000);
                long previousStart=0;
                try {
                    for(int frame=0;frame<warmup+samples;frame++) {
                        long start=System.nanoTime(),cpuStart=bean.isThreadCpuTimeEnabled()?bean.getCurrentThreadCpuTime():-1;
                        long allocStart=allocation==null?-1:allocation.getThreadAllocatedBytes(Thread.currentThread().getId());
                        if(cold){text.close();text=new TextService(fonts,count+1,1000000);}
                        int offset=scroll?frame%(count-12):0,reused=0,visible=0;
                        surface.getCanvas().clear(0xff20242c);
                        if(blur)BackdropBlur.draw(surface.getCanvas(),bg,Rect.makeXYWH(0,0,480,320),4,0xff20242c);
                        for(int i=0;i<count;i++) {
                            var layout=text.layout(requests.get(i),constraints);
                            if(layout==previous[i])reused++;
                            previous[i]=layout;
                            if(i>=offset&&i<offset+12){text.draw(surface.getCanvas(),layout,12,8+(i-offset)*25);visible++;}
                        }
                        long elapsed=System.nanoTime()-start;
                        long cpu=cpuStart<0?-1:bean.getCurrentThreadCpuTime()-cpuStart;
                        long bytes=allocStart<0?-1:allocation.getThreadAllocatedBytes(Thread.currentThread().getId())-allocStart;
                        if(frame>=warmup)results.add(new Sample(elapsed,cpu,bytes,previousStart==0?-1:start-previousStart,reused,visible,offset));
                        previousStart=start;
                    }
                } finally {text.close();}
                return List.copyOf(results);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("output.csv warmup samples");
        int warmup=Integer.parseInt(args[1]),samples=Integer.parseInt(args[2]);
        var out=new StringBuilder("count,scroll,cold,blur,sample,frame_ns,cpu_ns,allocated_bytes,interval_ns,reused,visible,offset\n");
        for(int count:new int[]{50,500})for(boolean scroll:new boolean[]{false,true})for(boolean cold:new boolean[]{false,true})for(boolean blur:new boolean[]{false,true}) {
            int i=0;for(var s:run(count,scroll,cold,blur,warmup,samples))out.append(count).append(',').append(scroll).append(',').append(cold).append(',').append(blur).append(',').append(i++).append(',').append(s.frameNanos()).append(',').append(s.cpuNanos()).append(',').append(s.allocatedBytes()).append(',').append(s.intervalNanos()).append(',').append(s.reused()).append(',').append(s.visible()).append(',').append(s.offset()).append('\n');
        }
        Files.writeString(java.nio.file.Path.of(args[0]),out);
    }
}
