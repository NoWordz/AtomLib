# AtomLib 可组合模态与原版物品图标方案

> 状态：2026-11-02 阶段收口。用户要求“先到这吧，更新下方案，然后提交，清理临时文件”；代码与离线验证候选已复核，暂不继续新增功能或性能优化。没有活跃子代理。本轮授权包含本地 Git 提交，不含推送、发布、部署或启动游戏。
> 阶段进度：M1 可组合模态与 CreatorTrial CPU 接线已完成并独立复核，见 `docs/evidence/composable-modal-candidate/review.md`。M2 图像槽、三端离屏适配与薄 Screen/F8 入口已形成代码候选并通过限定范围复核，见 `docs/evidence/modal-item-icons/review.md`；实际 Minecraft 图标显示未运行，性能尚未验收。
> 执行者按当前会话约定逐项实施；最多一个子代理，沿用用户指定的 Terra 6.1 / xhigh，主代理独立复核。本文不引入其他协作工作流。

**Goal:** 让创作者只组合 AtomLib 部件和业务数据，就能做出带真实 Minecraft 物品图标的可滚动选择弹窗。

**Architecture:** 分两个可单独验收的阶段：先把固定确认窗之外的可组合内容接入同一模态生命周期，再补库拥有的原版物品离屏绘制与 Skija 图像合成。沿用现有控件、Yoga、PresentationFrame 和 MinecraftRenderBridge；选择、过滤、提交属于宿主业务。

**Tech Stack:** 现有 Java / Skija / Yoga；库公共模块保持 Java 17，Fabric 1.21.1 消费工程使用 Java 21；覆盖 Forge 1.20.1、Fabric 1.21.1、NeoForge 1.21.1。

## 阶段结论与下次入口

| 项目 | 当前结论 |
| --- | --- |
| M1 可组合模态 | 已实现并独立复核；旧确认窗兼容、焦点/滚动/尾事件/异常回归通过。 |
| M2 图像槽、离屏资源、三端原生适配 | 代码、CPU/纯 GL 与离线编译候选已复核；实际原生模型尚未运行。 |
| 独立消费者 | 薄 Screen/F8/专用 mixin 已编译，9 组 JavaExec 场景通过；游戏启动与输入未验收。 |
| 独立重跑 | UI 97 项，Fabric 图标相关 7 项，消费者 9 组；三端各 111 份源码与候选一致。其余三端完整 GL/验包/六消费者见实施者记录，不混称本轮独立重跑。 |
| 性能 | 纯 GL prepare+close 热 CPU 中位约 9.43/10.41/9.78ms，尚未验收；旧模糊热提交约 6.2ms 也未拆因。 |
| 发行 | 公开 beta3、版本及 AC 输入不变；本次是本地阶段提交。 |

当前候选为 `build/modal-item-icons-candidate/maven`，M1 与更早候选保留。机器摘要、纯 GL 图案/时间数据与消费者源码快照保存到 `docs/evidence/modal-item-icons/`；这次保存的源码快照为本地消费者复现资料，不把本机绝对路径宣称为可移植下载配置。

下次恢复时的顺序：

1. 先分段定位图标 prepare/导入/关闭与完整 GL 状态保护的 CPU 成本；完整状态保护只是候选成本，不能先认定唯一根因再删保护。
2. 需要启动游戏时，先取得新的明确授权，再测真实模型/附魔/动态上下文、F8 输入、reload/shutdown 与 RenderSystem 恢复；CPU/纯 GL 不代替这层。
3. 根据结果决定修复和可用性验收，再讨论发版；不继续 AC 像素级微调。

下文设计依据及规格保留为批准时历史。勾选项表示已完成的代码或注明范围的自动验证；实际原生绘制与恢复验收仍保持未完成。

## 1. 依据和本轮边界

实施前基线（历史）：

