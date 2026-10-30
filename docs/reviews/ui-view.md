# UI view 生产链独立审查

日期：2026-10-29。审查者：Codex。结论：**确认 7 项 P2 正确性问题，均有当前生产类的限域复现；本轮没有修改实现或测试源码。** 优先处理手势终止／异常与 close 清理，再处理 hidden、焦点 reveal 和 overlay 状态一致性。

## 范围与证据

完整读取本次指定的 `view/UiView`／`HostContext`、`component/*`、`input/FocusManager`、`overlay/OverlayHost`、`scroll/ScrollView`、`core/input/InputDispatcher`，以及它们消费的 `PresentationFrame` 和现有相关测试。没有重新做来源全量调查，没有继续审查 runtime worker 尚未收口的新实现，没有跑 AC 基线。

边界遵循用户说明：`OverlayHost` 目前是独立状态模型；`UiView.suspendInput` 由宿主同一模态状态调用。**没有把缺少 T8 通用弹窗绘制或 OverlayHost 未接入 UiView 列为 bug。** runtime 只通过公开 API 提供复现所需的字体／CPU Surface，本文不评价其内部实现。

复现环境为 Windows x64／JDK17.0.18／LWJGL Yoga 3.3.1／Skija 0.143.18，调用已有 `build/standalone/{_core,_runtime,_ui}/classes/java/main` 与缓存内的 binary JAR。从 stdin 执行 JShell，没有生成 Java 测试文件、PNG 或修改 prod；source／javadoc JAR 不进入 classpath。一次带 sources JAR 的探查未编译成功，不计入运行证据；后续使用 binary-only classpath 的实际结果如下。

关键源文件 SHA-256（行号对应本次读取）：

| 文件 | SHA-256 |
|---|---|
| `ui/src/main/java/com/atom/lib/view/UiView.java` | `4fa7d5bf4d60584d0ef01dad0d658e2c3560fbe0eec90580a9c543115d490875` |
| `ui/src/main/java/com/atom/lib/component/Slider.java` | `463539c19e1a9ccaff70f9e499461c0176ed8129ab1697da0d36aacf0c44ceef` |
| `core/src/main/java/com/atom/lib/input/InputDispatcher.java` | `6184527877c6ea9dc50ced608f1cd2600828e65ea1d773273e7442f953fac93c` |
| `ui/src/main/java/com/atom/lib/overlay/OverlayHost.java` | `6e6102266edc20a725fdc1037c0114d9bb432ab4dea8cfbd185edde89ca49b1c` |

现有测试已包含真实设置行、绘图与动态 panel width 的正常整手势，以及普通取消、重复 release、同帧 paint、样式变化等。下述问题集中在**宿主回调内部同步改状态或抛异常**、隐藏滚动树和嵌套焦点，而不是否定正常路径测试。

## V1 · P2：release 的 preview 抛异常后，Slider active 与捕获所有权分离

**位置：**`core/.../input/InputDispatcher.java:24–27`；`ui/.../component/Slider.java:47–54`。以下 `ui/...` 均为 `ui/src/main/java/com/atom/lib/`，`core/...` 为 `core/src/main/java/com/atom/lib/`。

**触发：**按下滑条成功；绑定的 `preview` 在最后一次 release 更新中抛 `IllegalStateException`。Dispatcher 在进入 `target.release()` 前清空了 capture；Slider 在调用 preview 之后才把 active 置 false。异常跳过这一步，也没有异常路径调用 Slider.cancel。

**实测：**初值 20，press 到 30，release 到 80 时 preview 先写入 80 再抛异常；随后调用 dispatcher.cancel：

```text
RELEASE_EXCEPTION=preview failed
ORPHAN active=true capture=null cancels=0 value=80.0
```

此后 Cancel／关屏不能再通过 dispatcher 找到原 target；Slider.key 还会因为 active=true 拒绝键盘。MOVE 的 drag callback 也没有统一异常终止路径，只是那条路径仍保留 capture。现有测试只测成功 preview 和普通外部取消，没有覆盖这两类异常。

