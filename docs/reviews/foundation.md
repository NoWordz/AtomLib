# AtomLib foundation 独立代码审查

> 历史记录（2026-10-31 注）：本文提到的通用组件/输入代码已在后续提取中删除；相关结论仅用于当时的 foundation 审查追溯。

日期：2026-10-29。审查者：Codex（独立于本轮 foundation 实现者）。**最新结论：F1–F5 五项已在文末复核关闭；F2 以明确拒绝 wrap+gap 关闭，未实现该组合功能。** 以下保留首轮 1 项 P1／4 项 P2 的历史证据和旧行号，当前修复位置见文末。本报告不改实现或测试，不运行 AC 基线，不表示整个 AtomLib 已验收。

用户说明新增代码由主代理依据契约与官方 API 独立编写、未读 Tuui 反编译；本审查按该事实边界检查功能与资源所有权，不因类名相似推定代码来源。本次审查者同样没有读取 Tuui 反编译内容。

## 审查输入与证据范围

- 完整读取 `core/src/main/java/` 的 20 个契约／值类型、`core/src/test/java/com/atom/lib/geometry/GeometryContractTest.java`。
- 完整读取 `ui/src/main/java/com/atom/lib/layout/internal/YogaLayoutEngine.java`、`ImmutableLayoutSnapshot.java` 与 `ui/src/test/java/com/atom/lib/layout/YogaLayoutEngineTest.java`。
- 只读构建依赖、Yoga 探针的 `LayoutContractTest`、`NativeBackend`，用于区分探针测试与真实生产 adapter 的覆盖；未把探针跑通当成生产 adapter 正确性证明。
- 审查引擎源文件 SHA-256：`c1101ac5ef17186432d2516b9a2b10f7017123e5a7d27bf6ac86ba99f43931d5`。
- 限域复现使用现有 `_core/classes/java/main`、`_ui/classes/java/main`，Windows x64 JDK `17.0.18` 的 JShell 与 Gradle cache 的 LWJGL／Yoga `3.3.1`（含 Windows native）。没有重新编译、没有新增测试源文件、没有启动 MC／窗口，也没有运行全构建。
- 被调用引擎 `.class` SHA-256：`592496a04f4cf4e88cde258969584c34d939276eed1dbefc004957a5a7e36bc7`；其修改时间晚于上述源文件约 9 秒。记录源码与产物身份，**不声称运行过隔离重建或三个加载器／ARM64／3.3.3 的本轮回归**。
- 已读的既有 XML：core 3 tests、ui 3 tests，均 0 failures/errors/skips；时间分别 `2026-10-29T14:20:58`／`14:24:13`。这是现有报告，不是本审查重新执行的结果。

下述行号对应本次读取的源文件。修复时应增加真实生产 `YogaLayoutEngine` 的回归测试；不要只测试另一个探针 backend 或新写的复制公式。

## F1 · P1：measure 回调可在 Yoga 活动调用栈内销毁或重建原生树

**位置：**`ui/src/main/java/com/atom/lib/layout/internal/YogaLayoutEngine.java:34–38,48–49,79,119`；尤其是调用外部 `LeafMeasure` 的第 36 行与直接释放资源的第 119 行。

**触发：**宿主传入的 `LeafMeasure` 闭包捕获同一个 engine，在回调中调用 `engine.close()`；或递归 `engine.layout(otherTree, viewport)`，其中新树删除／替换正在测量的节点。

**原因与影响：**`checkThread()` 只限制创建线程，回调恰好在该线程执行；没有 `layoutInProgress`／回调深度守卫。`close()` 会执行 `YGNodeRemoveAllChildren`、`YGNodeSetMeasureFunc(null)`、`YGNodeFree`、`callback.free()`、`YGConfigFree`。这时第 79 行的 `YGNodeCalculateLayout` 尚未返回，Yoga 仍可能访问这些节点与 config，甚至当前 trampoline 本身正在执行。catch 住 Java 回调异常不能恢复已经释放的 native 内存。嵌套 layout 同样可能在第 60 行释放外层正在访问的 entry。

