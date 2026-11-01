# AtomLib status

Status date: 2026-11-01. Version `0.1.0-beta`, current release
[v0.1.0-beta-beta3](https://github.com/NoWordz/AtomLib/releases/tag/v0.1.0-beta-beta3).

The library now has its own public repository and CI (six jobs: pure-Java
contract tests, Windows source suite, hidden GL round trips on Xvfb for all
three loader targets, and the packaging/verifier job). AtomChat consumes the
pinned input archive from this repository's releases; the old secret-based
fetch on the AtomChat side is retired.

Implemented since the extraction checkpoint: Yoga bindings compiled per
target behind a generated bridge (3.3.1 vs 3.3.3 renamed symbols, the resize
crash blocker), subpixel text shaping in `FontRegistry`, the decided
`LayoutStyle` expansion (`Edges`, per-edge margin/padding, `aspectRatio`,
`align`/`justify`; absolute positioning is a non-goal), the default font
package (same-source Noto Sans SC VF re-subset, 7671 codepoints, zero drift
against the previous subset including at wght 500, Latin-1 and kaomoji ranges
added), `ScreenInputAdapter` with zero Minecraft types, and the
pixels-per-unit contract in `render-bridge.md`.

Evidence: the offscreen settings-home replication test lives in AtomChat and
its reports live in [evidence/settings-home-replication](evidence/);
line-width drift is fixed to 4/4, the four remaining geometry deltas are the
documented 1u rounding contract.

What has not: no external consumer yet, ARM64 hardware untested, D (Maven
host beyond GitHub releases) and the deferred E (multi-consumer
coexistence) not started. See [next-directions.md](next-directions.md) for
the live plan and its status notes.
