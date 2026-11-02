# API contracts

> 2026-11-01 本地候选新增可选 `ui/controls`：Slider、Toggle、单确认窗与 UiSession，合同见 [controls](controls.md)。下文 foundation 描述仍对应公开 beta3；选择候选控件时手势／焦点／模态归库，业务与页面结构归宿主，原始路径保持。release 在回调前已结束 capture，允许业务同步 open/close/setControls。

> 独审修订：ControlSkin.modalDialogStyle(Viewport)拥有模态外层规格，通用会话不依赖 AC。绘制和命中均按 PresentationFrame 层序。卡片内非按钮区域仅吞事件；普通取消隔离 held-key 尾序列，窗口失焦由适配器清空物理跟踪，详见控件合同。

`core` is platform-free: immutable geometry, layout trees/snapshots, input
records, capture dispatch and frame-clocked springs. `runtime` owns Skija
resource scopes, host fonts and text measurement/painting, images, CPU render
sessions and the backdrop blur helper. `ui` supplies the native Yoga layout
engine and presentation frames, plus the optional
`com.atom.lib.design.atomchat` package.

`ScreenInputAdapter` (also `core`, no Minecraft types) turns screen-style
pointer callbacks into `InputDispatcher` calls: `press`/`drag`/`release`
(double coordinates, int button) return true only when the dispatcher
consumed the event, `scroll` routes wheel events to a host `ScrollHandler`
instead of the dispatcher (scroll is not a `PointerEvent` type), and
`removed()` ends an active gesture with a cancel, never a release. Loaders
only differ in their scroll callback arity (one delta on 1.20.1, horizontal
plus vertical on 1.21.1); the adapter and the recipe in quickstart cover all
three. Hit testing stays with the host's `HitTester`, capture with the
dispatcher, and reactions with the `PointerTarget`s.

The raw foundation path leaves page trees, interaction and business state to
the host. The local candidate additionally offers the optional `controls`
package described above: it owns only the three controls' gestures, focus and
single confirmation lifecycle. It adds no generic scroll, view-host, shell,
router or devtools layer; hosts still own page composition and business state.

Layout and drawing must run on the thread that created `YogaLayoutEngine`;
native Yoga trees are never exposed. All painting, clipping and hit testing
consume the same `LayoutSnapshot`/`PresentationFrame`. Identical node IDs in
separate engines do not share state. A closed engine rejects further layout
work, and close is idempotent on the owning thread.

`LayoutStyle` stays a single flat record with wither methods and the
`column()`/`row()` factories. Per-edge spacing uses the `Edges` record
(top/right/bottom/left, finite values, negatives legal): `margin(Edges)`
accepts negative edges (Yoga allows them) and stacks with the container's
`gap`; `padding` is an `Edges` field with the old uniform `padding(float)`
and `spacing(float,float)` kept as sugar. `aspectRatio(float)` follows Yoga's
width/height ratio when exactly one dimension is definite; 0 disables it.
Cross-axis and main-axis alignment map onto Yoga's `alignItems` and
`justifyContent`: `Align` defaults to STRETCH and `Justify` to FLEX_START,
both matching Yoga's own defaults (pinned by measured-coordinate tests).
Absolute positioning and relative offsets remain unsupported by design —
screens needing absolute coordinates draw them in the render layer.

`FontRegistry`, `TextService` and the design package's host inputs are borrowed
from the host. The host owns those resources and closes its layout engine and
text/font resources before the runtime. Theme changes do not replace borrowed
service identities.

For Minecraft rendering, `SkijaRenderBridge` owns one DirectContext per GLFW
context. Normal drawing uses `drawFrame`; Minecraft content uses `vanillaPass`;
raw resource preparation uses the synchronous `withFrameAccess` or
`withContext` lease. Objects returned by a lease are borrowed only for that
callback. Closing a layout engine or host resource scope never closes the
shared render bridge. Close the bridge and runtime on the render thread before
the GL context is destroyed;
`MinecraftRenderBridge.releaseIfOpen()` does exactly that and is wired to each
loader's client shutdown, releasing only a bridge that already exists.

`com.atom.lib.design.atomchat` provides `UiTokens` (sizes, radii, shadow
constants and colour derivation), `SkiaDraw` (rounded rectangles, shadows,
edges, rings, images, blur and clipping), `UiCards` (card and float surfaces)
and `AppIcons` (icon path data). `Design.host(DesignHost)` supplies host
colours and half-screen state; `Design.rippleSink(RippleSink)` is the optional
click-feedback hook. The package does not own layout, motion timing or screen
composition.

`BackdropBlur.draw` accepts an already captured `Image`, a destination, sigma
and fallback colour. Capture stays with the platform bridge. It does not read
the default framebuffer or claim HDR/multisample support.


### 可组合模态生命周期

`open(ModalDialog, Consumer<ModalCloseReason>)` 打开单层模态，`closeModal` 在无活动模态时幂等。关闭原因区分完成、取消、宿主关闭、失焦和坐标尺度变化。正常关闭后退场期间继续隔离底页；失焦、宿主关闭、坐标尺度变化和抛异常的关闭回调会释放会话所有权。焦点与滚动使用成功绘制的几何，画布保存栈在异常时恢复；不要缓存上帧布局来命中新的内容。保留已有 `ConfirmationDialog` 接口。
