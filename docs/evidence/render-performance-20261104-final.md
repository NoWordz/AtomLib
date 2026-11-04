# Render performance final candidate: AtomLib

Date: 2026-11-04

Final source HEAD: `fd65d69e6830969a053912f94f9b03a7bd17ab43`. The plan baseline was `fba83a0`; the final source includes the reviewed empty-batch fast path and instrumentation tests. This document verifies that final source and does not replace `render-performance-20261104-followup.md`.

No product code was changed in this verification pass. No Minecraft process was started and nothing was deployed, published, committed, or pushed.

## Isolated source and candidate

The clean archive was produced with:

```powershell
git -C D:\AtomLib archive --format=zip --output=<evidence>/AtomLib-source.zip fd65d69e6830969a053912f94f9b03a7bd17ab43
Expand-Archive -LiteralPath <evidence>/AtomLib-source.zip -DestinationPath <evidence>/source
```

- Archive SHA-256: `623b9c0f17e45df4d03794daebc1422709d541ccd5b22c73b314c4f647e362e4`
- Source and hash record: `D:/AtomLib/build/render-perf-20261104/final-20261104-02/source-identity.json`
- Three copies of the distribution JARs are under `D:/AtomLib/build/render-perf-20261104/final-20261104-02/artifacts/`.

Final distribution JARs:

| Target | JAR | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Forge 1.20.1 | `atomlib-Forge-1.20.1-v0.3.0.jar` | 27,247,329 | `ea618c3537d22b5eca81a3c85cb984e5a4f259e91c938a4425a243a9ae7b3774` |
| Fabric 1.21.1 | `atomlib-Fabric-1.21.1-v0.3.0.jar` | 27,259,780 | `a1da0ff062a9b87a9eed8882ea9d1fdf5ce241336eaf1a806c04e7656b9308bc` |
| NeoForge 1.21.1 | `atomlib-NeoForge-1.21.1-v0.3.0.jar` | 27,254,234 | `59fa6ccc7c2b13255b2e60eabc4dbe41da541d14e6044a76e12b8930852683d9` |

These hashes match the earlier follow-up evidence, which is consistent with a reproducible final artifact.

## Build commands

Common modules were executed from the isolated Fabric platform project with JDK 21:

```powershell
java.exe -Dfile.encoding=UTF-8 `
  -classpath <source>\gradle\wrapper\gradle-wrapper.jar `
  org.gradle.wrapper.GradleWrapperMain `
  :core:build :runtime:build :ui:build `
  --offline --no-daemon --console=plain `
  -PatomlibTestFont=<source>/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf
```

Each platform used its own wrapper and required JDK. Fabric and NeoForge used JDK 21; Forge used JDK 17. The strict offline outer build was:

```powershell
java.exe -Dfile.encoding=UTF-8 `
  -classpath <source>\gradle\wrapper\gradle-wrapper.jar `
  org.gradle.wrapper.GradleWrapperMain `
  :build :publishToTestRepository :verifyArtifact `
  --exclude-task :verifyArtifacts --offline --no-daemon --console=plain `
  -PpythonExecutable=python `
  -PatomlibTestFont=<source>/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf `
  -PatomlibTestMavenRepo=<candidate-maven>
```

The Gradle log did not show `verifyArtifact` executing in this isolated invocation, so the final artifact assertion was run directly rather than inferred:

```powershell
python tools/verify_artifacts.py <distribution.jar> --loader <loader> `
  --version 0.3.0 --skija 0.143.18 --types 0.2.0 `
  --yoga <3.3.1-or-3.3.3> --icu 71.1 `
  --pom <platform/build/publications/mavenJava/pom-default.xml> `
  --module <platform/build/publications/mavenJava/module.json>
```

Logs are under `D:/AtomLib/build/render-perf-20261104/final-20261104-02/logs/`:

- `common-modules-test-build.log`: 233 tests, 0 failures, 0 errors, 1 skipped.
- `artifact-verification.log`: Forge, Fabric, and NeoForge each passed the direct recursive artifact check.
- `fabric-strict-offline.log`, `neoforge-strict-offline-retry.log`, `forge-strict-offline.log`: candidate assembly and publication commands exited 0.
- `fabric-build-publish-verify.log`: first Javadoc attempt failed because the JDK 17 Javadoc child used GBK for UTF-8 source. No source was changed; UTF-8 JVM options made the retry pass.
- `neoforge-platform-publish.log`: strict offline platform publication failed because `net.neoforged:neoform-runtime:2.0.18` had no usable offline cache entry.
- `neoforge-platform-publish-online-tooling.log`: the missing tool metadata was resolved by a non-offline tooling run, after which the NeoForge Maven coordinate was published. This step is not counted as offline evidence.

The three candidate Maven JARs were byte-identical to the distribution JARs. POM/module hashes and paths are recorded in `D:/AtomChat-Main/build/render-perf-20261104/final-20261104-02/dependency-verification.json`.

## Limits

This proves isolated source assembly, common-module tests, distribution contents, dependency registration, native architecture checks, shader/font inclusion, and Maven artifact identity for the listed targets. It does not prove Minecraft loading or visual correctness, game FPS, dynamic ItemStack rendering, GPU timing, ARM64 on hardware, Linux/macOS clients, or compatibility with arbitrary loader, shader, or rendering mods.

The empty-batch optimization is still supported by the earlier synthetic A/B only. This pass does not promote synthetic timing to a game-performance result and does not make a new root-cause or code-review claim.
