# 面板性能修复：2026-11-05

本轮修复已完成并部署，由一个子代理和主代理交叉复核；不要求再次录制已有计数场景。执行清单见 `docs/superpowers/plans/2026-11-05-render-performance-fix.md`。最终构建部署状态见文末和隔离回执。

## 根因和已实现项目

旧实测：23,368 个完整面板帧全部只捕获一次，capture p50 2.1335ms，逐帧 capture/enter 中位 99.813577%。证据正本 `docs/evidence/gl-scope-sample-20261105-103433.md:3`。

| 项目 | 实现与证据 |
| --- | --- |
| GL 过量查询 | `platforms/shared/src/main/java/com/atom/lib/client/render/GlStateScope.java:29` 给 Skia 绘制使用限定纹理范围，公共 capture/withContext 仍保存全量状态；`SkijaRenderBridge.java:125` 接入，rebuild 前显式选择受保护的 unit 0。保留 resetGLAll 及既有默认关闭的 fastState 实验。 |
| 每帧重塑形 | `runtime/src/main/java/com/atom/lib/text/TextService.java:220` 缓存 TextLine，按文字/字体/字号区分，复用一个 Shaper；字体 generation 改变才清理，缓存按条目和字符预算淘汰关闭原生对象。 |
| Yoga 重排 | `ui/src/main/java/com/atom/lib/layout/internal/YogaLayoutEngine.java:31` 按 revision 和 measure 有无判断失效，更新最新 callback；`:74` 同布局复用 snapshot，比较结构/样式/revision，不因新 lambda 引用失效。 |
| 会话查询 | `ui/src/main/java/com/atom/lib/controls/UiSession.java:114` 按 NodeId 查 Map；显示成员和列表替换按 Map/Set 查询，保留取消/焦点/替换异常行为。 |
| 缩放几何 | `ui/src/main/java/com/atom/lib/layout/PresentationFrame.java:67` 同帧按缩放值复用绘制坐标，避免每个控件复制整页 box Map。 |

GL 范围的原生依据已落盘 `build/perf-fix-20261105/primary/`：Skija build.py 指向 Skia `m143-da51f0d60e-5`；`GrGLCaps.cpp:493` 限制 fragment sampler 数至 32，`GrGLGpu.h:524` 与 `GrGLGpu.cpp:3062` 确定普通/上传 scratch unit 的范围；`:3015` 列出 2D/rectangle/external 三种纹理目标。外部纹理仅在对应扩展存在时查询。UBO、indexed blend/color mask 等未为了省查询而盲目移除。

## 首批验证

- `build/perf-fix-20261105/gl-red.log`：未改实现时，新的 GL 范围断言实际失败。
- `gl-green.log`：专用路径及旧完整 scope 状态回归通过。
- `main-fabric-gl.log`：主代理独立执行 Fabric 全量隐藏 GL 和破损 shader fallback，通过。
- `text-red.log` / `text-green.log`：原实现未保留 TextLine 的行为红灯；缓存和原生关闭、换代、像素一致性测试通过。
- `ui-red.log` / `ui-green-suite.log`：同缩放未复用几何的行为红灯；实现后 UI 全套通过。
- `yoga-red.log` / `yoga-green-suite.log`：新 lambda 同 revision 仍新建 snapshot 的行为红灯；实现后 UI 140 项通过，包含约束变化使用最新 callback 和 revision 变化重新测量。

## GL 离屏 A/B

测试 `platforms/shared/src/test/java/com/atom/lib/client/render/SkiaScopePerformanceTest.java`，相同 context/目标/绘制内容，FULL 与 SKIA 交替三轮，每种每轮 80 样本，之前交替预热 60 次；图像上传、非矩形抗锯齿 clip 和半透明图形逐像素一致。每次绘制前后包含 glFinish，测量的是该离屏场景完成的墙钟，不等于游戏帧率。

主代理独立重算最后冻结 CSV，样本 `build/perf-fix-20261105/gl/scope-full-skia-frozen.csv`，SHA-256 `ae2fd501495cf74b6a577754247fc79237512889af7c092827aec67e008670fb`，摘要 `main-frozen-ab-summary.json`（早先一次运行是 2.5877→0.5794ms，不与本表混用）：

| 路径 | 样本 | p50 ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: |
| FULL | 240 | 2.5923 | 3.3139 | 3.8979 |
| SKIA | 240 | 0.5948 | 1.1521 | 1.2578 |

三轮 FULL/SKIA p50 分别为 2.8075/0.5729、2.5402/0.6031、2.5152/0.5986ms。此为隐藏 GL 的实际收益证据，没有新的 Minecraft 性能验收数据。

## 交叉审查修正