**建议：**让正在结束的 gesture 也有可取消的 owner，并在 press／drag／release 的外部 callback 抛异常时统一终止、恢复组件状态，再传播原异常。清理异常不应掩盖原异常。测试 release-preview 抛异常后 active=false、capture=null、cancel 恰好一次、可开始新手势；另测 drag 和 commit 异常，明确 commit 失败是否可回滚，不能凭空承诺外部持久化原子性。

## V2 · P2：release preview 同步打开模态或禁用自己，仍发生 commit

**位置：**`InputDispatcher.java:27`、`Slider.java:49–51`、`UiView.java:33`。

**触发：**press 成功后，宿主在 release 的 `binding.preview` 内调用 `view.suspendInput(true)`。此时 dispatcher 已清空 capture，suspend 的 input.cancel 找不到正在 release 的滑条；preview 返回后 Slider 直接 commit。若 callback 改为 `slider.setEnabled(false)`，同样只在 preview 之前检查过 enabled，之后仍 commit。

**用真实 UiView＋Slider＋ValueBinding 的实测：**

```text
REAL_MODAL_RELEASE commits=1 cancels=0 capture=null
```

**直接生产组件的补充实测：**

```text
RELEASE_CANCEL commits=1 cancels=0
RELEASE_DISABLE commits=1 enabled=false
```

这是已经请求暂停底层输入之后的错误提交，不是缺少弹窗绘制。现有 `UiViewIntegrationTest:44–47` 是 press 返回后再 suspend；`SliderGestureTest:24–32` 也是从 release 外部取消，二者都避开了当前窗口。

**建议：**gesture 应有可取消的 ending 阶段；最后一次 preview 返回后，确认该事务未被取消／移除／disable／modal 打断，才允许唯一一次 commit。仅在 preview 后再检查 `active` 还不够：dispatcher 清空 owner 后，modal.cancel 根本没有触达到 Slider。回归直接把 `suspendInput(true)`、`setRoot` 移除、disable 放进最后一次 preview，断言 0 commit、1 cancel 和初值恢复。

## V3 · P2：隐藏的 scroll 节点仍被查询 layout.box，导致整个 refresh 失败

**位置：**`UiView.java:41–44`，特别是第 42 行；对照 `YogaLayoutEngine.collect` 与 `PresentationFrame.collect` 跳过 hidden 子树的行为。

**触发：**自定义合法 `UiElement` 的布局节点同时为 hidden 和 scroll，或 scroll 节点处于 hidden ancestor 下。`tree.nodes()` 包括它们，而布局 snapshot 不包括 hidden 子树。scroll 预处理没有按实际可见树过滤，直接查询 box。

**实测：**普通可见根下放一个 `behavior(false,true,true,true)` 的节点后 `view.refresh()`：

```text
HIDDEN_SCROLL=java.lang.IllegalArgumentException: Unknown node ID: NodeId[value=hidden]
```

自定义节点通过公开 UiElement／LayoutStyle 构建，测试 helper 仅提供声明的树和空 paint，没有重写布局公式。现有 hidden 测试停留在 Yoga adapter 层，没有经过 UiView scroll 同步。

**建议：**从布局 snapshot 的可见节点／paintOrder 或携带 ancestor visibility 的遍历计算 scrolling 与 extent；清掉隐藏 scroll state／target 时也执行既定取消策略。回归 hidden-scroll 本身、hidden ancestor 下的 scroll、hide→show，断言 refresh／paint／pointer 不抛异常且隐藏节点无命中。

## V4 · P2：合法 action 关闭 UiView 后，事件入口继续 refresh 已关闭实例

**位置：**`UiView.java:66–70,72–76,90`；内置 `Button.java:27,29` 的 action 允许宿主关闭页面。

**触发：**release 的 target.action 同步调用 `view.close()`，正常返回后 `pointer()` 仍无条件执行第 70 行 refresh；键盘 action 也有第 76 行同类路径。

**生产 UiView 实测：**自定义 PointerTarget 的 release 只调用该 view.close：

```text
CLOSE_RELEASE=java.lang.IllegalStateException: UiView is closed
```

