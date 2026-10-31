# AtomChat design package

`com.atom.lib.design.atomchat` is an optional layer on top of the render
bridge. It is the look AtomChat ships: frosted cards, 20-unit corner radius,
blue accent, hairline rims. Import it when you want that appearance; skip it
and draw with your own tokens if you do not.

| Class | What it gives you |
|---|---|
| `UiTokens` | Sizes, radii, shadow constants, colour derivation (`cardFill`, `accentFill`, `cardHover`, `onAccent`, `hairline`) |
| `SkiaDraw` | Rounded rect, chrome shadow, edge highlight, ring, rounded/cover image, blur backdrop, clip, colour lerp |
| `UiCards` | Card surface, float surface, notification backdrop |
| `AppIcons` | The shell icon paths |

The tokens read host state instead of hardcoding one mod's config. Install
`Design.host(DesignHost)` to supply your corner radius, accent, card tint,
card colour, panel colour, secondary text colour and half-screen state. With
no host installed the defaults are AtomChat's shipped values, so a bare
`UiTokens.cardFill()` already looks right.

`Design.rippleSink(RippleSink)` is the optional click-feedback hook;
without one, cards draw and clicks do nothing.

Not included on purpose: layout, motion timing, message bubbles, tab bars,
composers and any screen-level composition.