- `D:/AtomLib-CreatorTrial/REVIEW.md`：分类实例重建的 P2 已修，正式连续键盘回归和主代理独立重跑七组 CPU 场景通过。这是样例接线问题，不再计为库缺口。
- `ui/src/main/java/com/atom/lib/controls/UiSession.java`：目前 `open`、`ModalState`、模态命中与键盘通道专用于 `ConfirmationDialog` 的固定动作；`wheel` 在有模态时直接吞掉，没有模态内容滚动通道。
- `ui/src/main/java/com/atom/lib/design/atomchat/SettingsShell.java`：确认窗已有超视口等比适配，但不是可组合长内容弹窗。
- `platforms/shared/src/test/java/com/atom/lib/client/render/SkijaRenderBridgeTest.java` 的 `hostOffscreenTextureImportsInsideTheBorrowAfterAPollutedVanillaPass`：已有“离屏纹理→共享 context 借用图像→Skija 合成”的 GL 先例；它用纯色测试纹理，不证明实际 Minecraft 物品绘制已可用。

实施前试用工程为 **CPU 非模态 PARTIAL / BLOCKED**，当时没有 Screen 或游戏内打开入口；此历史阻塞已有代码候选补齐，当前验收范围以上方阶段结论为准。

全局约束：

- AC 保持只读，不等 AC 收尾，也不迁移 AC。
- 不再追求 AC 像素级复刻；这次验收的是创作者是否能完成实际交互。
- 保留所有既有 WIP、候选 Maven、输入锁和历史证据。公开 beta3、版本号、既有坐标不变。
- 不自动启动游戏、下载客户端或游戏资源、部署、推送、发布；2026-11-02 收口新增授权仅允许本地提交。
- 试用侧只写模型、业务动作、部件组合和薄 Screen 适配；不能自写模态隔离、命中、焦点、滚动、物品渲染或 GL 桥。
- 不加多层模态栈、路由、DSL、虚拟列表、聊天编辑器、IME 或背包操作系统。24 条物品记录足够验证本切片。
- 本轮规划读取了库实现，后续仍称“独立消费者验收”，不称严格创作者盲测。原试用的 Loom 自动处理 sources 事件继续披露。

## 2. 决策与推荐

| 决策 | 选项 | 推荐及理由 |
| --- | --- | --- |
| 模态范围 | 继续给确认窗塞专用槽位 / 一个可组合模态容器 / 多层弹窗系统 | 一个可组合容器。确认窗继续可用，分类、列表、页脚不必伪装成确认文案。 |
| 模态交互 | 新写一套动作状态机 / 复用普通控件会话 | 内部内容会话复用现有 Control、InputDispatcher、FocusState 和 ScrollViewport；根会话只做隔离与生命周期。 |
| 模态长内容 | 整窗缩到能装下 / 固定头尾、正文滚动 | 新容器固定头尾、正文滚动；旧确认窗维持现有适配契约。 |
| 物品图标绘制 | 最后补原版绘制 / 每个图标独立切换渲染器 / 一批离屏原版图标转为 Skija 图像 | 先验证批量离屏路线。图标跟随控件在同一 Skija 层绘制，继承裁剪、缩放、透明度和模态遮挡。 |
| 图标缓存 | 首版长期缓存 / 每帧绘制可见项、复用存储 | 首版不缓存图标内容，只复用离屏目标与分配。避免错误冻结时钟、指南针、附魔效果或组件差异；性能有账本后再决定。 |

物品离屏验证若不能可靠接入某目标平台，应记录具体失败并停在该阶段，不能悄悄退成假图标、复制贴图或让宿主补 GL。不能因已有纯色 GL 测试就跳过这一步。

## 3. M1：可组合模态

### 3.1 拟新增公开面

以下均为拟定接口，不是当前已存在 API。类位于 `com.atom.lib.controls`：

```java
public record ModalOptions(String title, float preferredWidth, float margin,
                           boolean dismissOnBackdrop, NodeId initialFocus) {}

public enum ModalCloseReason {
    COMPLETED, CANCELLED, HOST_CLOSED, FOCUS_LOST, COORDINATE_SPACE_CHANGED
}

public final class ModalDialog {
    public ModalDialog(NodeId id, ControlContext context, ModalOptions options);
    public void navigation(SegmentedChoice navigation); // 可选，不设置则无导航
    public void content(LayoutNode body, List<? extends Control> controls);
    public void actions(List<ActionButton> actions);
}

// UiSession 的增量接口：
public void open(ModalDialog dialog, Consumer<ModalCloseReason> onClosed);
public void closeModal(ModalCloseReason reason);
public PresentationFrame modalFrame(Viewport viewport); // 无模态时返回 null
```

