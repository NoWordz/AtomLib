# 图片预处理与离屏密度检查（2026-11-03，待独立复核和实机验收）

## 本次库侧收束

主代理独立复核最终源码：在 `build/atomdefault-independent-review-20261103` 使用 JDK 21、离线 `:core:test :runtime:test :ui:test --rerun-tasks`（明确传入项目默认字体夹具）重跑 207 项，无失败/跳过，13 项 Gradle 任务实际执行。独立核对三个交付 JAR 摘要并运行 `verify_artifacts.py`，再比较 beta3 与实施前 0.2.0 的公开/受保护声明，三端无缺失；三端 beta3 编译的有限旧消费者均运行通过。主代理另在独立输出发布到本地 Maven，并从不含原例子构建缓存的隔离源码副本运行六个 `verifyConsumer`，均通过；该 Maven 三端 JAR 摘要与本轮交付候选完全相同。六个消费者的编译与验包任务实际执行，NeoForge 工具链有缓存复用，消费者本身没有 JUnit 测试。这些结果不代表真实 Minecraft 或 AtomChat 已运行。三端完整 GL 47+1 由 Terra 执行；主代理独立执行范围另见 [STATUS](../../STATUS.md)。

`ImageStore.draw` 新增显式选用缓存、设备密度与采样模式的重载；原入口不变。CPU 光栅用例覆盖普通图、像素画最近邻、`COVER` 裁剪和物理尺寸、同源不同尺寸键、透明边缘、延迟解码回退、线程归属、源/派生句柄、reload、弱引用及 4096×4096 源图 / 2200×2200 目标超过 4Mi 像素预算时返回可绘制源图。GPU 图像由真实 hidden GL snapshot 验证直接回退且仍可绘制。自动导入并不改变源图片生命周期，资源重载时应协调清理缓存；见 [runtime 合同](../../wiki/runtime.md)。

密度审查发现 `ScrollViewport.present(...).scaled(...)` 原来丢失滚动快照；测试先因找不到滚动成员失败。修正后范围、内容长度、偏移和成员随展示几何同步缩放，`forPainting` 与多层合成保留对应坐标；真实 raster 像素、点击和滚轮定向测试通过。一次测试夹具把控件高度设得不足引发既有文字几何保护，改成自然高度后通过。

最终公共模块在独立目录执行 `gradlew.bat --offline --no-daemon --rerun-tasks --project-cache-dir build/atomdefault-closure-20261103/project-cache -I build/atomdefault-closure-20261103/isolate.gradle :core:test :runtime:test :ui:test -PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf`：core 24、runtime 47、ui 136，共 207 项通过，无失败或跳过，13 个任务实际执行。随后为滚动坐标补充逆变换/合成断言并定向通过；最终完整公共命令再次通过，其中 core/runtime 等 12 个任务为 UP-TO-DATE，UI 测试实际执行。

Forge/JDK17、Fabric/JDK21、NeoForge/JDK21 在 `build/atomdefault-closure-20261103` 各自独立输出执行 `:distributionJar :verifyArtifact :hiddenGlTest :hiddenGlBrokenShaderTest`，三端各 47 项 hidden GL 与 1 项 shader 故障回退通过，验包通过。三端构建任务实际执行；NeoForge 的 Minecraft 工具链准备有缓存复用，输出包含 download 命名步骤，不能由此推断实际网络请求；Gradle 自动清理了其全局 neoform 缓存中的 13 个过期锁文件。未启动游戏。三个 Python 制品/消费者/样例守卫分别 34、2、3 项通过。

三端隔离 JAR SHA-256：Forge `3d11f3543742aac402238e64804ef5d2c70af4d2d93c4c63473139fb4a81362e`；Fabric `86d754bf1281eb9310f3825f225b0fdb4d5de72d1ca27f2af71f7784b64522f3`；NeoForge `2e4e13de9d16a0181c1ee35ca58b28b0a68397a5be592673ae90b967ec2c7ba3`。与主代理上一候选的公开/受保护声明对照没有缺失，三端各运行 beta3 编译的有限旧消费者和旧皮肤默认方法检查通过；并不代表 AC 本体或游戏运行通过。