关闭／返回按钮是正常 UI 动作，事件完成后不应被包装成非法使用已关闭视图。此次没有观察到 native 崩溃；实际问题是异常从正常交互链逃逸。测试中关闭 A 再绘制 B 并不覆盖在 A 自己的 callback 内关闭 A。

**建议：**事件事务在每次宿主 callback 后检查终止状态，关闭后直接返回已确定的消费结果，跳过 focus／refresh／继续绘制。明确 close 重入与进行中事件／paint 的结束策略；回归 pointer action close、keyboard action close、preview／cancel 内 close 以及 close 后显式外部调用仍按约定拒绝。

## V5 · P2：焦点 reveal 只调整最近 scrollOwner，无法展开位于外层视口之外的嵌套容器

**位置：**`UiView.java:84–86`，`ScrollView.reveal` 本身能处理给定坐标；缺失的是祖先传播。

**触发：**100 高的 outer scroll，先放 200 高 filler，再放 50 高 inner scroll；inner 内有 20 高可聚焦 target。Tab 能选中 target，它在 inner 内已经可见，所以 inner 不需滚动；但整个 inner 位于 outer 的视口之外。

**实测：**

```text
FOCUS_REVEAL focused=NodeId[value=focus]
box=Rect[x=0.0, y=200.0, width=50.0, height=20.0]
clip=Rect[x=0.0, y=200.0, width=100.0, height=0.0]
```

焦点已经移动，实际可见 clip 高度仍为 0，键盘用户看不见当前控件。只测试 ScrollView.scroll 的剩余量传父级不能覆盖 reveal，二者是不同通路。

**建议：**由内向外遍历 scrollOwner 链，在每级 offset 更新后依据新的 presentation 几何继续 reveal；必要时重新获取 frame，避免用旧坐标。回归两级／三级滚动、内层不需滚但外层需要、部分可见、target 比 viewport 大及 resize 后再次 Tab。

## V6 · P2：OverlayHost.closeTop 的取消回调异常使 overlay 栈和焦点模态栈失配

**位置：**`ui/.../overlay/OverlayHost.java:21`，关联 `FocusManager.java:22–25`。

**触发：**modal 已 open，`cancelCapture` 在 closeTop 内抛异常。代码先从 overlays 移除 top，再调用 cancelCapture；抛异常后跳过 focus.popModal。

**独立状态模型实测：**回调在第二次调用抛异常（第一次为 open 时）：

```text
OVERLAY_EXCEPTION=cancel failed
OVERLAY blocking=false count=0 tab=NodeId[value=confirm]
```

此时 overlay 可见状态已无模态、blocking=false，但 FocusManager 仍在已移除 dialog 的 confirm 上循环。即使不接入 UiView 绘制，这个模型本身也产生了不一致状态。

**建议：**先明确 close 失败的语义：保留 overlay 与 focus 栈，或二者都完成移除后再传播清理异常；不能只移除其中一边。回归取消回调异常、嵌套 modal、非 modal 顶层、clear 中途失败与焦点恢复目标已删除。

## V7 · P2：UiView.close 在 cancel callback 抛异常时跳过原生 owner 清理

**位置：**`UiView.java:90`，关联 `InputDispatcher.cancel:42` 与 `Slider.cancel:53`。

**触发：**view 正捕获一个滑条；其 `ValueBinding.cancel` 抛异常。close 顺序为 input.cancel→engine.close→closed=true；第一个调用抛异常后 native engine 不关闭、closed 仍为 false。

**真实 UiView／Slider 实测，使用 Yoga config 数量作范围明确的资源证据：**

```text
CLOSE_CANCEL_THROW=cancel failed
AFTER_FAILED_CLOSE_CONFIG_DELTA=1
AFTER_SECOND_CLOSE_CONFIG_DELTA=0
```

第二次 close 才回收该 config；宿主在第一次 close 异常后丢弃 view，就没有执行必要释放。不是声称 JVM 进程内所有资源永久泄漏，而是单次 AutoCloseable 清理契约未完成、必须额外重试。

