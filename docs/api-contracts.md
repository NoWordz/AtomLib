# API contracts

`core` is platform-free: immutable geometry, layout trees/snapshots, input
records, capture dispatch and frame-clocked springs. `runtime` owns Skija
resource scopes, host fonts and text measurement/painting, images, CPU render
sessions and the backdrop blur helper. `ui` supplies the native Yoga layout
engine and presentation frames, plus the optional
`com.atom.lib.design.atomchat` package.

There is no generic component, overlay, scroll, interaction, view-host, shell
or devtools layer in this distribution. Hosts own their page trees, focus,
gestures, screen lifecycle and business state.

Layout and drawing must run on the thread that created `YogaLayoutEngine`;
native Yoga trees are never exposed. All painting, clipping and hit testing
consume the same `LayoutSnapshot`/`PresentationFrame`. Identical node IDs in
separate engines do not share state. A closed engine rejects further layout
work, and close is idempotent on the owning thread.

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
