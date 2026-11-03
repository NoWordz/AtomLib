# AtomChat design package

## AtomDefault（本地实施中）

`com.atom.lib.design.atomdefault.AtomDefault` 提供独立皮肤入口：`skin()` 使用现有默认外观；`skin(AtomDefault.presets().get(i))` 创建使用指定配色的新实例。每个宿主持有自己的 `ControlContext`、皮肤与 `TextService`；切换颜色时在该皮肤上调用 `updateStyle(palette.controlStyle(radius, halfScreen, cardTint))`。原 `AtomChatControlSkin(AtomChatControlStyle)` 与 `design.atomchat` API 保留，旧全局 `Design.host` / `Design.rippleSink` 仍有全局状态，不应作为多宿主隔离入口；旧入口弃用期限尚未确定。

十二个 `AtomDefaultPalette` 为不可变值，包含强调、面板、主次文字、卡片、边框色与面板透明度、是否模糊的建议值。配色源自 AtomChat，许可证及文件版本见 [来源基线](atomdefault-baseline-2026-11-02.md)。聊天气泡色、胶囊、业务配置与动作未映射；圆角不跟随预设变化。面板透明度、边框色与模糊票据目前只提供给宿主，皮肤不会自动应用或捕获背景；宿主须明确处理。默认皮肤工厂没有选择任意一套预设。预设皮肤的模态底色跟随面板色，固定旧构造器仍使用旧模态色；其余组件和效果尚未全部主题化。

明暗控件离屏图及隔离测试只是实现证据；字体角色、ripple 的跨宿主完整行为、模态各状态、实际游戏和用户观感仍待验证。

> 本地候选新增 AtomChatFonts 与 AtomChatControlSkin／AtomChatControlStyle，为可选 Slider／Toggle／ConfirmationDialog 提供 AC 材质。新路径使用实例不可变 style 与显式 UiCards/UiTokens 参数，不安装或临时替换全局 Design.host。`Design.defaultHost()`只返回不可变默认值。旧全局路径仍兼容，未由本次声称完成所有旧消费者的多mod改造。见 [完整合同](controls.md)。

> 模态外层限宽与留白由 AtomChatControlSkin.modalDialogStyle(Viewport)提供，UiSession 仅通用居中；非 AC 皮肤可使用自己的尺寸策略。本轮不实现背景模糊，新增模糊讨论文档仅随完整源码快照保留。

`com.atom.lib.design.atomchat` is an optional layer on top of the render
bridge. It is the look AtomChat ships: frosted cards, 20-unit corner radius,
blue accent, hairline rims. Import it when you want that appearance; skip it
and draw with your own tokens if you do not.

| Class | What it gives you |
|---|---|
| `UiTokens` | Sizes, radii, shadow constants, colour derivation (`cardFill`, `accentFill`, `cardHover`, `onAccent`, `hairline`) |
| `SkiaDraw` | Rounded rect, chrome shadow, edge highlight, ring, rounded/cover image, blur backdrop, clip, colour lerp |
| `UiCards` | Card surface, float surface, notification backdrop |
| `AppIcons` | Icon path data |

The tokens read host state instead of hardcoding one mod's config. Install
`Design.host(DesignHost)` to supply your corner radius, accent, card tint,
card colour, panel colour, secondary text colour and half-screen state. With
no host installed the defaults are AtomChat's shipped values, so a bare
`UiTokens.cardFill()` already looks right.

`Design.rippleSink(RippleSink)` is the optional click-feedback hook;
without one, cards draw and clicks do nothing.

Not included on purpose: layout, motion timing, message bubbles, screen chrome,
composers and any screen-level composition. These classes provide tokens and
drawing helpers, not widgets; the generic component, overlay and shell layer
from earlier extraction rounds has been deleted.