唯一子代理以 code-reviewer 角色对主线程五文件审查，抓到一项 P1：Yoga 的失败事务已经更换 native 节点，重试上次成功树时不能继续走 snapshot 快路。主代理新增结构换 ID、容器/测量模式变化失败后重试，以及后续 viewport/density 变化的回归，旧实现实际失败（`yoga-failure-red.log`）。修正为仅成功布局后 nativeStateValid=true，异常置 false；失效后禁用快路并重新标记整棵 native 子树。原 reviewer 只读复核确认 P1 关闭。此后重新执行完整 root 与三端验证，不使用修正前的绿灯代替。修正位置 `YogaLayoutEngine.java:73`，测试 `YogaLayoutEngineTest.java:10`。

## 最终集成状态

Yoga 审查修正后重新执行完整流水线，`build/perf-fix-20261105/verification-results.json` 四阶段均 exitCode=0：root 强制重跑测试；Fabric、Forge、NeoForge 各全量 hiddenGlTest、broken shader fallback、build、verifyArtifact、发布到隔离本地 Maven。日志为 final-root.log、final-<target>.log。测试计数及既有 skip 见 final-test-summary.json。

额外通过独立发布制品加载探针：`examples/plain-paragraph/build/plain-consumer-1.21.1-fabric.log`，使用本轮隔离 Maven 而非工作区 classes。三端 sources 包的 12 个改动生产文件与最终源码正文一致，dist JAR 中确认专用快照、塑形缓存、Yoga 事务有效性标记和会话索引均已打包；Fabric remapper 添加了五个无用 import，首次逐行比较因此失败，排除 import/空行后正文一致。详见 `packaged-source-check.json`。

2026-11-05 12:31（上海时间）已部署三端 AtomLib：Forge `1.20.1-main`、Fabric `1.21.1-CCB`、NeoForge `Mechanomania-航空学`。`build/perf-fix-20261105/deployment-receipt.json` 为 deployed_and_hash_verified，rounds=3、errors=[]、wmiGameProcesses=[]。旧包备份后缀 `.bak-perf-20261105-123157`；部署文件及备份均再次独立读回 SHA-256 验证；AtomChat JAR 哈希及其他现有模组文件元数据不变。未启动游戏、提交、推送或公开发布。

## 保留的行为与下一步

ItemStack 的请求快照/渲染隔离复制、逐项 scissor 的 flush、动态图标每次重绘仍保留；模糊四遍全分辨率与精确尺寸存储保持，resize 复用对象但仍需 glTexImage2D；通用完整 GL 快照、模态隔离层和 SVG path 解析没有盲目删除。详细分析及原生源码链接见 `perf-fix-20261105-implementation.md`。本轮不是宣称 issue 每个建议都已实施。

现在用户直接启动 Fabric CCB，正常开面板、切页即可试用，无需再运行计数采样脚本。已确认的是自动回归、像素/状态恢复及离屏性能收益；游戏 FPS 的实际改善留待该次正常试用反馈。

## 2026-11-05 复核与 AC 侧续修

后续复核没有继续改 AtomLib 生产代码。用户运行后的 `latest.log` 显示 `BRIDGE_ENTER` 已从修复前 p50 `2.13ms` 降至 `0.46ms`，面板稳定态 p50 约 `2.22–2.35ms`；剩余尖峰集中在切页（panel p95 `7.17–8.13ms`、max `33.95–50.55ms`）。因此没有再合并或删除 GL scope 快照。

旧 JFR 的 19 次导航按单调时钟重新配对后，设置详情入场出现 p50 `10–12.6ms`、外部约 `72–100 FPS`；`PROFILE` 首次入场另有一帧 `52.88ms`，采样栈落在同步皮肤纹理读回，后续命中缓存。隐藏 GL A/B 中，10 张卡片各自 `saveLayer` 仅比直接绘制多约 `0.28–0.32ms`，保留动画语义，没有把禁用动画当作修复。

AC 侧已修并部署三端：重复标签的 `TextBlob` 塑形缓存、消除同段文字二次测量、复用文字 Paint、缓存格式码解析、设置页 measure/draw 同帧复用行模型，以及移除头像名每帧正则。三端全量测试 Forge `1651`、Fabric `1666`、NeoForge `1651`，失败/错误 0，各 6 skip；产物级检查通过。部署回执为 `D:/AtomChat-Main/build/stage2-ac-20261105/deployment-receipt.json`。部署后用户自行启动 CCB；轻负载窗口由修复前 panel p50/p95=`2.31/8.13ms`、分配 `93.2KB` 降到 `2.14/3.47ms`、`29.0KB`，继续切页后为 `2.49/5.43ms`、`188KB`，`chat_interval` 稳定在 p50/p95=`3.32/4.73ms`。跨启动观测不作所有页面都已达标的结论，`44.20ms` 冷长尾仍在。

完整根因、源码位置、红绿证据、Skija `0.143.18` 上游核对与剩余风险见 `D:/AtomChat-Main/docs/verification/2026-11-05-panel-transition-performance.md`。旧 JFR 的导航分段和紧凑热点分别保存在本仓 `build/scope-diagnostics-20261105/analysis-103433/navigation-correlation.json` 与 `navigation-sample-hotspots.json`；临时生成的约 733MB 原始样本导出已删除，原 JFR 未动。