`ModalDialog` 是稳定的内容定义，不继承 sealed Control；内部模态内容会话拥有普通控件。`content` 只能在 UI 线程更新，输入列表做不可变快照；控件实例保持稳定并更新模型。导航、正文、页脚共同进行 ID/角色槽位冲突检查，禁止与底页重复，禁止同一控件同时归属两个会话。

首版只允许一个活动或退场中的模态；第二次 open 明确抛错。业务确认按钮先写入宿主结果，再调用 `closeModal(COMPLETED)`；关闭回调只报告原因，不在库里引入物品 ID、泛型结果系统或业务存储。无模态时 `closeModal` 幂等。

为 `ControlSkin` 增加具名的容器表面布局/绘制入口；新增入口提供默认“不支持该能力”的清晰异常，现有皮肤实现继续编译，使用可组合模态时才要求支持。AtomChat 皮肤负责现有圆角、标题和面板材质；通用会话不引用 AC token。容器正文和动作仍由各自控件皮肤绘制。

### 3.2 几何和兼容

- 模态布局由库内 `ModalLayout` 负责，头部、可选导航、页脚在有限视口里量测，剩余正文空间交给一个独立 ScrollViewport。底页和模态分别持有自己的 offset。
- 宽度取首选值与视口可用宽度的较小者；边距随极小视口限界。长正文只滚正文，不把 24 行一起缩小。若头尾本身就高于视口，库将整个内容区作为单一滚动区，保证最后动作仍可达。
- 发布的绘制、命中、裁剪、Tab 自动滚动均读同一个 PresentationFrame。底页、遮罩、模态表面、模态子控件依次合成；没有第二套宿主坐标。
- `modalFrame` 由库拥有的布局器生成，供普通宿主与 SettingsShell 共用。内部内容会话复用普通控件能力，但不允许再开模态；这是同一 Canvas 上的有序绘制，不递归调用 MinecraftRenderBridge。
- 保留 `open(ConfirmationDialog, Consumer<ConfirmationResult>)`、旧模型、角色 ID、取消结果和现有确认窗布局。提取复用代码时，以旧确认窗测试/图片检查兼容，不同时改其外观。
- 旧 `modalNode(Viewport)` 保留给现有确认窗消费者；遇到新可组合模态时抛出说明需改用 `modalFrame` 的异常，不能静默返回 null。新文档与 SettingsShell 统一走 `modalFrame`。

### 3.3 输入与关闭合同

| 触发 | 必须发生 |
| --- | --- |
| open | 取消底页未完成事务、保存底页焦点、隔离当前 held-key / held-button 尾事件；首个成功绘制前不激活模态子控件。 |
| 模态期间 | 指针、滚轮、字符、Tab/Shift+Tab、Enter/Space、Escape 均由根隔离；不能漏给底页。正文滚轮只改变模态 offset，Tab 可滚到不可见末项。 |
| 空白与遮罩 | 窗体内空白仅吞事件；只有窗外遮罩且启用 dismissOnBackdrop 时才取消。 |
| 焦点 | initialFocus 有效则选它，否则按已发布顺序选首个可用控件；Tab 正反循环只在模态内，禁用项跳过。 |
| Escape / 取消按钮 | 只回调一次 CANCELLED；按住 Escape 的 repeat 与 release 不关闭底页。 |
| 关闭动画 | 仍吞所有底页输入；不得在回调后立刻解锁下层。退场完成后恢复保存的有效底页焦点，无效时选首个有效目标。 |
| 普通宽高改变 | 保持打开、重新量测并限界滚动；活动拖动按坐标变更取消。 |
| GUI density / 坐标尺度改变 | 取消交互并以 COORDINATE_SPACE_CHANGED 关闭，沿用现有会话的坐标取消原则。 |
| 窗口失焦 / 宿主关闭 | 分别以 FOCUS_LOST / HOST_CLOSED 结束；清理后不保留捕获、owner 或丢失 keyup 的物理按键记录。 |
| 回调抛异常 | 生命周期已标记结束，不能重复回调；保留异常传播并完成 owner/捕获/资源清理。关闭回调内重开在旧模态退场完成前仍被拒绝。 |
| 绘制失败 | 不发布这一帧的新命中或图标几何；保持上一成功快照，Canvas save 栈恢复。 |