**建议：**关闭事务先进入 closing／closed 状态阻止 callback 重入继续使用；用 finally 或聚合清理确保 input 取消失败仍释放 engine、focus、scroll 与元素引用，再传播主异常／suppressed 清理异常。测试一次 close 即 config 数量恢复、重复 close 无副作用、cancel 内重入 close、cancel 抛异常与关闭期间 callback 再发输入。

## 正向检查和未覆盖边界

通过现有生产 `Label`／`Section`／`UiView`，用宿主显式注册的 AC 本地字体文件（只读，未拷入 Lib）和真实 Skija CPU Surface，本轮实际观察到：

```text
SAME_FRAME_STABLE=true
FONT_RELOAD_PAINT=OK REVISION_CHANGED=true
```

同一 `FrameTime(frameId=1)` paint 两次，布局 revision 没有额外推进；调用公开 `FontRegistry.reload()` 后再次 paint 正常且 revision 更新。`Label.layout:28–30` 会将字体 generation 加入 measureRevision；没有证据支持“当前普通字体重载必然使用旧测量”的泛化指控。未测试重载与 paint 并发，不评价 runtime worker 的正在修改实现。

`PresentationFrame.topAt:29` 同时检查展示 hit 与 clip；绘制也消费 frame.box 的 clip，正常滚动偏移在 PresentationFrame 统一施加。未发现足够具体的普通 draw/hit 两套坐标缺陷；上面的空 clip 焦点与 hidden scroll 崩溃分别列为 V5／V3，不据此否定统一几何设计。

`InputDispatcher.setTargets` 会在 refresh 后因移除／disable 取消 capture，press callback 内显式 cancel 也已有覆盖。不能把这些正常路径说成完全没有取消机制；当前缺口是同步 setter 到 refresh 的窗口、正在 ending 的 release 与外部 callback 异常。V2 针对后者给出实际 UiView 复现。

本审查没有重新运行全套 `:ui:test`，没有把用户报告的通过数量算作本轮执行量。限域 JShell 的退出 0 只是执行进程成功，七项失败依据是明确输出与预期状态对照；没有把它们写成“测试全绿”。未覆盖 Minecraft／GL、ARM64、3.3.3、输入法、完整 T8 弹窗与持久化 commit 的外部原子性。

## 建议追加的生产回归

1. 在 `UiViewIntegrationTest` 内把 modal／remove／disable 放进 release 最后的 preview，分别断言 0 commit、1 cancel、capture 与 active 同时清空。
2. preview／commit／cancel 分别抛异常，覆盖 press／move／release／key／close；检查原异常保留、终止恰好一次、页面资源仍能释放。
3. hidden scroll 本身及 hidden ancestor 的完整 view.refresh／paint／pointer；show 后正常恢复。
4. Button pointer／keyboard action 同步 close，事件入口正常终止；close 的失败回调与重入不遗留 native owner。
5. nested scroll 的 Tab reveal 使用真正 view.frame 的 border／clip 验证可见，而不只检查 offset 变化。
6. OverlayHost 的取消回调抛异常时，visible／blocking／allows／focus 模态栈仍保持同一种状态。

这些测试应调用现有生产 dispatcher／view／component，不写一套测试专用手势或布局实现。修复前先保留上述可重复失败，修复后再跑目标测试；不要改通过阈值或让异常被无条件吞掉。

## V1–V7 修复复核与本批关闭（2026-10-30 追加）

**本批结论：V1–V7 的原始复现路径均已关闭，可以交回主代理继续 MC 接线。** 这是对本批问题和下述源码版本的限定结论，不代表 UI 全面验收、T8 完成或可公开发行。此前发现的 V6 回调重入变体保留在末尾，未把它悄悄算作已修复，也未继续扩大本轮审查范围。

此次仅复核对应生产修复、相关回归、红绿日志和既有 XML；没有改 prod／测试源，没有再次运行 AC 基线或全量构建，没有启动客户端／专服。隔离客户端／专服的启动授权与验证由主代理负责，本文不覆盖其结果。

### 当前源码身份

