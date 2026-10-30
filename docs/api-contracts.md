# API contracts

`core` is platform-free: immutable geometry, layout trees/snapshots, input
records, capture dispatch and frame-clocked springs. `runtime` owns Skija
resource scopes, host fonts and text measurement/painting, images, CPU render
sessions and the backdrop blur helper. `ui` supplies Yoga layout, themes,
components, overlays, scrolling, HUD/crop interaction and the optional shell.
`devtools` is never bundled into player JARs.

All painting, hit testing, clipping, scrolling and focus operations consume the
same `LayoutSnapshot`/`PresentationFrame`. Identical node IDs in separate
`UiView` instances do not share state. Layout and drawing must run on the
thread that created the view; native Yoga trees are reusable but never exposed.

`HostContext` borrows a `FontRegistry`, a body `FontId`, `TextService`, a theme
snapshot, feedback callback, clipboard and translator. The host owns those
resources and must close its `UiView` first. Theme changes retain the borrowed
service identities.

For Minecraft rendering, `SkijaRenderBridge` owns one DirectContext per GLFW
context. Normal drawing uses `drawFrame`; Minecraft content uses `vanillaPass`;
raw resource preparation uses the synchronous `withFrameAccess` or
`withContext` lease. Objects returned by a lease are borrowed only for that
callback. A page's `close()` never closes the shared render bridge. Close the
bridge and runtime on the render thread before the GL context is destroyed.

`UiView` owns only its base/overlay Yoga engines, focus, capture, scrolling and
layout snapshot. `showOverlay`/`closeTopOverlay` maintain one authoritative
layer stack. Escape closes the top layer first; modal focus restoration is
limited to controls that still exist and are enabled.

`TextField` is a single-line grapheme-aware LTR editor with preview/commit/cancel,
selection and clipboard commands. `VanillaTextInputAdapter` maps GLFW keys and
committed Unicode into it. IME composition remains a host carrier concern;
full bidi, UAX #14 line breaking and CJK line-break rules are not claimed.

`VirtualList` measures only visible rows plus a configured buffer. `HudEditor`
updates a draft and commits once; `ImageCropper` returns owned PNG bytes while
the host retains storage and upload. `AppShell` provides slots and page motion;
route names, chat drafts and business persistence stay with the host.

`BackdropBlur.draw` accepts an already captured `Image`, a destination, sigma
and fallback colour. Capture stays with the platform bridge. It does not read
the default framebuffer or claim HDR/multisample support.
