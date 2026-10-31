# AtomLib status

Status date: 2026-10-31. Version remains `0.1.0-local`.

Implemented: T0 provenance gate, locked LWJGL Yoga selection, three independent
loader builds, seven embedded dependencies per target, one shared Skija render
bridge, host-font/runtime resources, unified Yoga layout and presentation
frames, the optional AtomChat design package, six Maven consumers, and pinned CI
input handling.

The earlier generic component, overlay, scroll, interaction, view-host, style
and shell layer has been deleted. `ui` now contains Yoga layout and the optional
`com.atom.lib.design.atomchat` package only. `devtools` is deleted and is no
longer part of the CI jobs, build input or distribution.

本轮提取后只保存本地开发检查点，尚未最终验收。单元测试、示例编译和打包由主代理统一执行；本文件不把计划中的检查写成已通过。

`docs/verification-2026-10-30.md` 记录的是删除前范围的证据，不适用于当前模块清单；`docs/reviews/ui-view.md` 审查的组件/浮层/视图代码已删除。当前开放问题以主代理的最终构建与用户验收为准。没有推送、远端创建、外部 Maven 发布、商店发布或部署。