本轮重跑 CPU raster 样本见 [图片](closure-image-downsampler.csv) 和 [合成列表](closure-synthetic-list.csv)，输入、设备和方法与下文一致，后者 16 组各 10 帧；图片冷调用中位约 1.012ms、热缓存约 0.00165ms，仅描述本次单机样本。与此前 11-03 首版模糊探针标签数据无关；修正后的 GPU 分段和限制见 [模糊探针](../backdrop-timing-20261103/report.md)。全部改动及用户/主代理既有未提交文件清单见 [工作区清单](changed-files-closure.txt)。

真实 Minecraft guiScale、ARM64、Iris/Gnetum、多模组、真实图像/HDR、长时 GPU 内存、真实 FPS、用户视觉与动效均未验收；图标真实模型、模糊滤波/合成独立分段和 AC 能力协同属于待办。旧拆分 `SettingsGroup.paint` / `UiSession.paint` 不自动组成一个组透明图层；使用共同合成入口。此次无部署、启动游戏、提交、推送或发布，等待主代理独立审查。

> 后续进展：Terra 再次因服务端 429 中断，但中断前新增 `ImageStore.draw` 的可选降采样重载及 GPU 图像回退测试。主代理对最新源码强制重跑公共模块 core 24、runtime 45、ui 134 项，全部通过；NeoForge GPU 图像回退的定向隐藏 GL 通过。原绘制 API 不自动启用该功能。三端最新制品和 GL 结果以本轮后续记录为准；真实游戏、长时间资源与完整独立审查仍未完成。

> 主代理复核补充：Terra 两次因 429 中断，未完成最终交付。`SettingsShell` 的共同合成候选已通过透明/不透明背景的像素回归；旧 `SettingsGroup.paint` 与 `UiSession.paint` 分离调用仍不会自动合并图层，原失败探针保存在 `build/atomdefault-independent-review-20261103/legacy-split-opacity-probe.java`。装饰回调泄漏画布变换的测试先失败，主代理添加 save/restore 后通过。最新完整公共模块测试为 core 24、runtime 39、ui 134，全部通过；三端分别通过 44 项隐藏 GL、shader 故障回退及验包，旧公开接口声明对照未发现缺失。此前“唯一失败”的描述是历史测试状态，不代表当前测试仍失败。完整独立审查和实机验收仍待补充。

## 来源与合同

工作区基线为 AtomLib `878ccc4cc5080b74546b0c3f892d647dce5598d0` 加本任务前两阶段未提交改动；AtomChat 只读，观察 HEAD `6452920ab60e46bfafd1a18191ed2e9725ce24ca`，有既存未提交依赖改动。`ImageDownsampler` 根据本方案中预算、借用、淘汰和弱引用的行为要求独立编写，调用已锁定的 Skija API；没有复制或翻译 AtomChat `ImageQuality`、Dango/UiStagger 表达，也没有沿用其 2×/3× 阈值。它是可选、实例持有的服务；旧 `ImageStore` 调用路径不变，后续新增重载可显式接入。仅允许直接可读取像素的 CPU 图参与，实际 `ImageStore` 常见延迟解码源可能因而回退。API 与限制见 [runtime](../../wiki/runtime.md)。

先建立真实失败测试：选择/缓存/预算/弱引用与关闭 5 项在空实现抛出未实现错误；直接缩小棋盘格时实际像素 225，分级缩小后回到测试范围；延迟解码图先错误生成派生图，随后改成借用源图；跨线程清空先成功关闭派生图，随后明确拒绝。真实 native 句柄断言覆盖驱逐、源关闭、GC 后维护、clear、close 和来源不被关闭；无后台 GC 或 GPU 内存长期统计。

