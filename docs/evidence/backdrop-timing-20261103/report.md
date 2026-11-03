# 背景模糊分段探针复核（2026-11-03）

## 测量配置修复

`BackdropTimingGlTest` 首版将 `BackdropStyle(4, blur, 1, 0)` 标为“无滤波／四轮滤波”，但构造器的首参是模糊半径，次参是圆角半径：两组实际上均执行半径 4 的模糊，只改变圆角。**首版标为 `no_filter` / `four_filter_passes` 的数据不能用于比较有无模糊。**

主代理增加真实 GL 像素回归：黑色背景上的红色方块在两种配置下取相同边缘像素；首版两种输出相同，测试失败。改为 `BackdropStyle(blur, 4, 1, 0)` 后像素不同，测试通过。此修订只改诊断测试，不更改生产渲染路径。

## 修正后的单次离屏样本

NeoForge 1.21.1，Windows x64，NVIDIA GeForce RTX 5060 Laptop GPU，OpenGL 3.3.0 NVIDIA 617.42；128×128 区域，三轮，各模式每轮预热 8 次、采样 16 次，固定先无滤波再四轮滤波。原始数据及驱动信息在同目录 `backdrop-stage-round-{0,1,2}.csv` 和 `backdrop-stage-metadata.txt`。

| 样本中位数 | 三轮范围 |
| --- | --- |
| 独立 `GlStateScope.capture` CPU | 2.05–2.41 ms |
| 独立 `GlStateScope.close` CPU | 0.025–0.040 ms |
| 无滤波 `BackdropRenderer.draw` CPU | 2.17–2.53 ms |
| 四轮滤波 `BackdropRenderer.draw` CPU | 2.37–2.60 ms |
| GPU 查询，无滤波 | 0.0034–0.0040 ms |
| GPU 查询，四轮滤波 | 0.0130–0.0136 ms |

`capture`、`draw` 内部各包含 GL 状态保护；它们不是独立滤波、合成或 GPU 耗时，不能与独立 scope 样本相加。GPU query 覆盖整个 draw 的 GPU 命令而非单独滤波，逐条仅查询一次不等待，每组仅 13–14 / 16 个查询在检查时就绪；固定模式顺序、小样本及系统负载均可能影响结论。该探针未启动 Minecraft，不代表真实游戏 FPS、其他显卡或用户当前实例的结果。

旧的独立 scope 测量见 [物品图标诊断](../item-icon-diagnostics/report.md)，也不是 AtomChat 面板的实际帧成本。后续应在真实场景测捕获、状态保护、绘制及缓存重建，并保留图像、状态污染、多宿主和运动背景回归，再决定能否安全优化。
