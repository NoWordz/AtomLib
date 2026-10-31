# T3 GL 渲染桥与接线

本轮实现限于 `platforms/shared/src/**`、各平台 `client/render/**` 和新脚本 `gradle/render-adapter.gradle`。后续已补 `runtime/image/BackdropBlur.java`：它只消费宿主捕获的 `Image`，在目标裁剪区内做 Skija blur；空图/零 sigma 用不透明实底回退。它不读取默认 framebuffer，也不拥有 DirectContext。

## 来源与边界

代码按官方 API 独立编写，没有复制 AC 已排除的 GlStateUtil、SkiaGraphics、blur 或 Tuui 代码。实际签名以本机锁定的 Skija 0.143.18、LWJGL 3.3.1/3.3.3 与三个目标映射编译结果核对。设计参考：

- [Khronos glGet](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glGet.xhtml)、[glBindFramebuffer](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBindFramebuffer.xhtml)、[glPixelStore](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glPixelStore.xhtml)。
- [Skija DirectContext](https://github.com/HumbleUI/Skija/blob/master/shared/java/DirectContext.java)、[Surface](https://github.com/HumbleUI/Skija/blob/master/shared/java/Surface.java)、[BackendRenderTarget](https://github.com/HumbleUI/Skija/blob/master/shared/java/BackendRenderTarget.java) 的公开资源包装、flush/reset 和生命周期语义；代码使用的是实际 0.143.18 产物签名。
- [GLFW 隐藏窗口与 context](https://www.glfw.org/docs/3.3/window_guide.html)、[Fabric 1.21.1 Yarn Framebuffer](https://maven.fabricmc.net/docs/yarn-1.21.1%2Bbuild.3/net/minecraft/client/gl/Framebuffer.html)。Forge/NF 的 RenderTarget 字段通过目标映射的实际 compileJava 核对。

## 文件职责

| 文件 | 职责 |
|---|---|
| `platforms/shared/src/main/java/com/atom/lib/client/render/GlStateScope.java` | 成对保存/恢复 GL 驱动状态，支持独立嵌套快照及 LIFO/thread/context 检查 |
| 同目录 `FramebufferTarget.java` | 宿主 FBO 对象身份、FBO 名、color texture 名及物理像素尺寸 |
| 同目录 `SkijaRenderBridge.java` | 唯一 context 所有权、Surface 重建、RenderSession/flush 和 vanilla pass 边界 |
| 同目录 `BorrowedFrame.java`、`BorrowedFramePainter.java` | 同步回调内借用 context/backend/surface，不转移所有权 |
| 各平台 `src/main/java/com/atom/lib/client/render/MinecraftRenderBridge.java` | official/Yarn 的薄适配、渲染线程断言、显式客户端懒加载入口 |
| `platforms/shared/src/test/java/com/atom/lib/client/render/*Test.java`、`HiddenGlFixture.java` | 真实隐藏 GLFW context、GPU 绘制、读回与状态回归 |
| `gradle/render-adapter.gradle` | 共享 source sets、compileOnly GL 依赖和独立 hiddenGlTest |

## 状态与生命周期契约

GlStateScope 使用 LWJGL 原始 GL 调用，完全不调用 RenderSystem/GlStateManager 的缓存修改或失效方法。前提是宿主入场时驱动状态与 Blaze3D 缓存一致，回调里只做 Skija 绘制；退出时恢复驱动，原有缓存保持不变。它不修复入场前已失配的缓存，也不回滚宿主对象的删除、buffer 内容、texture 参数或 FBO attachment 修改。

状态范围包含：

- 独立 read/draw FBO、read/draw buffer、renderbuffer、viewport/scissor。
- blend 开关、RGB/alpha 函数及 equation、blend color；逐 draw-buffer enable/color mask，并在驱动支持时恢复逐 buffer 的 blend functions/equations。
- depth test/function/write mask/range，双面 stencil function/ref/mask/op，cull/front face、polygon mode/offset、color mask、clear 值。
- program、可用时的 program pipeline、VAO/EBO、array/copy/PACK/UNPACK/uniform/transform-feedback buffer 绑定及 indexed UBO 范围。
- 所有 combined texture units 的核心 GL 3.2 texture targets 和可用时的 sampler 绑定，最后恢复 active texture。
- PACK/UNPACK alignment、row length、image height、skip rows/pixels/images、swap/LSB，以及 PBO；multisample/sample mask/coverage、clip distances、sRGB 等 enable 状态。

每次 Skija pass 先保存状态，再清除 PBO 和特殊 pixel-store 设置，避免 JNI 图像指针被解释为 buffer 偏移。然后 reset Skija 的 GL 缓存；绘制与异常路径都 flush/submit，最后恢复宿主状态。scope 本身不读取/吞掉 GL error；测试显式断言错误为 GL_NO_ERROR。

首版选择保守的完整 texture-unit 快照，尚未做帧耗时优化。不能把本轮正确性测试等同于性能验收或所有 OpenGL 扩展状态的完整虚拟化。

SkijaRenderBridge 在创建线程使用；第一次 pass 才创建 DirectContext。同一 GLFW context 同时只能被一个 AtomLib bridge 持有。对象身份、FBO 名、color attachment 名或尺寸变化会关闭旧 RenderSession/Surface/backend 并重建。若宿主删除后复用了相同 GL 名，必须显式 `invalidate()`。关闭顺序是 session → Surface → backend → DirectContext，必须在原线程、原 context 仍 current 时调用。

首版只接受完整的、color attachment 为 RGBA8 2D texture 的非默认 FBO。无 stencil FBO 已验证。其他格式/尺寸不符会明确抛错；尚不支持 HDR、multisample texture、默认窗口 framebuffer 或 shader mod 替换格式。不会替宿主删除 FBO/texture，不会关闭宿主 RuntimeHandle。

## 主代理永久接线

在三个 `platforms/<目标>/build.gradle` 的 module-layout apply 之后增加：

    apply from: '../../gradle/render-adapter.gradle'

本 worker 没有修改这三个 build 文件。脚本有一次性 apply 守卫；只增加 main/shared 源码和独立 hiddenGlTest source set。GLFW/OpenGL/lwjgl-core 是 compileOnly/测试依赖，不进玩家 JarJar/include 或 Maven API。测试 native 根据宿主 OS/JVM 架构选择；当前实际运行范围只有 Windows x64。

未永久 apply 前，使用本轮新增的测试 init 脚本临时接线：

    gradlew.bat :compileJava :hiddenGlTest --init-script D:/AtomLib/platforms/shared/src/test/gradle/render-test.init.gradle --no-daemon --console=plain

三个平台分别用自己的 wrapper、JDK17/21/21 执行；各自的模块与测试输出仍在该目标的 build 目录，不触碰 root standalone 输出。JUnit 的锁定版本从 dependencyVersions 读取，测试仓库限定到 Maven Central，避免 NeoForge 仓库的不可达请求拖累 JUnit 解析。

## 客户端 API

客户端明确调用 `MinecraftRenderBridge.get()`，不会由 common 入口或 Fabric client entrypoint 自动创建 native。普通接入为：

    var bridge = MinecraftRenderBridge.get();
    bridge.draw(canvas -> { /* Skija UI，坐标为 framebuffer 物理像素 */ });
    bridge.vanillaPass(() -> { /* 原版绘制，并在结束前 flush 宿主批次 */ });
    bridge.draw(canvas -> { /* 后续 Skija 层 */ });

宿主在进入 Skija 前必须先 flush 自己尚未提交的 GuiGraphics/DrawContext/vertex batches。原版绘制只能位于 Skija pass 之间；callback 内不能调用 Minecraft GUI、GlStateManager/RenderSystem 状态修改、切换 context 或改变宿主 GL 对象。`vanillaPass`、递归 draw、prepare、withFrameAccess、invalidate、close 在活动 Skija pass 内均拒绝。直接绕开这些 API 调宿主原版绘制的行为无法由 Java callback 自动拦截，属于调用方违约。

Canvas 为 Surface 所借出的对象，由 runtime RenderSession 负责 save/restore。消费者不得 close Canvas，也不得 restore 到 callback 入场 save count 以下。

## AC 合法 raw Skija 消费者的迁移

提供以下显式借用 API，三端 MinecraftRenderBridge 和共享 SkijaRenderBridge 都有对应入口，后者多一个 FramebufferTarget 参数：

| API | 用途 |
|---|---|
| `prepare()` | 确保当前 FBO/尺寸已准备，不返回裸句柄 |
| `drawFrame((canvas, frame) -> ...)` | 同一 pass 内同时借用 Canvas 与 frame |
| `<T> withFrameAccess(Function<BorrowedFrame,T>)` | 在完整状态/scope/pass 边界内做同步原始 Skija 操作 |
| `Image captureFrame()` | 返回调用方拥有的新 snapshot Image；由调用方在渲染线程关闭 |
| `<T> withContext(Function<DirectContext,T>)` | pass 外同步租借唯一 context；用于宿主合法 raw resource prepare，不保存句柄 |

BorrowedFrame 公开 `context()`、`backendRenderTarget()`、`surface()`。这些对象始终属于 Lib，不得 close/abandon/重新配置，也不得在 callback 结束后继续操作。借用入口在错误线程/context 或 callback 结束后抛错；已取出的原始 Skija 引用无法由 Java 撤回，因此“不保存、不转移、不关闭”仍是调用方契约。plain 计算结果和显式创建的自有 Image 可以作为返回值。

建议 AC 的 SkiaGraphics 变成薄 adapter，管理自己的 worldSnapshot Image 和当前 active BorrowedFrame：

    // checkFrameBufferId/createSurface 的兼容入口：
    bridge.prepare();
    // AC 自己决定捕获时机、替换与 releaseWorldSnapshot：
    Image nextWorldSnapshot = bridge.captureFrame();
    bridge.vanillaPass(preUi);
    bridge.drawFrame((canvas, frame) -> {
        activeFrame = frame;
        try {
            canvas.scale(density, density);
            oldDrawCallback.accept(canvas, worldSnapshot);
        } finally {
            activeFrame = null;
        }
    });

此时 adapter 的 requireContext/requireRenderTarget/requireSurface 只从 activeFrame 委托，供 ItemIconRenderer 的 Skija texture 导入及 chatEffects 的 Surface 操作使用；不能另建 DirectContext 或自行关闭 Lib 的三个句柄。若旧调用发生在 pass 外，应把那段原始 Skija 操作整体放入 withFrameAccess，不能取出一个 context 后在外面继续执行 GPU 操作。原版 item 渲染准备与 native 导入也必须按 vanilla/Skija pass 分开。

world snapshot 的业务失效、持有时长、切换世界释放仍由 AC adapter 管；releaseWorldSnapshot 只关闭自己拥有的 Image。prepare/captureFrame/withFrameAccess 都不能从已有绘制 callback 再次调用。中央 MinecraftRenderBridge.close 用于 context 仍有效的最终关闭，不应在一个普通页面 close 时销毁其他宿主共用的绘制 context。

## 客户端退出释放（2026-10-30 追加）

共享 bridge 的生命周期闭合在三端各接了一次，页面 close 仍不销毁 context：

| 目标 | 触发点 | 实现 |
|---|---|---|
| Forge 1.20.1 | `GameShuttingDownEvent`（`Minecraft#stop()`，渲染线程，窗口销毁前） | `AtomLibMod` 用 `FMLEnvironment.dist.isClient()` 守卫后交给 client-only `AtomLibClient` |
| NeoForge 1.21.1 | 同上（`GameShuttingDownEvent`） | 同上，走 `NeoForge.EVENT_BUS` |
| Fabric 1.21.1 | `MinecraftClient#close()` HEAD | `atomlib.mixins.json` 的 client mixin，refmap `atomlib.refmap.json` |

三端都调用新增的 `MinecraftRenderBridge.releaseIfOpen()`：只有在 `instance` 已存在时才 close，从不为了退出而 `get()` 出一个新 bridge；关闭异常会记 WARNING 而不打断退出。专服不加载这些类（dist 守卫 / client-only entrypoint）。Fabric refmap 已实测包含 `class_310;close()V`；**尚未在真实客户端退出路径上运行验证**。

### AC 消费者的两阶段拆分

`ItemIconRenderer.prepare` 不再取出 context：原版物品绘制放在 `vanillaPass` 内的 `drawOffscreen`（宿主 FBO + 原始 GL），Skija texture 导入放在紧随其后的 `bridge.withContext(context -> ...)` 里，提交/关闭缓存在前一个同类型租借内完成。`SkiaGraphics.requireContext()` 已删除，改为唯一入口 `SkiaGraphics.withContext(Function)`，句柄不出回调。

## pixels-per-unit 契约（2026-10-31 追加，行为实测）

`YogaLayoutEngine` 把 `Viewport.pixelsPerUnit` 原样映射到 `YGConfigSetPointScaleFactor`，每次 `layout()` 前设置。Yoga 的 point scale factor 会把**每个节点的布局结果吸附到 1/pointScaleFactor 的网格上**（Yoga 按 `floor(v*scale+0.5)/scale` 取整）。实测（`platforms/shared/src/test/java/com/atom/lib/layout/YogaRescaleHiddenGlTest.java`，三平台 hiddenGlTest）：

- `pixelsPerUnit=1` 时，100.4u 的根节点结果为 100u，50.5u 的子节点为 51u——小数布局在 1u 网格上必然漂移，最大半格。
- `pixelsPerUnit=16` 时同样两个值分别输出 100.375u 与 50.5u——1/16 网格保留了 AtomChat 纯 float 布局的实际取值（复刻夹具的 tile 几何在 16 档逐值相等）。
- Yoga 对纯容器树会复用旧 scale 的缓存结果：换 `pixelsPerUnit` 重排必须整树标脏（`YGNodeMarkDirty` 只接受带 measure 回调的叶子，树级入口 `YGNodeMarkDirtyAndPropagateToDescendants` 在 LWJGL 3.3.1/3.3.3 拼写不同，适配器按目标生成桥接，见 `ui/build.gradle` 的 `generateYogaDirty`）。该往返已被回归覆盖，宿主在同一 engine 上逐帧改 `pixelsPerUnit` 是受支持路径。
- pointScaleFactor=0（Yoga 语义上"关闭取整"）经本适配器**不可达**：`Viewport` 构造校验 `pixelsPerUnit > 0`（`core/src/main/java/com/atom/lib/geometry/Viewport.java`）。即 AtomLib 的 Yoga 布局永远有网格吸附，没有"纯 float"档位。

宿主义务：

1. **永远传真实物理像素比**（MC：framebuffer 像素宽 / GUI scale），不要图省事传 1。传 1 意味着接受整格吸附；要精确小数布局就传大 scale（如 16，代价只是 Yoga 取整计算，不改变测量回调次数语义——标脏后仍会重测）。
2. 一帧内保持 `pixelsPerUnit` 稳定；密度变化只允许发生在两次 `layout()` 之间。
3. 坐标系不变：viewport 与 measure 回调都以"unit"为单位，pixelsPerUnit 只影响 Yoga 输出吸附到哪一档网格，不做单位换算。

背景：AtomChat 自身布局是纯 float、从不吸附，1u 网格下复刻 settings 首页 tile 偏差最大 1.25u（`docs/evidence/settings-home-replication/report.md` 第 1 条）。这是取整策略分歧，不是表达力缺口。

## 实际验证

2026-10-30 最后完整运行：

| 目标 | compileJava | hiddenGlTest | 日志 |
|---|---|---|---|
| Forge 1.20.1 / Java17 / LWJGL3.3.1 | 通过 | 11 tests，0 failures/errors/skips | `platforms/1.20.1-forge/build/render-green.log` |
| Fabric 1.21.1 / Java21 / LWJGL3.3.3 | 通过 | 11 tests，0 failures/errors/skips | `platforms/1.21.1-fabric/build/render-green.log` |
| NeoForge 1.21.1 / Java21 / LWJGL3.3.3 | 通过 | 11 tests，0 failures/errors/skips | `platforms/1.21.1-neoforge/build/render-green.log` |

JUnit XML 位于各目标 `build/test-results/hiddenGlTest`，HTML 在 `build/reports/tests/hiddenGlTest/index.html`。共 33 个实际执行的测试，没有因缺显卡跳过。上下文为真实 GLFW 隐藏窗口（同时断言 GLFW_VISIBLE=false），本机 NVIDIA GeForce RTX 5060 Laptop GPU，OpenGL 3.3.0 NVIDIA 617.42。

覆盖状态往返/异常恢复、独立 scope LIFO/线程/真实 context 检查、逐 draw-buffer 状态、Skija→原版 GL→Skija 的像素读回、同尺寸新 FBO 与 resize、无 stencil、坏 attachment 后恢复、PBO/pixel-store 存在时的真实图片 GPU 上传、借用失效、borrow callback 异常、snapshot Image 所有权，以及关闭后另一个 bridge 重新获得同一 context。

先红后绿证据保留于 Forge 的 `render-red.log`（初始 5 tests 均按预期失败）、`render-indexed-red.log`（逐 buffer 状态回归失败）、`render-borrow-red.log`（2 个借用/capture 测试失败）。编译修正的中间失败和早期 NeoForge 仓库超时日志也保留，没有把这些失败算作通过。

本 worker 未启动 Minecraft。ARM64 实机、Iris/OptiFine、MC GUI/物品/tooltip 实际混排、真实 context 销毁回收、其他显卡及性能仍未覆盖，用户实机验收和主代理接线后回归负责这些边界。HDR/多采样目标支持仍不在本轮交付内。
