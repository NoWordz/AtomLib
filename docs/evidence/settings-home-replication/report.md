# Direction B evidence: the settings home page, replicated offscreen

One AtomChat screen drawn twice in one JVM: once through AtomChat's shipping
path (`SettingsHomePage.render`), once through the AtomLib stack (`YogaLayoutEngine`
+ `TextService` + the design package). Same bundled Noto bytes, same
design-package tokens and card painter, same zh_cn strings, both on a raster
`Surface`. Every difference below is therefore a real stack difference.

- Fixture: `AtomChat` `platforms/1.21.1-fabric/src/test/java/com/atom/chat/settings/SettingsHomeReplicationTest.java`
  (it must live in AtomChat: only there do both stacks, the MC translation
  table and the bundled font resources share one classpath).
- Raw data: `results.json` (21 checks, 7 failed) plus both PNGs, next to this file.
- AtomLib input tested: `0.1.0-beta` local test-maven jar, built from
  `codex/atomlib-extraction` 2026-10-31.
- Panel 480x860 at (40,20), list rect (55, 102.5, 450, 700), 2x2 tile grid.

## Result in one line

The stack is capable: Yoga reproduces the page's geometry exactly when the
pixel grid is fine, TextService matches line heights exactly, and the
whole-image pixel smoke passes at 0.76% (non-text, limit 2%). What is missing
is three concrete fixes, not another extraction round.

## What passed

| Check | Result |
|---|---|
| Tile geometry at pixelsPerUnit 16 | exact match, all 4 tiles (e.g. ac (63.000, 115.000, 210.750, 210.750) == lib) |
| Title line height | exact match, all 4 titles (27.150 vs 27.150) |
| Line count / truncation | 1 line, not truncated, both sides |
| Pixel smoke (non-text, per-channel delta > 16) | 0.76% < 2% limit; whole image 1.49% |
| Cards, icons, clip | visually identical (see PNGs); same design-package code paths |

## What failed, and what Lib needs

### 1. Tile geometry at pixelsPerUnit 1: off by up to 1.25 units — rounding policy, not expressiveness

- Phenomenon: all four tiles differ at the default grid (lib 211x212 vs ac
  210.75x210.75, y off by 0.5); on a 1/16-unit grid every value matches AC
  exactly.
- Root cause: `YogaLayoutEngine` maps `pixelsPerUnit` to
  `YGConfigSetPointScaleFactor`, so at 1 unit-per-pixel Yoga snaps every node
  to whole units. AtomChat's own layout math is pure float and never snaps.
  The page is fully expressible; the two stacks just disagree about when to
  round.
- Lib needs: a documented `pixelsPerUnit` contract (hosts pass the real
  physical-pixel ratio, never 1 for float UI) — or a rounding-mode switch on
  the engine for hosts that lay out in float units. No new layout API needed.

### 2. Shaped line width drifts +0.25 units per CJK glyph — subpixel advances off in the shaping path

- Phenomenon: "外观" 37.5 (AC) vs 38.0 (lib); "聊天与通知" 93.75 vs 95.0.
  Consistent +0.25/glyph, so centered labels sit ~0.6 units off and long
  strings drift linearly (~1.3% on a 5-glyph title).
- Root cause (probed in-fixture, `textMetrics.subpixelProbe`): the same `Font`
  measures `measureTextWidth("外观") = 37.5` but `shapeLine("外观") = 38.0`
  with `Font.isSubpixel = false`. Skija's shaper rounds per-glyph advances to
  integers when subpixel is off; `TextService` opens fonts without enabling
  subpixel, while AtomChat's renderer measures and draws through the unrounded
  path.
- Lib needs: `TextService` (or `FontRegistry.openFont`) to set
  `Font.setSubpixel(true)` — one line — or an explicit policy flag. Until then
  any ported screen reflows slightly.

### 3. Relayouting a live engine at a new pixelsPerUnit crashes on the fabric target

- Phenomenon: second `layout()` on the same engine with a different
  `pixelsPerUnit` throws `NoSuchMethodError:
  org.lwjgl.util.yoga.Yoga.YGNodeMarkDirtyAndPropogateToDescendants(long)`.
- Root cause: `ui/build.gradle` compiles the adapter against lwjgl-yoga
  3.3.1, where the binding still carries the "Propogate" spelling; LWJGL
  renamed the method ("Propagate") in 3.3.3, which is what the 1.21.1-fabric
  target jar bundles. AtomChat never calls Yoga, so this latent mismatch had
  no witness until this fixture. The same call shape is reachable in production
  the moment a host resizes a window (viewport change with a new pixel ratio)
  or drops a measure callback.
- Lib needs: per-target compile alignment — the adapter must compile against
  each target's locked yoga version (3.3.1 for forge, 3.3.3 for
  fabric/neoforge), or the adapter must avoid the renamed entry point. This is
  a release blocker for any Yoga-using consumer, found by direction B on its
  first run.

### 4. Layout expressiveness gaps (worked around, worth an API decision)

- Phenomenon: expressing the grid needed spacer nodes for the asymmetric
  insets (top gap s(10) vs side inset s(6.4)) and a measure callback returning
  height = width for the square tiles; `LayoutStyle` has no margins, no
  absolute positioning, no aspect ratio, and forbids wrap+gap.
- Root cause: deliberate minimalism in the locked adapter, but square tiles
  and inset bands are the bread and butter of this UI.
- Lib needs: decide between `aspectRatio` + edge margins in `LayoutStyle`
  (Yoga supports both natively) and keeping the adapter minimal with a
  documented recipe. Either works today; the recipe belongs in quickstart.md.

### 5. Variable-font weight is silently dropped (informational on this screen)

- Phenomenon: `bundled.otf` carries one variation axis; AtomChat's
  `FontManager` clones it to wght 500 for body text, `FontRegistry` loads the
  default instance. The settings home uses only the true-bold file, so this
  screen cannot see the difference — chat body text would.
- Lib needs: a weight/variation option on `FontSource`/`FontRegistry` before
  any screen with body text is ported (matches direction F's font contract).

## What this buys for the extraction decision

- Do not extract more screen composition on a hunch: the shared layer
  (tokens, cards, icons) already reproduces the shipped look pixel-faithfully.
- The three fixes above (subpixel shaping, per-target yoga compile,
  pixelsPerUnit/rounding contract) are small and bounded; after them, a
  settings page can be ported onto AtomLib with no visual drift beyond AA.
- The fixture stays in AtomChat as the regression harness for exactly this
  comparison; re-run it after each Lib fix and the failed set should shrink
  from 7 to 4 (geometry) to 0.

## Method notes and limits

- Text comparison is typography geometry only (line count, line width, line
  height), per the direction-B contract; no glyph pixels were compared. The
  pixel smoke excludes the icon+label bands; inside them the two stacks use
  different text draw paths, so their pixels are expected to differ until
  fix 2 lands.
- AC side ran with `PreviewLanguage` (offline translation table) and a
  default-config `AtomChatConfig`; hover/press animation pinned to rest, so
  both sides render the static state.
- The full AC test suite was not re-run; only the settings render tests
  (`SettingsHomeReplicationTest`, `SettingsStepperRenderTest`) plus the
  classpath change (`testRuntimeOnly 'org.lwjgl:lwjgl-yoga:3.3.3'` in the
  fabric platform, needed because the AtomLib input nests its Yoga binding
  behind jarJar where the test classpath never unpacks it).