### 3.4 文件与实施任务

新增：

- `ui/src/main/java/com/atom/lib/controls/{ModalDialog,ModalOptions,ModalCloseReason}.java`：公开内容定义与关闭语义。
- `ui/src/main/java/com/atom/lib/controls/internal/ModalLayout.java`：有限视口量测及正文滚动的几何生成。
- `ui/src/test/java/com/atom/lib/controls/ComposableModalTest.java`、`ComposableModalRenderTest.java`：交互与 CPU 绘制回归。

修改：

- `UiSession.java`、`internal/ModalState.java`：通用生命周期、内部内容会话、隔离与原确认窗兼容；不复制 ActionButton/Slider 状态机。
- `ControlSkin.java`、`design/atomchat/AtomChatControlSkin.java`：容器表面支持；按需新增独立皮肤模型文件，不把具体 UI 规格塞进会话。
- `design/atomchat/SettingsShell.java`：消费 `modalFrame`，移除本类重复的模态布局与 fit 计算，保留现有页壳布局。
- `ControlFixture.java`：show 改为合成 `modalFrame`，旧确认测试继续走同一真实发布路径。
- `docs/controls.md`、`docs/api-contracts.md`、`docs/quickstart.md`：稳定实例、组合、布局发布及关闭示例。

- [x] 先新增行为测试，旧实现应因没有可组合入口或隔离不满足而失败；不能用反射检查字段替代真实输入。
- [x] 增加单子按钮场景，直接使用现有 fixture 和拟新增接口：

```java
try (var f = new ControlFixture()) {
    var invoked = new java.util.concurrent.atomic.AtomicInteger();
    var action = new ActionButton(new NodeId("pick/stone"), f.context,
        new ActionModel("石头", true, ActionModel.Role.NORMAL), invoked::incrementAndGet);
    var dialog = new ModalDialog(new NodeId("pick"), f.context,
        new ModalOptions("选择物品", 640, 16, true, action.id()));
    dialog.content(action.node(LayoutStyle.column()), List.of(action));
    dialog.actions(List.of());
    f.session.open(dialog, reason -> {});
    f.show(480);
    f.key(KeyEvent.Key.ENTER, true); f.key(KeyEvent.Key.ENTER, false);
    action.update(new ActionModel("石头", true, ActionModel.Role.NORMAL));
    f.show(480);
    f.key(KeyEvent.Key.ENTER, true); f.key(KeyEvent.Key.ENTER, false);
    org.junit.jupiter.api.Assertions.assertEquals(2, invoked.get());
}
```

- [x] 再覆盖 24 项、禁用确认、重复 Enter、正反 Tab、独立滚动末项、遮罩/卡内空白、关闭尾事件、回调异常、失败 paint 不发布，以及两个会话不串状态。
- [x] 实现最小公开面与内部组合；让新行为测试和旧确认窗、slider、focus、scroll 回归一起通过。
- [x] 用默认皮肤之外的现有测试皮肤补一个新模态案例，证明通用会话不靠 AC 尺寸工作。
- [x] 先产出独立 M1 候选并接入 CreatorTrial：此时可以验收完整模态交互，但真实物品图标继续明确 BLOCKED。

## 4. M2：原版物品图标

### 4.1 所有权与拟定 API

平台适配接收该平台原生 `ItemStack`，保留数量、组件/NBT 及真实渲染语义；公共 core/runtime/ui 不引入 `net.minecraft`。不把图标当内置字体字符，也不按物品 ID 去抓一张静态贴图。

拟新增 `com.atom.lib.controls.ControlIcon`，仅为控件的非交互图像装饰：

```java
@FunctionalInterface
public interface ControlIcon {
    void paint(io.github.humbleui.skija.Canvas canvas,
               com.atom.lib.geometry.Rect bounds, float opacity);
}

// ActionButton 增量接口；size 是逻辑 UI 单位：
// null + 正尺寸预留空图标槽，null + 0 清除图标及占位。
public void icon(ControlIcon icon, float size);
```