**复现建议（此危险路径未实际执行）：**

```java
var engine = new YogaLayoutEngine();
var leaf = LayoutNode.leaf("text", LayoutStyle.column(), constraints -> {
    engine.close(); // 当前代码会直接进入释放路径
    return new MeasuredSize(20, 20, 10);
}, 0);
engine.layout(new LayoutTree(node("root", LayoutStyle.column(), leaf)), viewport);
```

这是根据当前源码确认的未防护释放路径；为避免破坏本机进程，本轮没有故意制造 use-after-free，也不声称已观察到崩溃。

**建议修复：**在每次 native layout 事务开始前建立活动标记，以 `finally` 恢复；活动期间拒绝对同一 engine 的 `layout()` 和 `close()`，在任何 native mutation 之前抛出清晰异常。继续把测量异常保存在 Java 侧并在 native 返回后传播。测试应验证回调中的关闭／嵌套 layout 被拒绝，异常返回后 engine 能继续使用，最后在 native 调用栈外正常 close。不能只在 `checkThread()` 增加判断而遗漏重入路径。

## F2 · P2：把 gap 附在全局非末项的右／下 margin 上，wrap 分行错误且没有行间 gap

**位置：**`YogaLayoutEngine.java:69–74`；`LayoutStyle.java:8,22` 已公开 `wrap` 和 `gap` 的组合。

**触发：**宽 128 的 row，`gap=8`、`wrap=true`，三个固定 60×20、`shrink=0` 的子项。

**实际复现：**

```text
a = Rect[x=0,  y=0,  width=60, height=20]
b = Rect[x=0,  y=20, width=60, height=20]
c = Rect[x=68, y=20, width=60, height=20]
```

**期望：**a 和 b 可恰好占满第一行（60+8+60=128），c 在下一行；若 `gap` 表示两个方向的统一间距，下一行 y 应为 28。

**原因：**代码以“整个 visible 列表的最后项”决定是否加 margin，而不是每条 flex line 的末项。a、b 都有右 margin 8，Yoga 将它们按 68+68 测算而提前换行；只写右 margin 同时没有提供行间间距。当前探针的 wrap 用例只测自设 `marginRight(5)`，生产引擎现有 3 项测试没有覆盖 wrap+gap，无法挡住这个问题。

**建议修复：**在目标绑定能力允许时使用真正的 gap；否则必须实现有明确行末语义的 fallback，并覆盖 row／column wrap。没有正确 fallback 前可以明确拒绝 `wrap && gap>0`，避免静默生成错误布局。回归至少覆盖 3 项／2 行、末行、hidden 中间项和百分比／grow 项。

## F3 · P2：只改变 pixelsPerUnit 时，snapshot revision 更新但几何仍是旧取整

**位置：**`YogaLayoutEngine.java:77–79`。

**触发：**同一棵宽 101 的 row，两子项各 `grow=1,basis=0`，先 layout scale=1，再只把 viewport 的 `pixelsPerUnit` 改为 2，宽高不变。

**实际复现：**

```text
scale=1: a.width=51.0
同一 engine 改为 scale=2: a.width=51.0，snapshot revision 1→2
新 engine 直接 scale=2: a.width=50.5
```

**原因与影响：**当前 3.3.1 对配置 point scale 的修改不会自动使这棵干净树重新计算已缓存的 layout；Java 侧虽然绕过 snapshot 快路径，却没使原生布局失效。GUI scale／像素密度切换后，同一树得到依赖历史的像素几何；新的 snapshot revision 不能保证几何正确。

**建议修复：**scale 变化时在 native calculate 前使根及相关子树缓存正确失效，或按明确策略重建；不能只增加 snapshot revision。实际 `3.3.1` API 有 `YGNodeMarkDirtyAndPropogateToDescendants`（上游拼写如此），但修复仍需检查该函数对本树与版本的实际语义，而非误用只能给 measured leaf 调用的 `YGNodeMarkDirty`。