| 文件 | SHA-256 |
|---|---|
| `core/src/main/java/com/atom/lib/input/InputDispatcher.java` | `8a0aa8790e2ef7d81a432190850a55c223dd2371221a02756eef3543aa2724d9` |
| `ui/src/main/java/com/atom/lib/component/Slider.java` | `8ddfc9109d05e43bc5467e252d1fe996d95328a6e4afc8da01a431cc44486b5c` |
| `ui/src/main/java/com/atom/lib/view/UiView.java` | `11b62a488cc10775ee21f0f98667e74065886b49e7f75a8dadf2ff68ffa84faf` |
| `ui/src/main/java/com/atom/lib/overlay/OverlayHost.java` | `77c838de5e9cce9d67e78c1c33727fda14becac116b41b7553119aa3b85a5f54` |

### 逐项关闭依据

行号对应本节源码版本；首轮上方的旧行号与失败输出作为历史证据保留。

| 问题 | 当前修复和生产回归 | 复核结论 |
|---|---|---|
| V1 | `InputDispatcher:30–35,48,52` 在 drag／release／press 异常时 abort，release 到 finally 才清 owner；`Slider:54–56` 清理 preview／commit 失败并保留原异常。`SliderGestureTest:21–32` 验证异常对象不被替换、active/capture 清空、1 次 cancel、内存值恢复及可重新按下 | 原始异常后孤立 active 路径关闭；另外实测 primary 与 suppressed 身份均保留。commit 的外部持久化原子性仍未承诺 |
| V2 | release 期间 owner 仍在；`Slider:40,50–51` disable 即取消、preview 后复查 active/enabled；`UiView:30–34,39` 同步取消移除目标及暂停手势。`SliderGestureTest:11–19`、`UiViewLifecycleTest:61–74` 把 cancel／disable／modal／remove 放入最后一次 preview | 原始错误 commit 路径关闭，回归断言 0 commit／1 cancel／初值恢复／无 capture；键盘事务也按 `Slider:67–69` 检查取消状态 |
| V3 | `UiView:52–58` 按 snapshot 的可见 paintOrder 处理 scrolling，子项 extent 只查询可见 ID。`UiViewLifecycleTest:31–39` 覆盖 hidden scroll 自身、hidden ancestor 与重新显示 | 原始 hidden-scroll box 查询崩溃路径关闭 |
| V4 | `UiView:82,93` 在 action 关闭后跳过 refresh；paint 在 `75` 检测关闭后终止后续项。`UiViewLifecycleTest:40–49` 使用真正 Button 的 pointer／keyboard action 关闭页面，且关闭后外部 refresh 仍拒绝 | 原始正常关闭动作逃逸异常路径关闭 |
| V5 | `UiView:101–103` 沿全部 scrollOwner 由内向外 reveal，每次 offset 改动后 refresh 并取新几何。`UiViewLifecycleTest:76–84` 使用真正 Button 验证焦点 ID 与展示 border∩clip 的正高度 | 原始“内层已可见、外层完全不可见”路径关闭；不是任意三级／超大 target 的穷尽证明 |
| V6 | `OverlayHost:21–25` 在 cancelCapture 异常后仍执行 focus.popModal，保留主异常与 suppressed；`28` 的 clear 聚合异常并继续收尾。`FocusAndModalTest:10–16` 断言 overlay 为空、blocking=false、Tab 回到 body | 原始 cancelCapture 抛异常导致隐藏 focus trap 的路径关闭；回调重入变体见末尾，未被此结论覆盖 |
| V7 | `UiView:111–114` 先进入 closing，重复 close 直接返回；聚合 cancel 与 engine.close 异常后清理引用并进入 closed。`UiViewLifecycleTest:51–60` 覆盖 cancel 内重入 close 后再抛异常、config 数量恢复与重复 close | 原始一次 close 失败跳过 native owner 释放的路径关闭；另外实测不重入、单纯 cancel 抛异常时一次 close 也恢复 config 数量 |

### 实际读取的红绿证据

