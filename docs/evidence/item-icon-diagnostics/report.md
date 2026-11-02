# 物品图标离线诊断（2026-11-02）

状态：**PARTIAL_OFFLINE_DIAGNOSIS — 游戏、体验和单项失败隔离均未验收**。源码基准为 `c3188d4500cc88df598ef12aa521cfba05e2c65f`；没有更改 AtomLib 生产代码。合成 GL 图案不包含 Minecraft 物品模型，不能把这些数字当成游戏帧耗时。

## 环境与方法

Windows、Intel i7-14650HX、NVIDIA GeForce RTX 5060 Laptop GPU，GL `3.3.0 NVIDIA 617.42`；Fabric 探针使用 JDK 21.0.11、64×64 隐藏 GLFW 3.3 core 窗口。测量结束后独立采样系统总 CPU 负载约 6.9–10.9%，并非逐样本负载记录。每组每轮新建 atlas，16 个 32px 合成图案；1 次冷样本、16 次预热、32 次热样本，共三轮。总区段是 `prepare` 到 `close` 返回，`pass` 包含在 `prepare` 内，不能相加；查询读取发生在总区段之外。

新探针源码为 `platforms/shared/src/test/java/com/atom/lib/client/render/item/ItemIconDiagnosticsGlTest.java`。原始数据及日志均在 `D:/AtomLib/build/item-icon-diagnostics-20261102/`；首轮数据位于 `fabric-diagnostics/item-icons/diagnostic-samples.csv`，补充无 pass 计时对照位于 `fabric-overhead/item-icons/diagnostic-samples.csv`，独立状态操作位于 `fabric-diagnostics/item-icons/isolated-state-samples.csv`。CSV 单位为纳秒，GPU 查询 `-1` 为不可用；延迟查询的 GPU 值对应四次迭代之前，不可用时只检查一次，不等待。查询组顺序固定，结果受顺序和系统负载影响。没有调用 `glFinish`。

## 数值及可得结论

补充对照的 32 个热样本中位数/p95（毫秒）：

| 轮次 | 无 query 总计 | prepare（含 pass） | close | 同步 query 读取（不含在总计） | 无 pass 计时总计 |
| --- | --- | --- | --- | --- | --- |
| 1 | 11.676 / 12.820 | 8.739 / 9.631 | 2.898 / 3.378 | 0.188 / 0.226 | 12.108 / 14.071 |
| 2 | 12.063 / 13.450 | 9.021 / 10.173 | 3.030 / 3.425 | 0.391 / 3.625 | 11.929 / 12.630 |
| 3 | 11.118 / 11.904 | 8.243 / 9.124 | 2.785 / 3.205 | 0.418 / 5.166 | 11.911 / 12.593 |

无查询组中的合成 pass CPU 中位分别为 0.013、0.007、0.004 ms；空 pass 总中位 11.665、11.169、10.920 ms。无 pass 计时对照仍测量总体 prepare/close，组间先后顺序固定；差异没有稳定方向，因此不能从这组数据精确估计全部插桩开销。同步 query 总耗时中位为 12.074、12.286、11.201 ms，延迟 query 为 10.987、11.965、10.317 ms，与无 query 分布重叠；延迟组每轮 32 个热样本仅 26 个查询在首次检查时可用。同步等待确实存在且尾延迟可达数毫秒，是否改变后续帧节奏不能从固定顺序的样本中定量排除，但它不能被证实是约 10–12 ms 的主要原因。GPU 查询只包合成清除图案，不是物品模型或完整 GPU 帧时间。

单独执行空状态操作，每轮 32 个热样本中位：`GlStateScope.capture` 为 2.516、2.150、2.138 ms；配对 restore 为 0.037、0.034、0.031 ms；包含自身状态保护的空 `bridge.withContext` 为 2.497、2.194、2.151 ms；带空 flush 的 `withContext` 为 2.512、2.293、2.199 ms。这些独立调用不是嵌套轨迹，不能简单相加得出真实帧分摊，但指出完整状态捕获值得进一步调查。`prepare` 中的前置 flush、外层捕获、容量检查/扩容、清空与像素归一、借图导入和帧构造尚未逐项插桩；`close` 的 flush 与保护也未单独拆分。首帧和容量分配数据见 CSV 的 `cold` 与 `allocation_count`，当前探针未单独测试二次扩容或精确容量。故本任务要求的完整分段尚未完成，不声称已找出唯一根因或安全优化。