**回归：**同一 engine 对比全新 engine，scale 1→2→1、非整数 scale、带测量叶子与 padding；断言全部 border／命中相关几何，而不只断言 revision 增加。

## F4 · P2：旧子节点提升为根时，继承上一父节点注入的 gap margin

**位置：**`YogaLayoutEngine.java:70–74`，结合第 56 行的解绑和第 61 行按 NodeId 复用 Entry。

**触发：**第一棵树为 `p(row,gap=8) -> [a(auto), b(auto)]`；下一棵树直接将同 NodeId 的 a 作为根。a 在 native Entry 中保留此前的 right margin 8；新树没有父节点循环替它清零。

**实际复现：**viewport 100×100；提升后的 a 为 `Rect[0,0,92,100]`，同样 style 的新根本应宽 100。

**原因与影响：**margin 实际是父级布局上下文产生的临时状态，却存放在被复用的 child native node 上；解绑只移除 parent 关系，没恢复父级注入样式。因此 UI 子树提取／页面根切换会无故损失 8 单位可用宽度。普通 a/b 在两个非根父节点间互换的抽样通过，因为新父循环会覆盖 margin；该通过不能覆盖提升为根的情况。

**建议修复：**每次同步先清掉每个 live node 的父级派生 margin，再施加当前 parent 的 gap，或直接停止用 child margin 表示 gap；为根明确清零。回归覆盖子→根→子、带 gap 的 row→无 gap 的 column、前后位置交换及整个旧父节点被删除。

## F5 · P2：删除测量回调没有使 native 布局失效，旧高度留在新树

**位置：**`YogaLayoutEngine.java:28–43`，具体为第 31 行 `measure==null` 分支。

**触发：**先布局 auto 高度的 measured leaf a，回调返回高度 30；再用同 NodeId／同 style／空 children、`measure=null` 的普通空 leaf 替换它，viewport 和父级结构不变。

**实际复现：**

```text
移除前 a.height=30
同一 engine 移除 measure 后 a.height=30
新 engine 直接布局同一无 measure 树：a.height=0
```

**原因与影响：**非空 measure 分支调用 `YGNodeMarkDirty`，而 null 分支只取消 native callback 并释放 trampoline。旧原生缓存仍可复用；Java `measureRevision` 字段已更新但不能替代 native dirty。节点从文字／图片测量内容变成空占位时，会保留错误高度和命中面积。

**建议修复：**把 measure 能力切换视为节点布局状态变化，使用目标 Yoga 支持的有效失效策略，必要时安全重建 Entry 并重挂父子关系。不要在取消 measure 后盲目调用仅允许 measured leaf 的 dirty API。回归覆盖 null→measure→null、同一 callback 的 revision 增长、measure→container，以及移除 callback 后资源只释放一次。

## 已抽样通过的路径及覆盖限制

通过 JShell 对现有生产引擎的调用，本轮观察到：

```text
REPARENT_AND_ADD=PASS
SAME_MEASURE_REVISION_STYLE_CHANGE=PASS
HIDDEN_NOT_PAINTED=PASS
CLIP_CHANGE=PASS
MEASURE_REVISION_DIRTY=PASS
```

具体是：两个现存子节点互换 parent 并添加第三个节点；measureRevision 仍为 0 时修改 gap 能更新子项位置；hidden 节点不进入 paintOrder；父 clip 的改变反映在后代 clip；同 callback 的 measureRevision 0→1 能将叶高 20 改为 40；重复 close 在这些正常路径未抛异常。

这些只是所列样本，没有证明所有重挂／删除组合、native allocation 失败、Windows ARM64、3.3.3 或并发行为都通过。没有 native node／callback 的完整分配释放计数，因此不写“资源无泄漏”。现有异常测试会在失败后换用新 callback／revision；它没有覆盖 F1，也不能验证 remove-measure 的 F5。

