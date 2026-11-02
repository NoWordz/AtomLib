# M1 主代理独立复核（2026-11-02）

结论：M1_CPU_REVIEWED。可组合模态的 CPU 交互、布局和认证二进制消费复核通过；本轮审查未发现新的可报告缺陷。完整方案仍未完成：M2 原版图标、消费者薄 Screen/客户端入口尚未实现，游戏、隐藏 GL 和性能未验证。

审查相对 `build/modal-item-icons-main-review/before/` 的实施前文件，而非把 Git 中全部历史 WIP 当作本轮改动。读取了 ModalDialog/Options/CloseReason/Layout、UiSession 差异、ControlSkin/AtomChatControlSkin/SettingsShell 接线、ComposableModalTest、ControlFixture，以及 CreatorTrial 的三个 Java 源文件、构建与锁。

实际执行：

- JDK17：`:core:test :runtime:test :ui:test --offline --no-daemon -PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf --console=plain` 成功，但任务均为 UP-TO-DATE。
- 因此追加 JDK17 `:ui:test --rerun-tasks`，其余参数相同，UI 94 项实际执行，失败/错误/跳过均为零。core 21、runtime 30 为既有 XML/缓存结果，本轮不称它们重新执行。
- JDK21：CreatorTrial `build trialChecks --offline --no-daemon --console=plain` 成功，8 组 JavaExec 场景实际执行；编译/运行 classpath 认证原始 Fabric JAR，`remapSourcesJar SKIPPED`。不把 JavaExec 组数称为 JUnit。

逐个重算三端 JAR、消费者 JAR、消费者锁、source.zip 和 input.zip 摘要，与子代理交付记录一致。逐份 sources JAR 对比 90 个 core/runtime/ui 共享 Java 文件，三端均无字节差异；实际三端 JAR 均含新模态类型。消费者 JAR 只有 TrialPage、TrialState 及 Item 三份业务 class，仍没有游戏入口。

直接查看了短窗 380×320、密度 1 的 CPU 图：标题、两行分类、首项和确认/取消可见；图片没有物品图标。此查看不构成整套视觉或游戏验收；六组尺寸/密度的末项可达性由消费者行为回归覆盖。

证据：`build/modal-item-icons-main-review/{root-validation.log,ui-executed.log,consumer-validation.log,artifact-review.json}`。子代理既有红绿/负控日志已查阅，本轮未重新执行这些变异。三端验包及六消费者为子代理执行的记录，本轮没有重复整套打包。

已关闭本轮实施代理以完成阶段复核；下一阶段继续沿用用户授权的同一 Terra 6.1 / xhigh，始终最多一个活跃代理。AC、旧候选与版本保持本轮既定保护边界，不启动 Minecraft，不部署、提交或发布。
