# 独审修复：局部源码与回归验收

本轮只修改源码与测试；候选、锁、新图和正式报告尚未更新。未修改 AC 或模糊相关文档，未启动游戏。

## 源码

- UiSession：绘制按 PresentationFrame.paintOrder 遍历；overlay press/release 命中统一排除 dialog 卡片；取消活动事务时隔离已按住的键直至 release；窗口失焦提供独立物理跟踪清理。
- ControlScreenAdapter：窗口失焦先记录边沿，取消后 finally 清理物理跟踪。普通 ESC、密度取消保留尾键隔离。
- ControlSkin.modalDialogStyle(Viewport)：皮肤提供模态外层 style；UiSession 只居中组合。
- AtomChatControlSkin：接回原有 LIST_PAD_X/DIALOG_MAX_WIDTH，保持原几何。

## 新测试

- ReviewInputBoundaryTest：5 项，卡片 title/icon/body/padding、拖入/拖出、按钮、密度取消时多键尾序列。
- WindowFocusBoundaryTest：2 项，Enter/RIGHT 失焦未收到原 keyup，返焦新完整 down/up 可用。
- ModalSkinLayoutTest：1 项，完全不依赖 AC 的皮肤，2 种宽度 × 2 种视口、自定边距和高度，实际 Yoga 布局验证。
- IndependentReviewTest：主代理 3 项断言原样保留，SHA 校验通过。

## 实际红绿

1. presentation-order-red.log：单独新绘制顺序测试，1 test / 1 failure / 0 errors，像素断言失败。
2. boundaries-red.log：6 tests / 5 failures / 0 errors；非 AC 宽度、卡片区域、跨边界拖动、密度取消均为行为断言失败，按钮基线通过。
3. focus-reset-red.log：11 tests / 2 failures / 0 errors；其余修复后，仅返焦 Enter/RIGHT 新操作被旧吞键状态吃掉。
4. local-regressions-green.log：14 suites / 42 tests / 0 failures / 0 errors / 0 skipped。

最终运行命令（PowerShell 调 Git Bash）：

```powershell
& 'D:/Git/bin/bash.exe' -c 'cd /d/AtomLib && export JAVA_HOME="C:/Program Files/Java/jdk-17.0.18" && ./gradlew.bat :ui:test --tests "*IndependentReviewTest" --tests "*ReviewInputBoundaryTest" --tests "*WindowFocusBoundaryTest" --tests "*ModalSkinLayoutTest" --tests "*ModalInputSequenceTest" --tests "*SliderTest" --tests "*ToggleTest" --tests "*UiSessionTest" --tests "*SessionSnapshotTest" --tests "*FocusStateTest" --tests "*ControlMaterialIsolationTest" --tests "*SliderRenderTest" --tests "*ToggleRenderTest" --tests "*ConfirmationDialogRenderTest" --offline --no-daemon --console=plain > build/controls-review/local-regressions-green.log 2>&1'
```

仅局部 JUnit + CPU raster / Yoga；本轮未重新运行三端 hidden GL、样例消费或打包。旧候选不包含本轮修复，等待独审/讨论收束后统一生成。
