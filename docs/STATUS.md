# AtomLib status

Status date: 2026-10-31. Version `0.1.0-beta`.

Implemented: one shared Skija render bridge with a borrowed-frame contract,
LWJGL Yoga with runtime native extraction, host-owned fonts and text layout
(`TextService`), immutable presentation frames, the optional AtomChat design
package, three loader jars with Windows x64/ARM64 natives, six standalone Maven
consumers, and pinned input handling for the AtomChat build.

The generic component, overlay, scroll, interaction, view-host, style and shell
layer is gone. `ui` contains Yoga layout and `com.atom.lib.design.atomchat`
only; `devtools` is deleted. Release metadata is in place (authors, Apache-2.0,
description, placeholder logo and repository URL).

What has actually been verified: `:core:test`, `:runtime:test` and `:ui:test`
pass locally (44 tests), `run_packaging.py platforms/verify/consumers` pass for
all three targets, and AtomChat main builds and tests against these jars with
its CI green.

What has not: no external consumer has used the library, this repository has
no remote so its own CI has never run, ARM64 hardware is untested, and the text
stack has only been exercised by generated examples. Repository URL and logo
are placeholders. See [next-directions.md](next-directions.md).
