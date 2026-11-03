# 背景磨砂（本地外观候选）

本能力由库持有 shader、程序和每实例区域缓冲。只在客户端渲染线程、当前 OpenGL 3.3 context、宿主批次已 flush 且 Skija pass 之外调用；不会另建 Skija context。公开 beta3 尚不包含它。

```java
var bridge = MinecraftRenderBridge.get();
var result = bridge.drawBackdrop(panelBoundsInGuiUnits, pixelsPerUnit,
    new BackdropStyle(10, 35, 1, 0xff16191f), frameId);
// 随后进入 bridge.drawFrame；SettingsShell.paint 最后一个参数传 !result.drawn()，
// 失败时由库面板材质使用自身不透明实底。不要用矩形 clear 替代圆角材料。
```

GUI 单位入口向外圆整物理区域并计算采样 padding。显式 `drawBackdrop(source, destination, physicalBounds, style, frameId)` 留给 HUD/cache 宿主，不猜“当前绑定 FBO”是否就是目标。宿主负责正确的捕获阶段、flush、目标身份及销毁前释放。

便捷入口共享 MinecraftRenderBridge 的长寿命服务，`frameId` 必须在该服务整个生命周期内单调不减，不能使用每次开屏从1开始的局部计数。样例传入本次渲染已采样的 `System.nanoTime()`；UiSession 自己的帧号仍可按屏幕从1开始。关闭再打开 Screen 不会重置共享模糊服务。

共享层可 `new BackdropRenderer()`，使用 `capture(source, paddedRegion, frameId)` 和 `draw(snapshot, destination, outputRegion, style)`。低层 capture 区域已经包含 padding；便捷 `render` 自动外扩并 clamp 到源边缘。物理输出使用整数像素区域，必须位于捕获和目标范围内。零 blurRadius 是未模糊 RGB 拷贝，opacity 零不改变颜色。

`BackdropRenderer.draw` / `render` 及三端 `MinecraftRenderBridge.drawBackdrop` 新增最后一个 `float maxLuminance` 参数的重载，范围 0–1；旧重载等价于 1。限制针对**合成前、编码 RGB 的加权亮度**（权重 0.2126 / 0.7152 / 0.0722），超过上限时按同一比例缩放三个通道；它不是线性光的物理亮度，也不保证与半透明目标混合后的最终亮度上限。宿主显式选择数值及源、目标；未为所有背景或用户视觉选择通用默认阈值。

新增 GPU 读回覆盖亮／暗、彩色四轮模糊、源 alpha、目标 alpha、区域外和第三 FBO；`hiddenGlBrokenShaderTest` 使用独立的无效 shader 资源检验编译失败返回 `SKIPPED`、目标不变，并由 `SettingsShell` 绘制不透明实底。此测试不更改正式 shader 资源。`SkiaDraw.drawBlurredBackground` 现在分别关闭画笔和该次创建的 `ImageFilter` 句柄；真实引用计数、重复绘制像素及句柄统计见对应测试。

`SkiaDraw.releaseShadowFilters()` 在渲染线程上解除共享画笔的滤镜引用并关闭缓存持有的全部阴影滤镜；可重复调用，随后绘制会重新创建滤镜。缓存是进程内共享的，不属于某个 `ControlContext` 或屏幕，只有协调方确认所有宿主暂停使用时才调用；单个宿主关闭不能自动清空其他宿主正在使用的共享缓存。该入口不关闭静态共享 Paint，GPU 长时资源和应用退出顺序仍需真实环境验收。

- v1 支持非多采样、完整 FBO 的 RGBA8 二维 color attachment 0。对象、纹理名、尺寸由 `FramebufferTarget` 提供。格式/尺寸不符和不完整目标返回诊断 `SKIPPED`，不写目标；不承诺 HDR、MSAA、任意纹理类型或第三方缓存布局。
- 原创 GLSL 为四个对角邻点等权凸平均，四轮 ping-pong，只读 RGB。合成用圆角 SDF 生成覆盖度，圆角外 discard；明确禁写目标 alpha。源 alpha 不参与权重与目标 alpha 保留是两个分别测试的合同。
- 服务绑定创建线程和 GLFW context。snapshot 是借用；下一次 capture、resize capture、invalidate/reload 或 close 使旧 token 失效。跨实例/过期 token、错误线程/context、Skija pass 内调用、非单调 frameId 和越界输出明确抛错。目标 generation 改变后须重新 capture。
- 同尺寸复用三张私有区域纹理，两个实例互不驱逐。`allocationCount()` 可诊断重建。`invalidate()` 丢弃程序/缓冲，下次惰性重建；宿主资源重载转发 `MinecraftRenderBridge.invalidate()`。`close()` 必须在原线程及 context 销毁前执行，可重复关闭；MC 便捷入口沿用 bridge 退出释放。
- shader 缺失、编译/链接失败及可诊断的私有缓冲创建失败返回 `SKIPPED`，不吞编程错或冒报成功。`fallbackColor` 是调用方选择实底的建议色；服务不在失败路径向 FBO 填矩形。样例用库的 SettingsShell 实底降级。
- 既有 `GlStateScope` 恢复 read/draw FBO、路由、viewport/scissor、program/VAO、纹理/sampler、blend/mask、depth/stencil 等状态，并补上真实红测发现的 COLOR_LOGIC_OP enable。显式源/目标的 read/draw-buffer 路由局部恢复。不虚拟化所有扩展、对象删除或入场前已失配的 Blaze3D 缓存。

隐藏 GL 检查不同源/目标/第三当前 FBO、全像素 alpha 哨兵、源 alpha 0/255、圆角外不变、连续帧、两实例、零强度同目标无反馈、resize/reload/close、线程/context、脏状态及真实行为变异。证据图是程序生成的棋盘；RGB 展示图强制不透明，alpha 单独展示。CPU 提交时间含首次惰性准备；GPU query 仅在读回后 available 时读取，无忙等或生产路径 glFinish。这不是游戏 FPS、性能验收或优于 Tuui 的证明。

依据为用户合同及 Khronos 的 [glColorMask](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glColorMask.xhtml)、[glBlitFramebuffer](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBlitFramebuffer.xhtml)、[query 读取](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glGetQueryObject.xhtml)。实施者未读取 Tuui，未搬旧 AC renderer/GLSL。Skia resetContext 使状态缓存失效，不等于每帧销毁 GPU 资源。

未运行 Minecraft、Iris/OptiFine/Gnetum、ARM64 或其他显卡。显式目标 API 允许宿主接正确阶段，但没有自动兼容所有 HUD 缓存服务。

独立热调用补测：64×64、四轮、32次，Fabric当前机器CPU提交中位约6.2ms、p95约7.2ms；完整逐端记录见证据中的 `independent-warm-timing.json`。这不含游戏场景，也没有分解状态保护、驱动调用和滤波的成本；性能尚未验收，不能仅凭GPU query时间或小区域推断整帧开销很低。
