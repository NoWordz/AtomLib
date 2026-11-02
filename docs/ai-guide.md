# AI authoring guide

> 完整外观候选使用 `build/appearance-candidate/maven`，公开 beta3 不含以下新能力。设置页优先组合 SettingsShell / SettingsGroup / SETTINGS_ROW / ActionButton / SegmentedChoice / Disclosure，所有值受控；样例不得自写 Canvas/GL、输入、焦点、滚动或通用皮肤。Shell 先 prepare，再更新业务模型并 layout；页面输出和 session 输入共用 PresentationFrame。只实现一个垂直内容视口；scale 提交后改变坐标，坐标变化取消旧事务。主题/显示若未实现就禁用，不画可用假入口。背景磨砂通过 [库 API](backdrop.md) 在 Skija pass 外调用，宿主先 flush；SKIPPED 要用库实底，不能吞掉编程错。

> 2026-11-01 本地候选增加[最小控件路径](controls.md)。选择它时只在页面写布局、文案、业务值及动作；禁止在消费者补轨道／开关／模态内部坐标、焦点、捕获或退场计时。模型先 update 再 layout，modalNode 单独 Yoga 后 compose，同一 Viewport 交给 paint。未选候选时下文公开 beta3 foundation 边界仍成立。

AtomLib is a rendering bridge, a Yoga layout engine and an optional design
package. There is no `Section`, `SettingRow`, `AppShell` or other widget layer
to build pages from. A host describes its own tree with `LayoutTree`,
`LayoutNode` and `LayoutStyle`, lets `YogaLayoutEngine` calculate it, and paints
with Skija.

When matching AtomChat, use `com.atom.lib.design.atomchat`: `UiTokens` for
sizes, radii, shadows and colour derivation; `SkiaDraw` for rounded rectangles,
highlights, images and blur; `UiCards` for card surfaces. When you need a
different look, draw directly with a `Canvas` and keep the geometry in your own
code.

Use token values instead of literal colours or repeated page-level arithmetic.
Do not hard-code a development font path into a distributable sample. Use
`FontRegistry.registerDefaults()` for the bundled subsetted Noto Sans SC faces,
or register host-supplied fonts first to override them and extend coverage.
Pass the selected font explicitly. Do not make global static theme, layout,
capture or animation state; each host owns its lifecycle and clock.

Host business state, persistence, networking, chat drafts, identity images and
IME composition remain outside the library. Keep layout, paint and hit testing
on the same `PresentationFrame`, and use stable `NodeId` values for
reconciliation.

`tools/verify_ui_examples.py` guards the two example paths: example A must use
`UiTokens`, `SkiaDraw` and `UiCards`; example B must use Yoga and a native
Skija `Canvas`; no example may import a removed component/overlay/shell layer.
It also keeps fixture checks that reject development-machine paths and raw
ARGB/geometry injection in pages. It is a supplemental check; production
render tests and user visual acceptance remain necessary.
