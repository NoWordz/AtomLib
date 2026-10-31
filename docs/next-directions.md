# Next directions

The three planned layers exist: the Skija render bridge, the Yoga layout engine
and the optional AtomChat design package. What is left is turning that into
something another author can pick up. The options below are independent; each
lists what it buys, what it costs and what it risks.

## A. Give AtomLib its own repository and CI  (recommended first)

Today AtomChat pins a 62 MB Maven input published as a pre-release asset on the
AtomChat repository, and AtomLib's own workflows have never run anywhere. That
makes the library's health depend on the consumer's pipeline.

- Work: create the repository, push `codex/atomlib-extraction`, let `build.yml`
  run on a real runner, tag `0.1.0-beta`, attach the three platform jars; then
  switch AtomChat to fetch the input from the new repository — update its
  resolve script (`gradle/atomchat-atomlib.gradle`), its CI download step, and
  re-pin the SHA-256 lock in `versions/atomlib.json`. During the beta, expect
  to re-pin after every rebuild of the same version tag; keep each re-pin in
  its own commit referencing the Lib build it came from.
- Buys: an independent version and release trail; a place for the input archive
  so AtomChat stops hosting it.
- Cost: half a day to a day. Risk: low-medium; the workflows have never run on
  a real runner, so budget for the first run failing.

## B. Prove the stack on one real screen

Redraw one AtomChat screen with `TextService` + Yoga + the design package inside
an offscreen test, then compare its geometry and pixels with what the mod draws
today. The differences are the honest list of what is still missing; nothing
gets extracted on a hunch.

- Work: reuse the existing offscreen surface harness, start with the settings
  home page (few elements, lots of text), keep a pixel tolerance for AA.
- Buys: evidence for or against further extraction; turns the six-item
  turnkey checklist into a pass/fail.
- Cost: 1-2 days. Risk: medium; may expose gaps in text measurement or
  font fallback that force a design change.

## C. Input adapter

A creator currently writes the mouse-to-`PointerEvent` mapping and the hit
table themselves. It is small, but every author gets it slightly wrong.

- Work: one small adapter per loader that turns screen callbacks into
  `InputDispatcher` calls, plus the recipe in the quickstart.
- Buys: removes the most common first-hour friction.
- Cost: about a day. Risk: low-medium; the callbacks differ slightly between
  1.20.1 and 1.21.1, so keep the shared part free of Minecraft types.

## D. Publishing beyond GitHub

Maven coordinates and store pages, so the library can be declared rather than
downloaded by hand.

- Work: GitHub Packages or another Maven host, then Modrinth/CurseForge
  listings following the project description rules.
- Buys: normal dependency management for consumers.
- Cost: about a day plus review. Risk: low. Needs A first.

## E. Multi-consumer coexistence  (deferred — not scheduled)

Deferred until a second consumer actually exists; with only AtomChat using
the library there is no real scenario to validate against. When the second
consumer appears, run this before that consumer's first release:

Two mods loading one AtomLib jar share a single render bridge. Nothing today
proves that the second consumer's screen and the first one's shutdown path do
not interfere.

- Work: two throwaway consumers in one client, open both screens, exit.
- Buys: confidence that a shared library jar is safe to install once.
- Cost: half a day. Risk: low-medium.

## Suggested order

A, then F, then B, then C, then D. A is a prerequisite for everything
else; F settles the font contract before B pins its pixel baseline; B is the
fork in the road — its evidence decides whether more of AtomChat moves
into the library, and possibly forces API design changes, so it runs before
any new API surface (C) is built. D is a packaging step once the artifact is
stable; its Maven host choice (leaning Modrinth Maven for anonymous reads)
can wait until then. E stays deferred until a second consumer exists.

## F. Default font package  (runs right after A)

AtomLib does not bundle fonts; the host does. AtomChat gets good text because
it ships a subsetted Noto Sans SC, but a library consumer starting from zero
gets tofu for all CJK. Decide the library's font story once:

- Work: move AtomChat's two subsetted OTFs plus `OFL.txt` into the library
  byte-for-byte and load them as the default; host-supplied fonts still win.
  Extend the subset with two cheap ranges: Latin-1 supplement (currently 20/96
  — `é`, `ü` tofu today) and the kaomoji symbol ranges (`¯` U+00AF is missing).
  Publish the subsetting script in `tools/` and document the default charset
  spec: GB2312 complete (6763 hanzi + 682 symbols, verified), kana, symbols.
- Explicitly out of the default package: GBK/GB18030, Hangul, emoji. Full
  Noto Sans SC is 10-16 MB per weight; a library cannot promise "never tofu"
  for arbitrary Unicode and should rely on the host/system fallback chain
  instead. Hosts needing more coverage rerun the subsetting script.
- Emoji note: Noto Sans SC has no emoji codepoints at all, so past tofu there
  is not a subsetting gap. Color emoji (Noto Color Emoji, CBDT, ~10 MB) is a
  separate direction if it is ever wanted.
- Buys: CJK-capable text out of the box for every consumer; a documented
  charset contract; AtomChat switching to the library font keeps rendering
  byte-identical, which also keeps B's pixel baseline honest.
- Cost: half a day. Risk: low.

## Explicit non-goals

Rebuilding a component or shell framework, cloning AtomChat's screens for third
parties, and shipping a compatibility shim for the unpublished `0.1.0-local`
builds. Each of those was either rejected during extraction or has no consumer.