## 异常与修复决策

三端 `MinecraftItemIcons.prepare` 在单项原生绘制抛异常时记录平台及 key 到异常消息并中止整批；并没有占位或继续其他项目的实现。`VanillaItemPass.draw` 使用宿主共享的顶点消费者，失败后其缓冲中是否存在不完整批次未知，而 `close` 仍会 flush；GL 外层快照及已有合成异常重试不能证明 Minecraft 缓冲、RenderSystem 缓存和模型状态均可安全恢复。捕获后继续其他项目会有状态污染或误画风险，因此没有实施伪恢复、删除保护或缓存。

后续应先构造可控的真实原生绘制失败，明确隔离批次、矩阵、RenderSystem 和 owned framebuffer 的恢复条件；仅对可证明恢复的失败返回带 key 的占位并按项记录日志；全局/状态恢复失败及 `Error` 必须中止，释放句柄后重试。占位数据怎样穿过当前批次属于需进一步设计的接口问题，尚无失败注入或完整合同证据。真实游戏场景禁止自动启动，须由用户参与验收；不能把纯 GL 注入称为原生故障隔离通过。

## 试用工程

在 `D:/AtomLib/build/item-icon-diagnostics-20261102/creatortrial-work/` 的隔离副本保留原 24 个条目，新增时钟、指南针和独立 key 的附魔钻石镐。镐使用世界附魔注册表创建真实耐久附魔；无世界时返回空物品，不假装附魔成功。JavaExec 覆盖三个 key 可进入可见请求，实际模型、附魔、时钟和指南针动态仍未实机验证。

F9 默认关闭，再按开启/停止，停止时只输出一次本地 `CreatorTrial diagnostics` 日志。缓冲区每类最多 4096 个样本；区分弹窗开启与关闭的连续 `MinecraftClient.render(boolean)` HEAD 间隔，状态转换区间丢弃，缺样标 unavailable；记录页面可见数、像素范围、prepare、页面绘制和 close 的 CPU 样本。页面样本只在弹窗打开时记录，背景模糊关闭。帧间隔包含限帧/VSync 等其他工作，不等于图标耗时；加载、资源重载等路径也可能调用此 render，采样时应避开，重载功能单独检查。未运行客户端，因此 mixin 接线、真实采样、插桩开关干扰和汇总日志均未实机验证。包中的 refmap 静态核对 `render -> class_310.method_1523(Z)V`、`tick -> method_1574()V`，不等于运行验证。

## 验证记录

所有 Gradle 命令均带 `--offline --no-daemon --console=plain`，完整输出在上述构建目录。Fabric 使用 JDK 21.0.11，Forge 使用 JDK 17.0.18，NeoForge 使用 JDK 21.0.11。

- Fabric：`hiddenGlTest --tests 'com.atom.lib.client.render.item.ItemIconDiagnosticsGlTest'` 两个独立探针分别执行通过；补充 pass 计时开关对照再执行一次通过。新旧数据未覆盖。
- 三端分别执行 `hiddenGlTest --tests 'com.atom.lib.client.render.item.ItemIconHiddenGlTest' --tests 'com.atom.lib.client.render.item.ItemRenderStateTest' --rerun-tasks -PatomlibRenderEvidence=<各自独立目录>`：各 7 项实际执行，失败 0、跳过 0；合计 21 项。其中一项是合成 Java 状态测试，非真实 RenderSystem。
- 三端分别执行 `:build :publishToTestRepository :verifyArtifact -PatomlibTestMavenRepo=D:/AtomLib/build/item-icon-diagnostics-20261102/rebuilt-maven -PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf` 成功。这里 publish 是写到新的本机隔离目录，不是公开发布。Fabric 和 NeoForge 多个构建任务为 UP-TO-DATE；产物校验和 Maven 写入实际执行。平台普通 `test` 为 NO-SOURCE，不能算作新执行的测试。
- 三端各执行一个 `examples/a/<target>` 的 `verifyConsumer -PatomlibTestMavenRepo=<上述目录>` 成功，编译/打包部分有 UP-TO-DATE；并非六个消费者全重跑。
- 隔离试用工程执行 `build trialChecks`，11 组 JavaExec 检查通过；`remapSourcesJar` SKIPPED，常规 JUnit 无测试。样本数量行为红灯见 `trial-red.log`，诊断控制器行为红灯见 `diagnostics-behavior-red.log`，最终绿灯见 `trial-final.log`。这些检查不运行原生 Minecraft 实例。
- 本轮没有变更根模块生产代码，也没有重跑根模块测试；沿用旧候选的根模块证据，不能写成此次重跑。
- NeoForge 离线执行输出中出现 `downloadJson`、`downloadClient`、`downloadServer` 等工具步骤，Forge 亦出现 `downloadMcpConfig`。没有请求或启动游戏下载 assets；单凭这些日志不能证明底层准备步骤是否访问网络。

