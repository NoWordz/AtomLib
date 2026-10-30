# T8 浮层、虚拟列表与文本框

本批新写代码使用 Apache-2.0，只消费 AtomLib 现有契约及 Skija API。不复制 ARR 或未准入 AC 实现。写集限于四个新 overlay、VirtualList、TextField、对应新测试与本文；UiView、InputDispatcher、OverlayHost、FocusManager、ScrollView 和既有组件由主代理维护。

## 主代理接入契约

- 浮层是可组合 UiElement，放进宿主明确提供的 overlay 槽，其 layout／geometry／paint 与命中使用同一 LayoutBox。现有 LayoutStyle 没有 absolute positioning，本批不伪造该能力。锚点浮层需要主代理提供“相对 viewport 的 Rect 槽 + 独立 clip + z 顺序 + 最上层优先输入”的桥；槽位应在边界避让后同时用于 layout 和 hit。
- Menu、Tooltip、ConfirmationDialog、ColorPicker 的显隐由组件状态控制；宿主根据同一状态调用 OverlayHost 和暂停底层输入。Esc／方向键先送当前顶层浮层，不能同时再送底层 view。外点拦截由宿主在 slot hit 之外显式处理。
- TextField 提供 `insertText(String)`、`command(Command)`、`setFocused(boolean)`、`commit()`／`cancel()`。主代理需要把当前焦点的字符输入、复制剪切粘贴全选快捷键、焦点获得／失去和关屏取消路由过来；现有 KeyEvent 没有字符负载。不可仅把 GLFW keycode 当作输入文字。IME composing text 留 MC adapter，未经实机验证不宣称支持。
- VirtualList 拥有自己的可见窗和逻辑 offset。主代理在命中其 viewport 时先调用 `scroll(distance)`，将返回的未消费距离交给外层 scroll owner；焦点需要展示指定项时调用 `revealKey(key)`。不要再让 UiView 的 ScrollView 对该 leaf 施加第二次 offset。
- 这些建议不要求本执行者修改既有接口。主代理可在 RootUI 桥中提供上述路由；若后续新增 UiElement 的 focus/text/wheel 生命周期钩子，调用同样的公共方法即可。

已读取基础 worker 的 `OverlaySpec.centered/anchored`、`OverlayPlacement`、`OverlaySize`、`UiView.showOverlay/closeTopOverlay/textInput` 与 `UiElement.focusChanged/textInput`。组件按这些实际 API 接入。

需要主代理补齐的具体生命周期钩子：`default void UiElement.overlayClosed(){}`。四个新浮层已经提供同名方法，分别 close/hide/dismiss；应在 overlay 内容根从 view 移除时调用一次（含 Esc、锚点失效、关屏），异常参与原有清理聚合。真实回归 `OverlayContentViewTest.removingConfirmationContentFromTheViewCancelsExactlyOnce` 当前会证明无钩子时 Esc 遗漏确认窗取消回调。另建议 `default float wheel(float x,float y,float distance){return distance;}`；VirtualList 已提供对应方法，边界外原样返回距离。

## 实施与验证顺序

先以编辑事务和 grapheme 回归固定 TextField，再以大量数据、稳定 key、测量缓存和锚点补偿固定 VirtualList，最后完成浮层的状态与真实 Skija 绘图／输入测试。仅运行本批测试及必要的 ui 回归，不重跑 AC 基线。本文在交付时追加实际测试结果、API 用法和未支持项；实现审查由另一强代理承担，本执行者不将自己的验证冒充独立审查。
