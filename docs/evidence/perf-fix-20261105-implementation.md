# 2026-11-05 性能修复实现回交

源码已冻结，未启动游戏、修改 AtomChat、提交或发布。完整 root 回归、三端 native/build/artifact/publish 与部署由主线程接手；本文不把定向测试当作全链路验收。实现代理没有修改主线程负责的 UiSession、PresentationFrame、YogaLayoutEngine 及其两个测试。

## 基线与冻结

- 原有未提交改动基线：`build/perf-fix-20261105/before-source.zip`、`before-sha256.json`。不执行 checkout/reset，不用 HEAD 覆盖现有源码。
- 本实现涉及 12 个生产文件、5 个新增测试文件，逐文件 SHA-256：`build/perf-fix-20261105/implementation-source-freeze.json`。不含主线程自己的 5 个文件。
- TextService mutation 已按原字节恢复；SHA-256 `06bf0cf7459b0bdbbed512d4bc7c3ccb4096a0fbaf3855eb8c92b9df1131fa68`。mutation 运行过的 classes 输出需要主线程后续正常编译覆盖，不能打包为候选；主线程的强制回归流程负责覆盖。

## 已实施的可证开销

1. **GL**：`GlStateScope.java:29` 按 `(GLFW context, GLCapabilities identity)` 缓存锁定 Skia 的纹理范围：`min(32, GL_MAX_TEXTURE_IMAGE_UNITS)` 个单元，2D/rectangle，支持 OES external 时加 external。其他状态（包括完整 UBO、indexed blend、PBO、pixel store）不裁剪。`SkijaRenderBridge.java:125` 仅 drawFrame 选择此路径，并在首次初始化/重建前 activeTexture(0)，避免误改宿主最高单元。公开 `capture()`、`withContext()` 与 close 保持完整快照；resetGLAll 和旧 fastState 默认值不变。JFR 原事件保留，units/targets 反映真实查询范围（`GlStateScope.java:205`）。包私有 FULL 参考重载仅供相同绘制测试，不增加公共实验开关。
2. **Text**：`TextService.java:219` 按文本、字体 ID、字号缓存 TextLine，颜色和约束不污染塑形键；布局与绘制共享塑形。缓存同时受 entry/UTF-16 字符预算约束，超预算行临时使用并释放，淘汰立即 close。`TextService.java:208` 每服务惰性复用一个 Shaper，`212` 在字体 generation 变化时释放原生缓存，`240` 关闭全部本服务原生资源。layout 的热命中不再逐条扫描旧 generation。
3. **Icons**：`ItemRenderState.java:14` 用 ClassValue 缓存 Field 元数据，字段值、数组、矩阵和嵌套 cache 每次仍实时读取/快照。`ItemIconAtlas.java:31` 空/全空帧直接建立透明占位，不初始化 Skia，不 capture/flush；`66` 无 image 的 release 同样不进 bridge。三端 `MinecraftItemIcons.java:22` 空批次不 flushHost。帧 ID、单活动帧、异常恢复和过期合同保留。
4. **Backdrop**：`BackdropPrograms.java:11` 每次 link 后查询 9 个 uniform 位置，invalidate/close 清回 -1，不在稳态 draw 中重复 glGetUniformLocation。`BackdropBuffers.java:10` resize 复用 texture/FBO 对象，纹理存储仍按准确新尺寸 glTexImage2D；不扩容量、不使用旧尺寸 UV、不改变四遍过滤及任何 shader。它仍需重分配存储，不宣称 resize 零 GPU allocation。`BackdropRenderer.java:63` 仅在一个连续 render 调用内、源目标五元组完全相同时复用源校验，`36` 的公开 draw 入口始终重新校验，能检测独立调用间 attachment 被改。
5. **Skin**：`AtomChatControlSkin.java:19` 复用不可变 ControlMotion，避免每控件每帧 new record 及构造校验用的 double 数组；具体时长不变。没有凭猜测重写 path、材质或画质。