## 制品与保护

三端重新写入隔离 Maven 的 JAR SHA-256 均与上一候选一致：Forge `d1cc3838812bd29cc79e19fdd7bd3964911098c5e2596415e177890ad94a5157`；Fabric `7a96b1274d2f07d4a2ff1b0da36b849d960171c31c9f2dc5f84f6b9930977398`；NeoForge `66d60cb9a1e7274de3e33ec7888581e500f25331955752fff85a0b1463230090`。试用 JAR SHA-256 `6abf374ad6ddb03ab6806ae377eedd52effb2cc893bd7d313f1c323542e77ac2`。完整路径、输入锁、试用源码快照、SHA-256 与大小见 `D:/AtomLib/build/item-icon-diagnostics-20261102/manifest.json`；该锁的编译和运行时均验证了同一 Fabric JAR 的真实路径与摘要。

原 CreatorTrial 的 76 个既有非 build/缓存文件，及原候选 Maven 的 90 个文件，分别与工作前备份按字节核对无差异。原候选 `input.zip` SHA-256 仍为 `d00b82a1c460aa89ff43a9e9208e65a3b17f6c618531b922daa303f7eb9f6e1d`；原输入锁仍为 `8159538ee7562946162a50b0c8692c17462c9a6e8841bd1a19e86e61f2ec74db`。未核查原试用 build/缓存全量字节；其原工作区未用于本次构建。

## 用户手动验收待办

在你指定的现有 Fabric 1.21.1 实例中，由你自行核对 Fabric Loader 及 AtomLib 依赖、避免同模组重复 JAR，然后安装上述 Fabric JAR 与试用 JAR；尚未对任何实例执行安装。进入有效世界，固定镜头、窗口大小、VSync、帧率上限及其他模组，先预热并观察诊断关闭时体验；按 F9 开启，采集弹窗关闭状态后 F8 打开试用页、点“选择物品”采集开启状态，结束按 F9，从本地日志取一次汇总。需要分别记录时段、设置和可见数量；两状态差值包含页面等其他工作，不直接归因于图标。

检查普通方块/物品、附魔闪光、时钟和指南针（记下世界时间及指南针观察条件），分类、滚动、选中/确认/取消、键盘、关闭重开及资源重载；不能观察动态变化就记未验证。检查世界和原版界面是否被污染；发生崩溃或状态污染时停止并保存日志。此步骤尚未执行；Forge/NeoForge 实机、其他 GPU/ARM64、特殊物品渲染器和整合包兼容均未验证。

## 主代理复核

在隔离试用工程中以 JDK 21 执行 `gradlew.bat trialChecks --rerun-tasks --offline --no-daemon --console=plain`：11 组 JavaExec 场景实际执行通过，5 个 Gradle 任务实际执行。独立重新计算 `manifest.json` 所列 7 个文件 SHA-256，均与清单一致；试用 JAR 内的 refmap 将 `render` 映射至 `class_310.method_1523(Z)V`，将 `tick` 映射至 `class_310.method_1574()V`，且 JAR 不包含 `com/atom/lib/` 或 `io/github/humbleui/` 类。此为静态及离线复核，不是游戏启动或真实物品渲染验证。主代理复核期间没有重跑三端构建和 GL 测试。
