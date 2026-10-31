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
  run on a real runner, tag `0.1.0-beta`, attach the three platform jars.
- Buys: an independent version and release trail; a place for the input archive
  so AtomChat stops hosting it.
- Cost: half a day. Risk: low.

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

## E. Multi-consumer coexistence

Two mods loading one AtomLib jar share a single render bridge. Nothing today
proves that the second consumer's screen and the first one's shutdown path do
not interfere.

- Work: two throwaway consumers in one client, open both screens, exit.
- Buys: confidence that a shared library jar is safe to install once.
- Cost: half a day. Risk: low-medium.

## Suggested order

A, then C, then B, then E, then D. A is a prerequisite for everything else; C
removes the first-hour friction; B decides whether more of AtomChat should move
into the library; E matters as soon as two consumers exist; D is a packaging
step once the artifact is stable.

## Explicit non-goals

Rebuilding a component or shell framework, cloning AtomChat's screens for third
parties, and shipping a compatibility shim for the unpublished `0.1.0-local`
builds. Each of those was either rejected during extraction or has no consumer.