- `D:/AtomLib/view-review-red.log`：`25 tests completed, 7 failed`，`BUILD FAILED in 9s`；失败包括旧异常手势、release 中取消、隐藏 focus trap、action close、nested reveal、release modal/remove、hidden scroll。这个计数不是“每个 V 编号各有一个独立红测”的证明。
- `D:/AtomLib/view-review-green.log`：实际执行了 `:core:test` 与 `:ui:test`，`BUILD SUCCESSFUL in 10s`、9 actionable tasks。本审查读取该执行记录，没有把它写成审查者重新运行的命令。
- 恢复后读取 `build/standalone/_ui/test-results/test/TEST-*.xml`：**25 tests，0 failures，0 errors，0 skipped**，timestamp `2026-10-29T15:20:10`。
- 同时读取 `_core/test-results/test/TEST-*.xml`：**8 tests，0 failures，0 errors，0 skipped**，timestamp `2026-10-29T15:20:08`；其中 geometry 3、input 3、motion 2。故本节以实际 XML 合计 8 为准，不沿用先前“core 6”的口头数量；未据此扩大为 motion 代码审查。

审查者额外在 stdin JShell 中调用修复后的生产类，未写测试文件，观察到：

```text
V1_PRIMARY=true SUPPRESSED=true ACTIVE=false CAPTURE=null CANCELS=1
V2_COMMIT=0 CANCEL=1 VALUE=20.0 ACTIVE=false CAPTURE=null
V6_EXCEPTION blocking=false count=0 focus=NodeId[value=base]
V7_EXCEPTION=cancel failed without reenter
V7_SINGLE_CLOSE_CONFIG_DELTA=0 CLOSED=true CAPTURE=null
```

V7 的计数只证明本样本 Yoga config 被回收，不等于全部 native 对象无泄漏。未扩大到 runtime worker 新代码、MC／GL、ARM64 或新的输入／组件能力。

### 已告知、未扩查的 V6 回调重入线索

原 V6 是 **cancelCapture 抛异常**。它已关闭。补查时另观察到同一 closeTop 事务中的 **cancelCapture 同步打开另一个 modal** 会使两栈错位：`OverlayHost:22` 先移除旧 overlay，但旧 focus modal 尚未 pop；callback 中 open 新 modal 会 push 新 focus 层，随后 `24` 的 pop 弹掉新层，留下旧 confirm。

限域实测：新 overlay 的 interactive 为 `newConfirm`，但 `focused()`／`tab()` 返回 `oldConfirm`，`allows(focused())=false`。此前已在会话中告知；这是一个具体 P2 待办，**未修复、未算入本批原始七条关闭，按用户要求不继续扩大审查或改实现**。

后续若单独处理，回归应在关闭旧 modal 的 cancelCapture 中只重入一次 open(newModal)，检查可见 overlay、focus 与 allows 一致；可在调用宿主 callback 前完成对应旧 focus 层的移除，或明确拒绝／排队这类结构性重入。不能用当前异常回归通过来宣称该重入变体也已通过。

### V6 回调重入变体的关闭（2026-10-30 追加）

上面「未修复」的措辞作为当时的记录保留，以本节为准。`cb612db` 的 `OverlayHost.closeTop` 已按建议顺序执行：先 `overlays.remove`，再在宿主 callback 之前 `focus.popModal()`，最后才 `cancelCapture.run()`；callback 里同步 `open(newModal)` 因此 push 在已经弹出的旧层之上。

回归 `FocusAndModalTest.closingAModalMaySynchronouslyOpenItsReplacement` 断言：关闭旧 modal 时同步打开新 modal 后，`focused()` 与 `tab()` 都落在 `newConfirm`，`allows(focused())` 为真，可见 overlay 只有 1 个。本轮实际执行 `gradlew :ui:test --tests com.atom.lib.input.FocusAndModalTest`：4 tests，0 failures/errors/skips（2026-10-30T15:37:50Z，日志 `build/modal-reentry-1.log`）。

该关闭只覆盖这一重入顺序；不扩大为 V1–V7 之外的新结论，也不替代 MC/GL、真实弹窗动画与 AC 侧 `AcModalAdapter` 的实机验证。