`ActionButton` 为图标分配保留的 `/icon` 槽位，文字和图标分别量测；点击仍命中整行，图标不增加焦点。皮肤使用该槽位，尺寸与裁剪来自同一 PresentationFrame。保留现有 ActionModel 构造器和字符串 icon 语义，不改变旧按钮宽度。新图像槽与旧字符串 icon 同时使用时明确拒绝，避免两个来源抢同一空间。正尺寸且内容为 null 的预留槽不绘制像素；同尺寸换句柄不需要重新量测，尺寸变更必须重新布局。

各目标平台新增同名 `com.atom.lib.client.render.item.MinecraftItemIcons` 与 `ItemIconRequest`；ItemStack import 随目标映射变化。共享层的 `ItemIconFrame` 不暴露 MC 类型：

```java
// 由每个平台的 MinecraftRenderBridge 拥有，get 不新建第二个 DirectContext：
public MinecraftItemIcons itemIcons();

// 平台类 ItemIconRequest：String key、原生 ItemStack stack、int pixelSize。
// MinecraftItemIcons 方法：
public ItemIconFrame prepare(List<ItemIconRequest> requests, long frameId);

// ItemIconFrame：有效期限于本次同步渲染，使用后释放借用：
public ControlIcon icon(String key);
public void close(); // AutoCloseable，不销毁可复用离屏存储
```

实际放置时 `ControlIcon` 在 ui 模块，`ItemIconFrame` 在平台 shared 源目录，平台适配仅依赖已有 ui；不得让 ui 反向依赖平台代码。`ItemIconRequest` 创建时复制 ItemStack，拒绝空 key、重复 key、非正尺寸；空 stack 返回明确空图标。传入的尺寸由 Lib 提供的逻辑尺寸→物理像素换算示例产生，不让宿主猜缩放。

准备请求的示例数量为当前帧正文可见项及必要边缘项，依照当前布局产生的 PresentationFrame 判断；不能用上一帧 hit 快照选择而造成滚动一帧空洞。每项先创建稳定 ActionButton 并预留图标尺寸，量测后 prepare，再将本帧 icon 句柄放回原按钮；绑定图像句柄不改变这次已量测的尺寸。

### 4.2 必须先通过的原型

- [x] Forge/Fabric/NeoForge 分别确认原版 GUI 物品绘制所需的目标 FBO、投影、深度和批次提交路径。实际 API 以各目标已解析的映射源码/签名为准，不从一个平台猜另两个平台。
- [ ] 在一个库拥有的带深度离屏目标上批量画可见项；进入前刷新宿主批次，使用原版 GUI 物品绘制，完全提交后才能导入纹理。
- [ ] 明确恢复 GL 状态和 RenderSystem/GUI 全局状态；仅有 GlStateScope 不等于原版渲染的所有全局状态已恢复。所有异常路径均恢复，不能修改宿主当前目标所有权。
- [x] 原版 pass 全部发生在 Skija pass 外；使用桥的 `withContext` 借用现有 DirectContext 导入完成的纹理。图像由 Skija 正常绘到按钮槽位，既有模态层自然覆盖底层图标。
- [x] 处理纹理原点、预乘 alpha、图块边缘串色及深度清理。仅清除自有离屏附件；不把模糊链的主目标 alpha 合同误套为“所有自有纹理都不能写 alpha”。
- [ ] 验证完成才固定实现 API 并铺三端；某平台不能通过时清楚列为未覆盖，不把纯色替身结果当实际物品支持。

### 4.3 生命周期与限制

- 每帧清理并重画可见图标内容。目标容量按增长策略复用，同容量重复 prepare 不新建纹理/FBO；不以每帧 destroy/recreate 作为默认实现。
- 只在渲染线程、正确 GL context、Skija pass 外 prepare；未关闭上一个 ItemIconFrame 时再次 prepare 明确拒绝。帧句柄关闭或资源代次变更后，再 paint 要报失效，不能访问已删除纹理。
- 下一帧复用纹理之前，上一 Skija pass 必须已 flush/submit；纹理内容改变后按 Skia 借用纹理合同重建/失效图像包装，不能只覆盖 GL 纹理却让 Skia 继续采样旧缓存。
- 顺序为：释放图像借用 → 释放附件/FBO → 关闭桥 context。资源 reload、invalidate、shutdown 都覆盖；不能把 DirectContext 或原生 Canvas 交给宿主长期保存。
- 首版提供原版 GUI 模型本体与附魔效果；数量、耐久条和 tooltip 的排版不在本切片内，宿主可作为文案展示。时钟等随上下文变化的模型每帧交原版求值，不以 ID 永久缓存。
- 24 条原版记录是验收下限。自定义模组渲染器、特殊渲染目标兼容与跨 context 不凭编译宣称支持；异常要带物品 key/平台诊断，不静默换假图标。