`LayoutNode` record 的结构相等比较包含 style、children、measure、measureRevision：源码读取及上述样本均不支持“仅 revision 相同就跳过全部布局”的指控。第 50 行的快路径虽是递归比较，现阶段没有证据证明它会忽略正常不可变树的 style／hidden／clip 变化；本报告不把这个猜测列为 bug。真正观察到的 scale 缓存问题已单列 F3。

core 的范围验证、重复 NodeId 拒绝和 immutable snapshot 复制目前没有发现足够具体的额外缺陷；极端 float 溢出、无限深度树和未来 baseline alignment 未作为本轮实用阻塞项。

## 重放方法

不写工程文件即可用本机 JDK17 的 JShell，从 stdin 执行下面片段。classpath 取已有 core/ui main classes 与 Gradle cache 的 `lwjgl`、`lwjgl-yoga` 3.3.1 Java/native JAR；本轮两次 JShell 进程均退出 0。**JShell exit 0 不等于上述业务断言通过**；F2–F5 是明确打印出错误值再与全新 engine／契约对照。

```java
import com.atom.lib.geometry.*;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.*;
import java.util.*;
LayoutNode node(String id, LayoutStyle s, LayoutNode... c) {
    return new LayoutNode(new NodeId(id), s, List.of(c), null, 0);
}
var viewport = new Viewport(100, 100, 1);

// F2
var fixed = LayoutStyle.column().size(Length.points(60), Length.points(20))
    .flex(0, 0, Length.auto());
try (var e = new YogaLayoutEngine()) {
    var s = e.layout(new LayoutTree(node("r", LayoutStyle.row().spacing(0,8)
        .behavior(true,false,false,false), node("a",fixed), node("b",fixed),
        node("c",fixed))), new Viewport(128,100,1));
    for (String id : List.of("a","b","c"))
        System.out.println(id + "=" + s.box(new NodeId(id)).border());
}

// F3: 同一 engine 对比新 engine，错误结果为 51 而非 50.5。
var flex = LayoutStyle.column().flex(1,1,Length.points(0));
var tree = new LayoutTree(node("r",LayoutStyle.row(),node("a",flex),node("b",flex)));
try (var e = new YogaLayoutEngine()) {
    e.layout(tree,new Viewport(101,10,1));
    System.out.println(e.layout(tree,new Viewport(101,10,2)).box(new NodeId("a")).border());
}
try (var e = new YogaLayoutEngine()) {
    System.out.println(e.layout(tree,new Viewport(101,10,2)).box(new NodeId("a")).border());
}

// F4
try (var e = new YogaLayoutEngine()) {
    e.layout(new LayoutTree(node("p",LayoutStyle.row().spacing(0,8),
        node("a",LayoutStyle.column()),node("b",LayoutStyle.column()))),viewport);
    System.out.println(e.layout(new LayoutTree(node("a",LayoutStyle.column())),
        viewport).box(new NodeId("a")).border());
}

// F5
try (var e = new YogaLayoutEngine()) {
    var leaf = LayoutNode.leaf("a",LayoutStyle.column(),
        c -> new MeasuredSize(20,30,15),0);
    e.layout(new LayoutTree(node("r",LayoutStyle.column(),leaf)),viewport);
    var empty = new LayoutTree(node("r",LayoutStyle.column(),node("a",LayoutStyle.column())));
    System.out.println(e.layout(empty,viewport).box(new NodeId("a")).border());
}
```

修复优先级建议：先 F1 的 native 事务与重入边界，再 F5／F3 的缓存失效，随后一起解决 F2／F4 的 gap 状态模型。每项先在生产 adapter 上增加能复现旧错误的测试，修复后再跑 core/ui 目标测试和选定两个 LWJGL 版本的矩阵；不要改写 golden 或只增加“没有抛异常”的弱断言。

## F1–F5 修复复核（追加，2026-10-29）

