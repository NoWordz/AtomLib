# AtomChat design package

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
