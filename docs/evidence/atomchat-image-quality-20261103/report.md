# AtomChat ImageQuality 超预算只读复现

2026-11-03，在 Windows x64 / Java 21.0.11 使用 Skija 0.143.18 CPU raster 和已存在的 NeoForge 1.21.1 AtomChat 0.4.0 JAR 运行 `ImageQualityBudgetProbe.java`。JAR SHA-256：`d58edf3c4ffa00ca8648e65c9fead2d8045c29d01456b33a66901bb733fbe81f`。

输入为 4096×4096 的不透明 raster 图片，`ImageQuality.scaledFor(source, 1100f)`，空缓存。源码中的 3× 门槛允许进入，派生目标为 2200×2200、4,840,000 像素，大于 4,194,304 像素预算。输出：

```text
sourceClosed=false
sameAsSource=false
resultClosed=true
```

即返回了已经关闭的派生图，而调用者的源图仍然打开。该探针只证明指定实际 JAR 的单源超预算边界；没有启动 Minecraft、检查安装实例、运行多来源碰撞或证明普通聊天图一定命中。仓库源码未改动。代码在 `shared/src/main/java/com/atom/chat/render/ImageQuality.java:121` 插入后裁剪，再在 `:128` 返回。下一步应在 AtomChat 的并行负责人处理时补可重复回归，并先确定大于预算时借用源图或其他符合所有权的降级。

运行：设置 classpath 为上述 AC JAR、Skija shared 0.143.18、Skija windows-x64 0.143.18 和 humbleui types 0.2.0；执行 `java -cp <classpath> ImageQualityBudgetProbe.java`。源码 SHA-256：`1c92bb14f49b8224efdf0152496966ef40cff6cc66c89c0016d4dc332d61a215`。