密度用例使用真实 Yoga、Skija raster 和 UiSession，在 1、1.5、2、3 四种 density 与 0.8、1、1.25 三种布局比例下核对带小数动画偏移的图像像素和点击；另测 1、2、3 密度下 1 逻辑单位描边宽度以及 1 物理像素动画位移。由宿主将画布缩放一次，事件传显示逻辑坐标；这些不构成 MC guiScale/真实 GPU 验收，不规定所有填充边必须像素对齐。

## 可重复样本

设备 Intel i7-14650HX、Windows x64、Java 21.0.11，CPU Skija 栅格；代码编译目标 Java 17。图片测试输入程序生成 1024×1024 不透明横条，目标 128×128，预算 300000 像素，阈值 2 仅为测试输入，10 次冷缓存、10 次热缓存、10 次像素画旁路。原始数据 [image-downsampler.csv](image-downsampler.csv)：首个冷调用约 4.745ms，冷样本中位约 1.018ms，热样本中位约 0.00165ms；单次运行、无外部设备隔离，不能视为产品收益或推荐阈值。

合成列表输入不是聊天：50/500 条固定文本、每帧请求全部布局、只绘制 12 条，静止/滚动、每帧清空/复用布局缓存、纯 CPU 背景模糊开/关，共 16 种组合；每组合预热 4 帧、采样 10 帧，见 [synthetic-list.csv](synthetic-list.csv)。`reused` 是布局对象身份复用数；`interval_ns` 是不节流循环中帧开始间隔，`cpu_ns` 和 `allocated_bytes` 为 JVM 当前线程计量，Windows 线程 CPU 时钟分辨率导致不少采样为 0，不能将其解释为无 CPU 开销。native 分配、GPU query、Minecraft 帧率均不在此夹具中。

在仓库根目录分别运行，使用独立输出路径，避免两个任务共用 `benchmarkOutput`：

```powershell
.\gradlew.bat --offline --no-daemon :runtime:imageDownsamplerBenchmark -PbenchmarkOutput=D:/AtomLib/build/atomdefault-image-20261103/image-downsampler.csv -PbenchmarkSamples=10
.\gradlew.bat --offline --no-daemon :ui:syntheticListBenchmark -PbenchmarkOutput=D:/AtomLib/build/atomdefault-image-20261103/synthetic-list.csv -PbenchmarkWarmup=4 -PbenchmarkSamples=10
```

本次同时传入 `--project-cache-dir build/atomdefault-image-20261103/project-cache -I build/atomdefault-image-20261103/isolate.gradle` 隔离构建输出。夹具不要求启动游戏，不访问 AtomChat。原始 CSV 是本次工作区未提交代码的样本，不能与之前 AtomChat 离屏历史数字直接相减。

## 独审阻塞和验证状态

历史独审探针中，先后独立绘制半透明组背景与控件时最大 alpha 150，要求不超过 130；两个旧独立调用仍不会自动合成一个组图层。`SettingsShell.paint` 使用同一图层组合组卡片及控件，不透明背景的半透明插值测试从误差 27 转绿，另测多组透明度及装饰绘制失败后状态与输入快照；当前 `GroupEntranceOpacityReviewTest` 验证的是共同合成路径。原两个调用已经分别写入目标；仅用透明画布上的混合模式或延迟关闭 saveLayer 会破坏不透明背景、旧调用的 save/restore 或多个组的顺序，不能作为兼容修复。宿主需使用共同合成入口或自行组织图层。

历史根公共测试曾为 core 24/24、runtime 39/39、ui 129/130；那次失败对应上面的旧拆分路径，不代表当前测试状态。后续主代理验证与最新结果见本页开头及状态文档。完整测试的工作目录已单独隔离，避免字体证据测试覆盖既有候选。Minecraft、GPU 图片长时内存、ARM64、Iris/Gnetum、HDR、用户视觉和真实帧性能均未覆盖。