图标 ItemStack 构造时 copy 用于请求快照，prepare 中 getter copy 隔离原版/mod renderer 的可变输入；两者分别承担不同所有权，不移除。逐项 flush 保留，因为下一项切换 scissor，延迟批次可能使用下一项裁剪。非空 atlas 的提交/纹理借用释放屏障保留。对应三端 `ItemIconRequest.java:6/10`、`VanillaItemPass.java:37/40/43`；没有游戏/原生模型兼容性证据时不删除这些屏障。

## 原生范围证据与接口核对

Skija 版本由 `versions/dependencies.properties:1` 锁定 0.143.18。

- [Skija 0.143.18 build.py](https://raw.githubusercontent.com/HumbleUI/Skija/0.143.18/script/build.py) 第 9 行指定 `m143-da51f0d60e-5`。本地副本 `build/perf-fix-20261105/primary/skija-build.py:9`。
- [Skia GrGLCaps.cpp](https://raw.githubusercontent.com/JetBrains/skia/da51f0d60e/src/gpu/ganesh/gl/GrGLCaps.cpp) 第 494–497 行把 fragment samplers 限为 min(32, driver limit)；本地同名副本保留精确行号。
- [GrGLGpu.h](https://raw.githubusercontent.com/JetBrains/skia/da51f0d60e/src/gpu/ganesh/gl/GrGLGpu.h) 第 524 行定义 texture unit 数；[GrGLGpu.cpp](https://raw.githubusercontent.com/JetBrains/skia/da51f0d60e/src/gpu/ganesh/gl/GrGLGpu.cpp) 第 3015–3026、3062–3080 行覆盖目标集合、普通绑定及最后一个 scratch/upload 单元。本地 `primary/` 有完整版本及代码注释所指行号。
- Skija sources.jar 实查：`primary/Shaper.java:145`、`primary/Managed.java:13/25/43`、`primary/Font.java:47/208/274/296`，确认 shapeLine 返回新对象、原生所有权、Stats 和构造/setter/finalizer 的 JNI 计数。库内 Font 配置见 `runtime/.../FontRegistry.java:106`。没有凭印象拼接 SDK 返回字段。

## 红绿记录与指标

所有以下日志位于 `build/perf-fix-20261105/`，采用 JDK 21；root 命令均传 `-PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf`，Fabric 均从自己的平台目录调用自己的 wrapper，传 `--offline --no-daemon -PatomlibRenderEvidence=D:/AtomLib/build/perf-fix-20261105/gl/evidence`。

| 项 | 红灯 | 最终通过及范围 |
| --- | --- | --- |
| GL | `gl-red.log`：drawFrame 仍全单元查询，行为断言失败 | `gl-green.log`：10 项，包含完整 capture、JFR、最高单元/PBO/异常；`gl-ab.log`：4 项，增加 cube/array/sampler 与 FULL 对照 |
| Text | `text-red.log`：热 draw 没有保留任何 TextLine | `text-green.log`：旧两个缓存测试、TextLayout、FontIsolation 通过；`text-skin-final-green.log`：包括最后的 3 个缓存测试、像素一致、原生关闭及 skin |
| Icons | `icons-red.log`：空帧实际产生 2 个 capture；另一个 metadata 合同测试因缺少缓存入口失败 | `icons-green.log`：11 项，含空→非空、过期、扩容、方向、透明边缘、线程/context、异常恢复 |
| Backdrop | `backdrop-red.log`：resize 的旧纹理对象被替换；uniform/validation 两项新增探针尚不存在 | `backdrop-gl-final-green.log`：含新 3 项、原画面/alpha/脏状态/resize/reload/缺资源回归、4 项 Skia scope，以及 broken shader fallback |
| Skin | `skin-red.log`：相同常量 motion 每次返回新实例 | `text-skin-final-green.log`：对应行为通过 |

调用错误单独披露：最初 `gl-red.log` 曾因 PowerShell 未引用 `-Dfile.encoding` 失败，随后正确重跑并保留真实行为红灯；最初 Text 测试拼错不存在的 `Constraints.unbounded()`，读本地 API 改正后重跑真实红灯。`backdrop-green.log` 是错误 root task 查找失败，不是代码失败，也不是绿灯；正确结果只看 `backdrop-gl-final-green.log`。

Text 最后指标：100 次单 run draw，cached=1000、强制不保留塑形结果的对照=1700 次 JNI。每命中省 Font 创建、三个配置、shapeLine、Font 和 TextLine 两个 finalizer 共 7 次，即 700。最初新增测试使用无源码根据的“减半”阈值而失败，日志 `text-skin-green.log` 如实保留；它不能否定已测得的 700 次减少。纠正为明确 700 后通过。临时把所有 shapedLine 结果改为不保留，差值成为 0，`text-cache-mutation-red.log` 在同一断言真实变红；随后按字节恢复，未靠放过无收益基线获得绿灯。该指标是 JNI 数量，不能直接换算成面板时间。

GL 最后一次定向运行的原始样本已独立冻结在 `gl/scope-full-skia-frozen.csv`，统计与 SHA 见 `gl/scope-ab-final-summary.json`，避免后续全量测试覆盖 `gl/evidence/scope-full-skia.csv`。每模式预热，三轮 FULL/SKIA 交替，每组 80 帧；同一 64×64 图像/圆角 clip/AA scene，计时包含 drawFrame 和 glFinish；完整画面逐字节一致。此 FULL 参考入口与 SKIA 入口共用完全相同绘制逻辑。

| 轮次 | FULL p50 / p95 ms | SKIA p50 / p95 ms |
| --- | --- | --- |
| 0 | 2.8075 / 3.6392 | 0.5729 / 1.1214 |
| 1 | 2.5402 / 2.9270 | 0.6031 / 1.1430 |
| 2 | 2.5152 / 2.7876 | 0.5986 / 1.2061 |
| 全部，各 240 样本 | 2.5923 / 3.3139 | 0.5948 / 1.1521 |

之前第一次 A/B 的 2.5877→0.5794 ms 汇总属于之前一轮运行；不能与最后 CSV 混用。相同机器离屏三轮方向一致；未重新录制游戏，不宣称实际 FPS 或把原实机 capture 的 2.1335ms 全算作已消除。

## 主线程五文件窄审

使用 code-review 技能，对照 before-source.zip，splitlines() 去除行尾噪声后审查；规范化差异保存为 `review-*.java.diff`。没有编辑这些文件，也没有另派代理。

**[P1] Yoga 失败事务后不能直接复用旧 snapshot 快路。** `ui/src/main/java/com/atom/lib/layout/internal/YogaLayoutEngine.java:74/75` 只比 previous（上次成功树）与输入树，未证明 entries/native tree 仍对应 previous。具体复现：成功布局含 `a` 的 A → 尝试含 `b` 的 B，B.measure 抛错 → 重试 A（同 viewport）。B 的失败事务已经在第 92–94 行删掉 a，previous 仍为 A；新快路进入 `entries.get(a).measure=...`，抛 NPE。JShell 已用现有编译输出与真实 Yoga natives 复现，日志 `build/perf-fix-20261105/review-yoga-failed-transaction.log`，栈定位第 75 行；没有调用 Gradle或写入源码。

建议主线程在失败后明确标记 native 状态不再与成功 snapshot 对齐，禁止快路，或重建/核对 native entries；补“结构变化失败后重试旧树”，同时覆盖 measured/container 切换失败后的恢复。已有 `YogaLayoutEngineTest.java:64` 恢复时递增 revision，未覆盖此路径。该 P1 已立即单独回报主线程；由主线程修复与验证，不能把当前集成快照称为全部收口。

其余窄审未发现新缺陷：UiSession 的 index 与 controls 在取消前同步发布，close 清空二者且 `!closed` 防止重新建立 ownership；Control 是 sealed 且当前实现无 equals/hashCode 改写，HashSet 保持旧 contains 的成员语义（`UiSession.java:69/75/278`）。PresentationFrame 的 create/scaled/compose 保持 boxes 与 order 的成员集合一致；withScroll 清空同 scale cache（`PresentationFrame.java:19`）；新 scale/new frame 使用独立 cache，故 Map membership 与旧 order.contains 在这些合法构造路径上等价。Yoga sameLayout 比较 style、测量有无、revision 和子序，正常变化路径会更新；问题集中在上述失败事务之后的早退。