仅复核新 engine、其测试与这五项修复；未改实现、未新增测试源文件、未重跑 AC 基线。当前 engine SHA-256：`0eb0948d684fe8d9938d32b31f2b776c11d530f0fd81033fd51f89e4abde1cff`；当前 `YogaLayoutEngineTest.java` SHA-256：`ff4ad0f9ee38eebd42c34124fd7390681e4133a23ef8064435052d6f0f28d661`。首轮 hash 对应旧实现，保留用于重放，不是当前实现身份。

| 项目 | 当前修复证据 | 复核结果 |
|---|---|---|
| F1 | engine `49–54` 在事务前拒绝重入，并通过 finally 复位活动标记；`135` 在释放任何 native 资源前拒绝活动期间 close；test `85–98` 覆盖回调内两种操作被拒绝且随后还能 layout | 关闭。另以 JShell 验证回调未捕获 close 拒绝异常时，在 native 返回后传播 cause，engine 仍能恢复使用 |
| F2 | engine `52` 在设活动标记及 native mutation 之前拒绝任何 `wrap && gap>0`；test `79–83` 固定异常 | 关闭错误结果路径，**功能收窄**：有 gap 仅支持单行 Row／Column，wrap+gap 仍不支持。另在 JShell 验证已有有效树后遭拒绝，再返回原树仍取得同一有效 snapshot |
| F3 | engine `92–93` 设置 point scale 后，scale 变化时使根及 descendants dirty；test `55–62` 明确断言 1→2→1 时宽度 51→50.5→51 | 关闭，本轮 Windows x64／3.3.1 实测通过；不是对 ARM64／3.3.3 的运行声明 |
| F4 | engine `79` 对所有 live node 先清零 parent 派生的 right/bottom margin，再由 `84–89` 按新关系注入；test `64–69` 覆盖子提升根宽度恢复 100 | 关闭；父级派生 margin 不再遗留在根 |
| F5 | engine `58–75` 检测 measured／非 measured 模式切换，把待替换节点及其直接 native parent 先解绑，再 dispose／remove／创建；`81–83` 重建关系；test `71–77` 验证移除 measure 后高度为 0 | 关闭；不以仅 dirty descendants 的无效修补代替 Entry 替换。另跑 50 轮 measure→container(kid)→measured root→普通 leaf，并同时重挂 sibling，样本通过 |

**实际执行的目标测试命令**（本机 JDK17，工作目录 `D:/AtomLib`）：

```text
java -classpath D:/AtomLib/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :ui:test --tests com.atom.lib.layout.YogaLayoutEngineTest --no-daemon --console=plain
BUILD SUCCESSFUL in 10s
7 actionable tasks: 2 executed, 5 up-to-date
```

本轮读取新 XML：`build/standalone/_ui/test-results/test/TEST-com.atom.lib.layout.YogaLayoutEngineTest.xml`，**8 tests，0 failures，0 errors，0 skipped**，timestamp `2026-10-29T14:43:06`。这是所选测试类的本轮执行结果；用户报告的 core 6／ui 12 全套结果不冒算成本审查执行量。本命令没有执行完整 build、加载器启动、AC 基线或其余 ui 测试。

补充 JShell 从 stdin 调用新编译产物（未写测试文件），6 个检查全部通过：

```text
F2_REJECT=PASS
F2_REJECT_PRESERVES_SNAPSHOT=PASS
F1_PROPAGATE_OUTSIDE_NATIVE=PASS
F1_ENGINE_RECOVERS=PASS
F5_MEASURE_CONTAINER_ROOT_REPARENT_50_CYCLES=PASS
CONFIG_INSTANCE_COUNT_RESTORED=PASS
EXTRA_CHECKS=6 FAILURES=0
```

最后一项对比 `YGConfigGetInstanceCount()` 的循环前后值，仅证明本样本 config 数量恢复，**不是全部 node/callback native 内存无泄漏证明**。原生分配失败、callback 分配失败与其他绑定版本仍未穷尽。本次限定复核没有发现五项修复中的新增具体缺陷；其他新组件／输入代码不包含在这次关闭结论里。
