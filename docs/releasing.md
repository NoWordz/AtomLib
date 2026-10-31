# Releasing AtomLib

AtomLib ships one jar per loader and Minecraft version. Each jar bundles the
Java modules (core, runtime, ui), the matching Skija build with its Windows
x64/ARM64 natives, LWJGL Yoga and its natives, and the optional design package.
Nothing else is required at runtime.

| Target | Artifact | Java |
|---|---|---|
| 1.20.1 Forge | atomlib-1.20.1-forge | 17 |
| 1.21.1 Fabric | atomlib-1.21.1-fabric | 21 |
| 1.21.1 NeoForge | atomlib-1.21.1-neoforge | 21 |

## Version

The version lives in gradle.properties as atomlib_version and appears in the
jar name, the mod metadata and the published coordinate. It is currently a
pre-release (0.x). Treat the public surface as:

- stable within a minor version: the render bridge, Yoga layout, TextService,
  FontRegistry and the design package;
- free to change in a minor bump while the major version is 0.

Inside Minecraft, normal semver is not enough: a loader refuses a mod built for
another Minecraft version, and Forge/NeoForge compare declared dependency
ranges. Keep the Minecraft version in the artifact id and bump the AtomLib
version for every published build.

## Build and verify

~~~powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.11'
python tools/run_packaging.py platforms --jobs 1
python tools/run_packaging.py verify
python tools/run_packaging.py consumers --jobs 1
~~~

platforms builds and publishes the three jars to build/test-maven. verify
re-checks each jar's contents and nested natives. consumers compiles the six
standalone example projects against the published coordinates; it clears the
Loom remap cache first, so a republished same-version jar cannot produce a
false pass.

## Publish

The release workflow builds the three jars on windows-latest and attaches them
to a draft GitHub release. Publishing to Maven or a mod store happens outside
this repository and needs the owner's approval; the coordinates and metadata
are already correct, so a store upload is a packaging step, not a code change.

## Checklist before announcing a version

1. atomlib_version bumped in gradle.properties.
2. run_packaging.py platforms, verify and consumers all pass.
3. Jar contents checked: no component/overlay/shell classes, design package
   present, pack.mcmeta present, natives nested under META-INF/jarjar.
4. Each consumer mod updates the pinned SHA-256 of the jar it ships against
   (AtomChat keeps this in versions/atomlib.json).
5. Consumers clear Loom's remapped_mods cache after a same-version republish.
6. The three real clients start, open a screen and exit without a native leak.
7. Release notes name the Minecraft and loader versions each jar targets.

## What is not covered yet

Remote CI has not been run, ARM64 hardware has not been tested, and dedicated
servers must never load the client/native path. Do not call a build "tested"
beyond what these steps actually executed.
