# AI authoring guide

Build ordinary pages from `Section`, `SettingRow`, `Label`, `Button`, `Slider`,
`Toggle`, `TextField`, `VirtualList`, overlays and other public components.
Describe data, actions and hierarchy; Yoga calculates sibling positions.

Use semantic `ThemeSnapshot` values (`palette`, `spacing`, `radius`, `type`,
`motion`) instead of literal colours or page-level coordinate arithmetic. A
new appearance variant belongs in a production component or the component
gallery first. Use `CustomPainter` or a raw Skija callback only when the visual
cannot be represented by a component, and document the reason in the host code.

Do not hard-code a development font path into a distributable sample. Pass the
host font explicitly and keep font bytes outside AtomLib. Do not make global
static theme, view, focus, capture or animation state; each host and `UiView`
owns its own lifecycle and clock.

Host business state, persistence, networking, chat drafts, identity images and
IME composition remain outside the library. Keep layout, paint, hit testing and
scroll on the same `PresentationFrame`, and use stable `NodeId` values for
reconciliation.

`tools/verify_ui_examples.py` guards the three example paths and rejects raw
geometry/colour injection in semantic page examples. It is a supplemental
check; production render tests and user visual acceptance remain necessary.