### 4.4 文件与验证

新增或修改：

- `ui/src/main/java/com/atom/lib/controls/ControlIcon.java`；修改 `ActionButton.java`、`ControlSkin.java`、`design/atomchat/AtomChatControlSkin.java` 的图标槽和绘制；新增 `ActionIconRenderTest.java`。
- `platforms/shared/src/main/java/com/atom/lib/client/render/item/`：`ItemIconFrame.java`、`ItemIconAtlas.java`，分别管理帧借用与离屏存储/导入；沿用桥资源和上下文，不再抽象一个新渲染后端。
- 三端各自 `src/main/java/com/atom/lib/client/render/item/{MinecraftItemIcons,ItemIconRequest}.java`；修改三端 `MinecraftRenderBridge.java`，仅处理原生绘制接线和生命周期。
- `platforms/shared/src/test/java/com/atom/lib/client/render/ItemIconHiddenGlTest.java`：纯 GL 路径与异常/复用/所有权测试。平台真实 MC 绘制单独记证据，不能混算进此测试。
- `gradle/render-adapter.gradle`：增加 `atomlibRenderEvidence` 项目属性，设置 hiddenGlTest 的证据根目录；只在未指定时沿用现有默认目录。本轮执行必须显式指定新目录，防止覆盖旧模糊证据。图标测试将子目录设为 `item-icons`。
- `docs/item-icons.md`：平台最小接线、帧句柄生命周期、支持范围；`docs/quickstart.md` 链接可直接编译的完整例子。

- [x] RED：CPU 用小幅真实位图的 ControlIcon 验证图标位于槽中、文本不重叠、半裁剪正确、整体缩放一致、模态遮罩覆盖底页图标。此项只证明 UI 合成，不冒充 Minecraft 图标。
- [x] RED：隐藏 GL 验证上下方向和透明边缘、重复同容量零重分配、换容量/失效/关闭释放、异常恢复、过期 frame 拒绝、污染 PBO/像素状态恢复。
- [x] GREEN：纯 GL 原型、三端适配及原生 ItemStack 消费编译通过；实际 Minecraft 原生显示仍未运行。
- [x] 在现有隐藏 GL 环境测固定可见数量的 CPU 提交与 GPU 区段，记录冷/热、分配计数、硬件和样本数；不设未经用户确认的“达标耗时”，不拿 AC 的 0.15–0.27ms 模糊结果作本图标性能结论。

## 5. 同一场景的消费者验收

沿用 `D:/AtomLib-CreatorTrial`，保留修复前后证据；不另造第三个替身工程。

修改该工程 `TrialPage.java`、`TrialState.java`、`TrialChecks.java`、`build.gradle`、`input-lock.json`、`fabric.mod.json` 及 README/REPORT/results；新增薄 `TrialScreen.java`、`TrialClient.java`，需要按键注入时放入独立 `dev.creatortrial.mixin` 子包。业务状态新增“草稿选择/已提交选择”，控件仍由库处理输入。

