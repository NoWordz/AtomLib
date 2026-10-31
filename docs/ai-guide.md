# AI authoring guide

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
Do not hard-code a development font path into a distributable sample. Pass the
host font explicitly and keep font bytes outside AtomLib. Do not make global
static theme, layout, capture or animation state; each host owns its lifecycle
and clock.

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
