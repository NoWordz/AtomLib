# Final verification, 2026-10-30

AtomLib version `0.1.0-local`; Java common bytecode 17. No release was made.

> 历史记录（2026-10-31 注）：本文统计包含当时仍存在的 `ui` 组件/浮层/壳层测试和 `devtools`。这些模块与测试已在后续提取中删除；本文数字仅作为删除前的证据保留，不代表当前发行内容或测试数量。

此为 Hubble 实施阶段证据。主代理随后发现未解决问题，当前状态以 `docs/STATUS.md` 和 AC 的 `docs/verification/2026-10-30-atomlib-checkpoint.md` 为准；不构成最终验收通过。

- Root standard suites: 116 tests passed (`core` 9, `runtime` 23, `ui` 82,
  `devtools` 2), with 0 failures/errors/skips.
- Hidden GLFW/OpenGL bridge suites: 11 tests per target, 33 total, all passed.
  Hardware: NVIDIA GeForce RTX 5060 Laptop GPU, OpenGL 3.3.0 driver 617.42.
- Artifact verifier self-tests: 29 passed. Consumer verifier tests: 2 passed.
  UI/asset/example guard: 3 passed.
- Six isolated consumer builds: 2 examples x 3 loaders, all PASS. They resolve
  only the published local platform coordinate and do not use AC source or
  project substitution.
- Three platform `build publishToTestRepository verifyArtifact` runs passed,
  including JarJar/Loom inclusion, PE x64/ARM64, version, registration,
  duplicate, font-magic, POM and unsafe-class checks.
- Gallery preview rendered at 320 and 640 widths from production components;
  output: `build/standalone/_devtools/preview/component-gallery.png`.

Final platform JAR SHA-256 values are recorded again in the paired delivery
under `D:/AtomChat-Main/build/atomlib-delivery-20261030-final/SHA256SUMS.txt` and in
AtomChat's `versions/atomlib.json`.

Limits: no Minecraft client/server launch, ARM64 hardware, IME, bidi/CJK line
breaking, Iris/shader compatibility, performance certification, alternate GPU
matrix or long-session leak proof. New changes did not receive a new independent
strong-model review. Existing foundation/UiView/render reviews remain scoped to
their recorded revisions and findings.
