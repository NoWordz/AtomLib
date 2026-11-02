# 可选控件与设置页面材料（本地候选）

## 短窗口修复（2026-11-01）

最新修复候选位于 `build/interaction-fixes-candidate/maven`，上一轮候选保留。`SettingsShell` 在确认窗自然尺寸超过视口时，只对窗体做等比适配；遮罩仍覆盖完整视口。绘制、命中和裁剪使用同一 PresentationFrame，普通尺寸保持原布局。直接组合 `session.modalNode` 的宿主仍负责为其提供足够空间；本修复没有增加通用模态滚动系统。

PresentationFrame 保留缩放前的量测几何供绘制使用，避免把显示坐标反算回逻辑坐标时，40u 文字槽变成 39.999996u 并触发重新换行。详见[修复记录](evidence/interaction-fixes/report.md)。

## 完整调整页增量（2026-11-01）

新候选在 `build/appearance-candidate/maven`。下文最小候选为此前快照；公开 beta3 和 AC 锁不变。

- `Control.presentation(SETTINGS_ROW)` 选紧凑行：滑条标题/数值同行、开关在说明右侧、空说明无高度；旧构造和 CARD 保留。ControlSkin 新行入口默认转发旧入口，保持自定义皮肤源码兼容；新 Action 入口默认明确抛 UnsupportedOperationException，选择新部件的皮肤需实现它。
- `ActionButton` / `ActionModel` 完整按下→释放才激活，repeat 不重复；拖出、禁用、失焦、模态、移除取消。`SegmentedChoice` 组合稳定 ID 的同一激活部件，selected 受控，Tab 跳过 disabled；本版不另加左右箭头 roving-tab 状态机。`Disclosure` 受控展开，SettingsGroup 一次绑定稳定子控件；收起立即让子目标不可用并取消事务，不等待下次 paint。
- `SettingsGroup` 管共同卡片与分隔线。`SettingsShell` 管圆角面板、注入 title/clock、导航、自然内容、固定页头和单垂直滚动，没有路由或全局当前页。宿主声明 groups/footer、模型及回调，通用几何由库承担。
- `SettingsPageStyle` 的 width/uiScale/contentScale/opacity/cardTint 作用于真实绘制。PresentationFrame 保留缩放后的绘制/命中/clip 几何，绘制按该帧比例使用相同文本测量单位。pixelsPerUnit 必须是真实密度；Yoga 网格仍可能造成半格边界差异。
- `shell.prepare(session, viewport, style)` 先于模型更新；密度或坐标比例变化取消旧事务并回滚。样例的两个 scale 滑条在释放/键盘提交后改变坐标比例，手势中标签仍预览，避免其每帧改变坐标又取消自身。宽度/透明度连续预览；外部 scale 变化仍取消活动手势。
- ScrollViewport 保留自然高度，由 PresentationFrame 保存根裁剪与子 offset；UiSession 管滚轮（40u/格）、滚动条拖动、完整内容顺序的焦点可见性和模态隔离。滚动取消旧滑条，超限 clamp。滚动条在已滚动/悬停时显示。成功 paint 才发布该帧的滚动 bounds/extent/候选，失败保留旧输入几何。只支持一个垂直视口，无嵌套、惯性或虚拟列表。
- 滚动条按实际 thumb 行程映射内容行程，最小24u手柄仍能拖到内容末尾。捕获属于左键，其他键的 release 不结束手势；Escape 先取消滚动条捕获并吞掉其指针尾事件，再允许后续 Escape 交给宿主关闭页面。
- 确认窗保留默认取消、尾事件隔离和一次结果；AC 皮肤改为 warning/title 同行、暗色浮层、胶囊按钮。

完整消费示例在 `D:/AtomLib-Usability/src/main/java/dev/atomlib/usability/AppearancePage.java`，无 Canvas 绘制、GL、通用皮肤或输入/滚动状态机。[背景磨砂](backdrop.md)有独立资源/线程合同。游戏及用户观感仍待验，本轮不是新创作者盲测。

