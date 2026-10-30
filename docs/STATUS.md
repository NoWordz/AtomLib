# AtomLib status

Status date: 2026-10-30. Version remains `0.1.0-local`.

Implemented: T0 provenance gate, locked LWJGL Yoga selection, three independent
loader builds, seven embedded dependencies per target, one shared Skija render
bridge, host-font/runtime resources, unified Yoga layout and presentation frames,
core components, overlays, virtual scrolling, text editing, HUD/crop interaction,
optional shell, backdrop blur, local inspector, gallery preview, six Maven
consumers, AC appearance-layout integration and pinned CI input handling.

本轮主代理复核后只保存本地开发检查点，尚未最终验收。Lib 标准 116 项强制重跑通过；三个 AC 目标构建/既有标准测试通过，产物守卫通过。

下个会话先修 AC 半屏切换导致布局区永久暂停输入（真实回归已复现），再处理 AC context 逃逸同步租借、Lib 客户端最终退出释放接线、Linux CI 的测试 native 配置。完整入口为 `D:/AtomChat-Main/docs/verification/2026-10-30-atomlib-checkpoint.md`，其中区分本轮重跑、既有证据与未覆盖内容。

Verification numbers from implementation are recorded in
`docs/verification-2026-10-30.md`; they do not override the open review findings.
The user authorized local checkpoint commits and temporary cleanup. No push,
remote creation, external Maven publication, mod-store publication or deployment.
