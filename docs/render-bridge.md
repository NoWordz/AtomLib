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
