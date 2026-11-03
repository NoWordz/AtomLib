<p align="center"><img src="platforms/1.21.1-fabric/src/main/resources/atomlib.png" width="160" alt="AtomLib logo"></p>

<h1 align="center">AtomLib</h1>

<p align="center"><em>Skija &amp; Yoga for Minecraft.</em></p>

## What it is

AtomLib is a client-side UI library for Minecraft mods. It combines [Skija](https://github.com/HumbleUI/Skija) drawing with [Yoga](https://www.yogalayout.dev/) layout, then provides the Minecraft render bridge, text services and optional controls needed by a host mod. The host still owns its screens, data and behavior. [AtomChat](https://github.com/NoWordz/AtomChat-Mod) is one consumer.

The repository currently contains a **0.3.0 development candidate**. The latest public release is [0.1.0-beta beta3](https://github.com/NoWordz/AtomLib/releases/tag/v0.1.0-beta-beta3); it does not include every feature described here. There is no public Maven repository yet.

## Features

- **Rendering bridge** — draw Skija into the Minecraft client and coordinate with vanilla rendering on the render thread.
- **Layout and input** — build Yoga box trees; use the same presentation frame for drawing, clipping and hit testing.
- **Text and images** — load host fonts or bundled Noto Sans SC subsets; measure and draw text, manage images and optionally preprocess oversized images.
- **Controls** — compose buttons, sliders, toggles, choices, scrolling and dialogs with host-owned state and actions.
- **Optional appearance** — use instance-owned AtomDefault skins and presets, or supply a custom skin; the older AtomChat design API remains available.
- **Motion and materials** — frame-clocked transitions, springs and an optional backdrop blur service.
- **Minecraft items** — prepare native item icons for control slots; real-game and compatibility testing remains incomplete.

## Compatibility

| Minecraft | Loader | Java | Bundled client natives |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.10 | 17 | Windows x64, Windows ARM64 |
| 1.21.1 | Fabric Loader 0.16.9 | 21 | Windows x64, Windows ARM64 |
| 1.21.1 | NeoForge 21.1.235 | 21 | Windows x64, Windows ARM64 |

These are the project's build targets, not a promise that every loader version or hardware combination has been tested. Windows ARM64 is packaged but not validated in-game. Linux and macOS client natives are not included. AtomLib is a client dependency; a dedicated server does not need to initialize its rendering runtime.

## Install

1. Choose an AtomLib JAR matching your Minecraft version and loader, and install it alongside the consuming mod in `mods/`.
2. Use a compatible version of the consuming mod. An accepted version number alone does not guarantee API compatibility.
3. For development, build a local Maven input and declare the matching target coordinate. The [getting started guide](docs/wiki/getting-started.md) and [quickstart](docs/wiki/quickstart.md) show the setup.

## Architecture

| Part | Responsibility |
| --- | --- |
| `core` | Geometry, layout contracts, input records and motion primitives |
| `runtime` | Skija resources, text, fonts and image services |
| `ui` | Yoga layout, presentation frames, optional controls and skins |
| `platforms` | Forge, Fabric and NeoForge client bridges and distribution JARs |

The host owns the screen, business state and lifecycle; AtomLib supplies reusable rendering and interaction tools. See the [architecture guide](docs/wiki/architecture.md) and [host controls guide](docs/wiki/controls.md).

## Known limitations

- This is an early development candidate, not a stable API commitment. Independent host adoption, long-running GPU lifecycle, GUI scales, ARM64 and shader/mod combinations need further real-game testing.
- Text is plain left-to-right UI text, not a complete bidirectional, IME or rich-text engine. Word and limited CJK line-breaking policies have separate boundaries.
- Yoga integration does not expose absolute positioning or relative offsets; use host drawing or supported layout composition where needed.
- Item icons and backdrop blur have explicit render-state and lifetime contracts. Their hidden-GL or offline tests are not proof of compatibility with every Minecraft renderer.

## Building from source

Use JDK 17 for common module tests, JDK 17 for Forge and JDK 21 for Fabric/NeoForge. Dependencies must be available to Gradle; `--offline` works only after they are cached.

```powershell
.\gradlew.bat :core:test :runtime:test :ui:test --no-daemon "-PatomlibTestFont=C:/path/to/font.ttf"
python tools/run_packaging.py all --jobs 1
```

See the [wiki](https://github.com/NoWordz/AtomLib/wiki) ([source](docs/wiki/Home.md)), [examples](examples/), [release checklist](docs/releasing.md) and [project status](docs/STATUS.md).

## License

AtomLib-authored code is [Apache-2.0](LICENSE). Allowlisted AtomChat adaptations retain MIT terms; see [NOTICE](NOTICE), [licenses](licenses/) and [provenance](docs/provenance.md) for third-party attribution.

Copyright © 2026 E33EPUS & NoWordz
