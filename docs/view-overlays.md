# 视图浮层与输入 API

接口已写入，可供并行组件/宿主接线。`OverlayHost` 的现有契约未修改。

```java
view.showOverlay(OverlaySpec.centered(dialog).withSize(OverlaySize.STANDARD));
view.showOverlay(OverlaySpec.anchored(menu, anchor.id(), OverlayPlacement.BELOW_ANCHOR));
view.showOverlay(OverlaySpec.anchored(tooltip, anchor.id(), OverlayPlacement.TOOLTIP)
    .withSize(OverlaySize.COMPACT));
boolean closed = view.closeTopOverlay();
List<OverlaySpec> layers = view.overlaySnapshot(); // 不可修改，底层在前
InputResult consumed = view.textInput(committedUnicodeText);
```

`OverlaySpec(UiElement content, OverlayPlacement placement, NodeId anchor, OverlaySize size, boolean modal)` 是不可变描述，`id()` 等于 content 根 ID。`centered()` 默认 modal/standard；`anchored()` 除 TOOLTIP 外默认 modal/anchor。可用 `withModal(boolean)` 和 `withSize(OverlaySize)` 覆盖。所有子节点 ID 在整个视图内唯一；打开浮层或替换 base 时，重复 ID 会拒绝且不替换原内容。

Placement 为 `CENTER / BELOW_ANCHOR / ABOVE_ANCHOR / TOOLTIP`；Size 为 `COMPACT / STANDARD / WIDE / ANCHOR`。前三种宽度取主题 controlHeight 的 6/9/14 倍，ANCHOR 使用锚点宽度；最终受 viewport、内容明确指定的点宽度及 maxWidth 约束。高度由 Yoga 自然测量。下方放不下时尝试上方，上方放不下时尝试下方，最终夹在 viewport 内。锚点使用同一展示帧里的稳定 NodeId，跟随滚动和 resize；锚点删除、隐藏、disabled 或完全滚出裁剪区，及浮层根隐藏时，关闭该层与其上方层。

每个存活浮层持有一棵可复用 Yoga 树。内部 `YogaLayoutEngine.layoutIntrinsicHeight(tree, viewport)` 保持宽度约束、使用自然高度；普通 `layout(tree, viewport)` 的根填满语义保持不变。展示层为自然测量结果提供高度受限的滚动 viewport，保留长文本的完整高度，不在有限 AtMost 高度下丢失溢出。`PresentationFrame.compose()` 合成只读帧，绘制、hit、clip、anchor 和 scroll/reveal 共用它；浮层不参加 base 的 row/column 布局。

模态层的绘制顺序和输入拦截来自 `OverlayHost.snapshot()`；视图按实际可见、enabled 的内容动态更新 focus trap。只保留一个 capture 和一个 focus。打开 modal 取消当前手势，关闭任意层按现有 OverlayHost 契约取消当前 capture；Esc 优先关闭最上层，再处理无浮层时的 capture。关闭模态恢复仍有效的原焦点。Tab 可经所有 scrollOwner reveal，包括浮层外层滚动 viewport。TOOLTIP 默认非模态，打开时不取消底层手势、不主动抢焦点。

`UiElement` 新增兼容默认接口：

```java
default void textInput(String text) {}
default void focusChanged(boolean focused) {}
```

视图只向当前 enabled/可见/允许的焦点转发文本。文本参数为宿主已提交的完整 Unicode 字符串。`UiView.textInput()` 返回消费结果。焦点进入、离开、同 ID 元素对象替换、disabled/隐藏/关闭均通知生命周期；没有焦点的模态仍拦截文本。`KeyEvent.Key` 原枚举项保留，并追加 `A/C/V/X/Z/Y`。

`HostContext` 五参构造继续有效。新增七参构造接受 `HostContext.Clipboard`（`String read()`、`void write(String)`）及 `Function<String,String> translator`；通过 `clipboard()`、`translator()`、`translate(String)` 访问。五参默认翻译原样返回，剪贴板读空字符串/写无操作；服务由宿主借给视图，`withTheme()` 保留其身份，不依赖 MC、AC 或内置字体。

`UiViewOverlayTest` 覆盖 20 个真实 Skija/Yoga 行为：遮挡绘制与拦截、焦点 trap/恢复、锚点滚动/resize、超大内容真实滚动、隐藏/失败回滚、同 ID 替换、回调重入和资源计数。该套件在最终标准测试中为 20/20。游戏内观感、IME、ARM 实机仍由宿主/用户验收。