- [x] 宿主页放“选择物品”入口与一次提交结果。点开模态后显示标题、分类、24 条记录、选中说明及确认/取消。
- [x] 普通窗口保持分类一行；窄窗将分类改为两行可达布局或使用库组合能力重排，不延续当前五标签挤在同一固定高度的写法。
- [x] 分类变化隐藏已选项则清空选择；无选择时确认禁用；确认返回业务 ID，取消不修改宿主已提交结果。下一次打开从宿主已提交结果初始化。
- [x] 子控件稳定复用；保留已修的连续 Enter 回归，并增加关闭后再次打开、底页焦点恢复和底页滚动不受模态影响。
- [x] 正常 1000×700、短窄 380×320，像素密度 1 / 1.5 / 2；用真实 Surface 覆盖绘制与输入几何。末项和动作都必须可达，不能只断言 offset 变了。
- [x] M1 保存真正的 CPU 模态截图；M2 没有游戏渲染环境时，真实 ItemStack 图标的实际显示单列为 NOT_RUN，CPU 图片不放伪造物品图标。
- [x] 增加可编译的薄 Screen/客户端入口，不启动客户端；只在无其他业务 Screen 或标题页打开，复用已修入口边界与专用 mixin 子包，避免恢复旧启动错误。
- [x] 最终仍只消费认证二进制 Maven。新的候选锁和原始制品摘要保存于新证据子目录，正式根 `input-lock.json` 指向该候选；旧 90eb 输入锁、Maven 和报告保留修前快照。`build.gradle` 的仓库与 SHA 校验均从该锁读取，替换目前硬编码仓库与第二份 90eb 摘要；认证摘要来自新候选验包记录，不能在构建时对输入现算现信。继续 plain implementation + 精确原始 JAR 路径/SHA 校验，不让 Loom 重新处理 Lib sources。

## 6. 执行顺序、命令与交付

顺序固定：M1 行为红灯 → M1 库实现 → 旧确认窗兼容 → M1 消费验收 → M2 原版离屏原型 → M2 图像槽与平台适配 → 消费验收 → 主代理独立复核。全程同时最多一个子代理。

库测试从 JDK 17 运行，显式提供已有测试字体，不读取 AC WIP：

```bash
export JAVA_HOME='C:/Program Files/Java/jdk-17.0.18'
./gradlew.bat :core:test :runtime:test :ui:test --offline --no-daemon \
  -PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf \
  --console=plain
```

该字体路径已在本轮规划核对为实存文件；不允许参数不存在时跳过字体相关测试并算通过。

平台打包与六消费者沿用现有工具，候选写新目录：

```bash
python tools/run_packaging.py all --jobs 1 \
  --test-font D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf \
  --maven D:/AtomLib/build/modal-item-icons-candidate/maven
```

分阶段实施时 M1 使用 `build/composable-modal-candidate/maven`；最终候选包含已在工作树中的 logo 与前轮修复，不能把旧 90eb 输入与新 API 拼在一起。产物记录三 JAR、输入 zip、源快照的精确摘要；候选打包不是发版。

三端隐藏 GL：先完成上面的证据根目录属性，再分别从 `platforms/1.20.1-forge`、`platforms/1.21.1-fabric`、`platforms/1.21.1-neoforge` 使用本目录 wrapper 运行 `hiddenGlTest --offline --no-daemon --console=plain`，依次附加 `-PatomlibRenderEvidence=D:/AtomLib/build/modal-item-icons-evidence/1.20.1-forge`、`-PatomlibRenderEvidence=D:/AtomLib/build/modal-item-icons-evidence/1.21.1-fabric`、`-PatomlibRenderEvidence=D:/AtomLib/build/modal-item-icons-evidence/1.21.1-neoforge`。Forge 使用 JDK17，其余 JDK21。本阶段不运行 runClient。

消费者使用 JDK21：

```bash
export JAVA_HOME='C:/Program Files/Java/jdk-21.0.11'
./gradlew.bat build trialChecks --offline --no-daemon --console=plain
```

正式验证前保存本轮相关 WIP 基线和旧证据；仅恢复本次负控的精确字节，不执行 checkout/reset/stash/clean。回归必须有至少一个旧行为红灯；不要求每个新类都为覆盖率写测试。

交付分开报告：

| 层次 | 允许的结论 |
| --- | --- |
| 库 CPU 交互/合成通过 | 可组合模态、滚动与图像槽的自动合同通过。 |
| 纯 GL 离屏/导入通过 | 共享上下文和纹理生命周期通过，不代表 MC 模型已实际显示。 |
| 三端编译、验包、消费者通过 | 三端 API 与打包成立，不代表三端游戏实机验收。 |
| 未启动 Minecraft | 实际物品渲染、资源包/特殊模组、真实输入和最终观感仍未覆盖；不称完整实用性验收通过。 |

用户已确认上述范围并由一个 Terra（xhigh）实施，主代理阶段复核完成；当前按用户要求收口。恢复时仍遵守一次最多一个子代理及上方未决顺序，不擅自扩展为新的渲染框架。