## 上一轮最小候选合同

2026-11-01 经用户批准实施。公开 beta3 的 API 与字节不变；本文描述隔离候选 `build/controls-candidate/maven`，尚未发布、升版或重钉 AC。完整方案见 [实施合同](superpowers/plans/2026-11-01-minimal-controls-and-fonts.md)。

## 创建与状态归属

在现有 `ui` 模块中显式选择 `com.atom.lib.controls`。没有新模块、前置 mod、页面壳或路由。宿主保留页面 Yoga 树、业务值、保存和各项资源；库负责 Slider、Toggle、单个 ConfirmationDialog 的手势、焦点、捕获、模态隔离和局部动效。

```java
fonts.registerDefaults();                  // 通用正文真实400，静态粗体700
AtomChatFonts.register(fonts);             // 独立 BODY=500、HEADING=700，不改 fallback 次序
var text = new TextService(fonts, TextBreakPolicy.CJK_UI);
var skin = new AtomChatControlSkin(AtomChatControlStyle.defaults());
var context = new ControlContext(text, skin);
var slider = new Slider(new NodeId("width"), context,
    new SliderModel("面板宽度", "连续预览，松手提交。", 480, 400, 600, 10, true),
    value -> Math.round(value) + " px", events);
var toggle = new Toggle(new NodeId("half"), context,
    new ToggleModel("半屏模式", "分别记忆宽度。", false, true), checked -> updateMode(checked));
var session = new UiSession(List.of(slider, toggle));
var input = new ControlScreenAdapter(session);
```

示例中的 `events` 与 `updateMode` 属于宿主业务。实例只创建一次，布局前 `update(model)`；`node(outerStyle)`只返回结构，不推进业务或重新创建手势。Control 是 sealed 的内部实现族，首版不承诺第三方继承；皮肤可按 `ControlSkin` 契约扩展。`controls()`返回当前不可变根控件列表。

`ControlSkin.modalDialogStyle(Viewport)`提供确认窗外层布局规格，随后传给同一皮肤的 `dialogNode`。通用 UiSession 只做视口内居中组合，不依赖 AC token。AC 皮肤保留原有限宽与两侧留白；自定义皮肤可以提供不同宽度、高度与边距策略，仍须满足实际视口和角色槽位合同。

Slider 格式化函数必须纯且非 null；文字的实际内容、字重 generation 和样式快照参与 measureRevision。模型构造拒绝无效范围、非有限值或 null 文案。传回当前 preview 是正常回声；重复旧 model 也不会误取消。真实外部值／范围覆盖会取消旧事务，并回传新的权威值。

## 每帧与接线

1. 先 `session.prepare(viewport)`，使像素比变化的取消／回滚在量测前发生；随后更新模型与皮肤快照，组合各个 `control.node`，用宿主 Yoga 引擎布局。prepare→update→layout→paint在同一UI线程内连续进行。
2. 用该帧的真实 Viewport 创建 `PresentationFrame`。取得 `session.modalNode(viewport)`，若非 null，以另一个 Yoga 引擎布局并通过 `PresentationFrame.compose` 叠到基页之上。
3. flush 原版 GUI，在共享 MC 桥的租借回调内按真实 pixelsPerUnit 仅缩放一次 Canvas。宿主背景／标题先画，再 `session.paint(canvas, frame, viewport, time, motionEnabled)`。paint保留防御性prepare，但不能替代布局前的prepare，否则回滚文案／页面宽度可能错过本帧量测。
4. 成功画完后库才发布命中几何；失败保留上次成功几何。新替换实例未被成功显示前不能激活。根控件按 PresentationFrame.paintOrder 绘制和命中，登记列表顺序不覆盖 compose 的层序；画、点、clip 与焦点读同一快照。
5. 所有鼠标／键盘／滚轮／字符先送给 ControlScreenAdapter；consumed 才停止，否则调用 super。不得 super-first 短路 release。每帧转发窗口焦点，removed 时先取消。
6. 关闭顺序为 session → 宿主布局引擎 → text → fonts → runtime。借入服务与 MinecraftRenderBridge 不归 session 关闭。

