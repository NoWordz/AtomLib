# API contracts

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

There is no generic component, overlay, scroll, interaction, view-host, shell
or devtools layer in this distribution. Hosts own their page trees, focus,
gestures, screen lifecycle and business state.

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
