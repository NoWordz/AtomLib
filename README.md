# AtomLib

AtomLib is an independent Minecraft UI foundation built on Skija and Yoga. It
provides a single render bridge, host-owned font loading, unified layout/hit
geometry, components, overlays, scrolling, HUD/crop interaction, an optional
shell and local development previews.

The project is currently `0.1.0-local`. It is not published to Maven or a mod
store. AtomLib-authored code is Apache-2.0; allowlisted AtomChat adaptations
remain MIT and third-party licenses are retained under `licenses/`.

## Platforms

| Minecraft | Loader | JVM | Gradle | Yoga |
|---|---|---|---|---|
| 1.20.1 | Forge 47.4.10 | 17 | 8.8 | LWJGL 3.3.1 |
| 1.21.1 | Fabric Loader 0.16.9 / Loom 1.7.4 | 21 | 8.8 | LWJGL 3.3.3 |
| 1.21.1 | NeoForge 21.1.235 / MDG 2.0.141 | 21 | 9.2.1 | LWJGL 3.3.3 |

Each loader has one distributable JAR. It contains AtomLib's Java modules,
Skija, HumbleUI types, the matching Yoga Java API and Windows x64/ARM64 native
packages. Minecraft's own LWJGL, fonts and business state stay with the host.
Linux and macOS clients are outside the first release's native support scope.

## Install

Install the matching AtomLib platform JAR in `mods/` alongside the mod that
consumes it. Forge and NeoForge declare the client-only dependency in their
metadata. Fabric consumers check a pinned client requirement and present a
clear missing-dependency message. Dedicated servers do not initialize AtomLib's
native runtime.

Fonts are host resources. AtomLib never bundles a TTF/OTF/TTC/WOFF file. The
host registers bytes with `FontRegistry` and passes the selected body `FontId`
through `HostContext`.

## Build

Use the JDK documented in the table above. On Windows:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-17.0.18'
.\gradlew.bat :core:test :runtime:test :ui:test :devtools:test --no-daemon
python tools/run_packaging.py all --jobs 1
```

The local Maven repository is `build/test-maven`; the six standalone example
consumers compile only against published platform coordinates.

See [getting-started.md](docs/getting-started.md), [api-contracts.md](docs/api-contracts.md),
[ai-guide.md](docs/ai-guide.md), and `docs/STATUS.md` for the implemented scope
and known limits.

## Optional design package

`com.atom.lib.design.atomchat` carries AtomChat's own look — tokens, drawing
primitives, card surfaces and icon paths — for mods that want to match it.
It is a separate import, not a requirement: the render bridge and Yoga layout
work without it. See [design-package.md](docs/design-package.md).