三份可编译 MC Screen recipe 在 `examples/a/<target>/src/main/java/com/atom/example/a/ControlsRecipe.java`。1.20.1 的滚轮是单 delta，1.21.1 是双 delta；Fabric 使用 `isWindowFocused()`，Forge／NeoForge 使用 `isWindowActive()`轮询窗口失焦沿。它们不是要求宿主继承的库基类。

## 交互合同

- Slider 在鼠标上连续预览，不按 step 吸附；键盘 Arrow／Home／End 按 step 或端点。实际值改变后，匹配 release 或最后一个调整键 keyup 提交一次；不变点击不保存。按最近成功显示的 track.content 行程累计 delta，重排本身不移动值。
- Escape、失焦、移除、禁用、打开模态、关闭或坐标比例变化取消活动事务一次，不提交。无活动事务的 Escape 交回宿主关闭 Screen。commit 和 cancel 都先结束内部事务／capture 再调用业务；preview 异常尝试一次回滚，清理异常放入 suppressed。
- 同一窗口内取消后，仍按住的键的 repeat 和 keyup 归旧手势，直到各自释放；新的完整 down/up 才能重新操作。ControlScreenAdapter 的窗口失焦沿在取消后清空物理按键／鼠标跟踪，因为系统可能不再发送旧 keyup；重新聚焦后的新完整操作不能被旧尾键永久吃掉。
- Toggle 只在完整左键手势或 Enter／Space 的完整 keydown/keyup 后激活；repeat 不重复激活，拖出、禁用与取消无 onChange。
- Tab／Shift+Tab 按显示顺序遍历可见且 enabled 的目标，移走焦点会取消旧目标的未完事务。
- 打开窗体使用 `session.open(dialog, onResult)`；同一会话最多一个可见／关闭中的窗体，第二次 open 明确拒绝。没有嵌套队列。
- 打开立即隔离下层并取消旧手势；打开它的按键／鼠标尾事件被吞掉。默认焦点取消，完整 Enter 取消；Tab 到确认后新的完整 Enter 才确认；Escape 或遮罩取消。结果先标为已决／CLOSING 再回调，只发一次。
- 遮罩取消需要在卡片外完成按下与松开；卡片标题、图标、正文、padding 只吞事件，不产生结果。卡片内按下拖出或遮罩按下拖入卡片都不取消；左右按钮分别保留自己的完整手势合同。
- OPENING、ACTIVE、CLOSING 都隔离 pointer/key/wheel/character。透明度到0才恢复仍有效的旧焦点；期间按住的按钮／键继续保留序列归属直到释放。close 对未决窗产生一次取消，已决窗不再回调。

内部子 ID 为根 ID 加 `/role`。页面及其他控件不能占用这些保留名字。Slider 的 track.content 是手柄中心可用行程，hit 是可按区域，皮肤与行为都读取这份几何。相同 NodeId 在两个不同会话中互不共享 focus/capture/style/动画；同一 Control 实例不能同时借给两个会话。

## 字体与有限中文断行

`FontRegistry.register(id, source)`仍保留来源默认实例。新增 `register(id,source,FontOptions.weight(w))`选择真实变量轴；轴越界或静态脸不匹配明确失败，不伪粗、不钳制。注册与 reload 先加载全部替代脸再换入；失败保留旧 generation 和旧脸。`FontSource.bundledRegular()`／`bundledBold()`只读原来的默认资源，源字体和 OFL 未改。

默认 regular 从来源100改为明确400，是本地候选的有意外观变更。AC 字体角色另选500／700。openFont 明确 ANTI_ALIAS、NORMAL hinting、subpixel=true，量测与绘制用相同设置。

旧 TextService 构造仍为 GRAPHEME。新 `CJK_UI`保留字形簇与显式换行，对方案中的中文起止标点回退寻找合适边界；极窄无合法边界时保留内容和原宽度合同。它不是完整 UAX／Bidi／IME 引擎。TextRequest 原字段及构造器不变。

## 候选、范围与证据

平台发布及六个消费者通过 `-PatomlibTestMavenRepo=<path>`解析指定仓；`run_packaging.py --maven`传递同一属性。默认 build/test-maven 不变。打包脚本 `--maven`与 `--output`选择输入输出，缺输入在写输出前失败。

独立消费者 `D:/AtomLib-Usability`只消费 compiled Maven，显式执行 `tools/lock_candidate_input.py`生成 candidate-lock；日常 build 只读锁，配置阶段核对原始 jar 的 SHA-256。构建输出按摘要隔离；Loom1.7.4 没有缓存目录 setter，样例按认证摘要归档／轮换其 remapped_mods 目录，保留旧缓存，串行运行构建。

候选锁仍为 `schema_version: 1`，字段为 `group/artifact/version/target/sha256`，SHA 针对原始平台 jar，接口修订必须显式重锁。本轮汇总 `results.json` 使用 `schema_version: 2`，新增独审结论、红绿／mutation 证据和逐层验收状态；样例原有各能力字段继续单列，overall 保持 `PENDING_GAME_AND_VISUAL`。schema 版本不是库发布版本，也不表示游戏验收通过。

本轮证据位于 Lib `build/controls-candidate/`与样例 `evidence/controls-candidate/`。历史 beta3 `evidence/result.json`、六张静态 PNG 与六张 AC 参照不覆盖。CPU、GL、游戏、用户视觉状态分开报告。编译与离屏通过不代表游戏或观感通过。

不包括滚动、文本编辑／IME、菜单、导航、页面壳、虚拟列表、任意模态栈或 AC 生产迁移。内容放不进视口时明确报布局不足，不截字、缩字号或由样例私补滚动。首版模态只淡入淡出。Windows玩家 native 范围不扩；ARM64硬件、专服、真实多mod共存与 Maven host仍未由这轮验收。


## 可组合模态

`ModalDialog` 组合稳定的普通控件实例：构造 `ModalOptions(title, preferredWidth, margin, dismissOnBackdrop, initialFocus)`，调用 `content(bodyNode, controls)`、可选 `navigation(choice)` 和 `actions(buttons)`，然后通过 `session.open(dialog, reason -> ...)` 打开。宿主在确认动作中先保存业务值，再调用 `session.closeModal(ModalCloseReason.COMPLETED)`；取消不应提交草稿。一个会话同一时刻只能有一个活动或退场中的模态，控件不能同时归属两个会话。内容更新在 UI 线程进行，控件身份应保持稳定。

普通宿主在每帧将 `session.modalFrame(viewport)` 与底页的 `PresentationFrame` 合成，再调用 `session.paint`；`SettingsShell` 已自动合成。滚轮、焦点、遮罩和关闭由库管理。正文过长时只滚正文；空间不足以放置头尾时整个内容可滚。`modalNode` 仍服务旧确认窗，可组合模态必须使用 `modalFrame`。模态皮肤通过 `modalTitle` 和 `paintModalSurface` 提供表面：未实现这两个入口的皮肤在使用可组合模态时会明确报错。


### ActionButton 图像槽

`icon(ControlIcon, size)` 为同一控件增加非交互 `/icon` 槽位；null 与正尺寸预留，null 与 0 清除。同尺寸句柄更新不需重新量测，尺寸变化需布局。普通点击、焦点和可用性继续属于整行控件，图像由皮肤按本帧槽位裁剪；旧构造器和无图像槽的按钮宽度保持原合同。原版图标服务见 [item-icons.md](item-icons.md)。
