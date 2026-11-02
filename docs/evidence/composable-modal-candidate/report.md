# 可组合模态 M1 候选

状态：CPU 功能候选已通过主代理阶段复核，见 [review.md](review.md)；非游戏实用性验收。M2 原版物品图标与消费者游戏入口未实施。

`ModalDialog`、`ModalOptions` 和 `ModalCloseReason` 接入单层会话；内容复用普通控件、焦点与滚动。库拥有的 `ModalLayout` 生成表面、标题、正文和动作的 `PresentationFrame`；`SettingsShell` 与测试宿主消费同一路径。普通窗口固定头尾，极短窗口整区滚动。旧 `ConfirmationDialog` 保留其行为和界面；`modalNode` 遇到新模态明确拒绝。自定义皮肤通过新的默认方法实现表面。

真实 CPU 测试覆盖重复 Enter、正反 Tab、自动滚动、已有底页滚动隔离、极短窗口、遮罩、Escape 尾事件、回调抛错、失焦、尺寸变化取消拖动、失败绘制保留旧命中以及自定义模态皮肤。运行期红绿包括异常回调释放 owner、保留槽位冲突、尺寸变化取消模态拖动、取消回调异常时不发布新命中框；另有停用滚轮转发的负控。日志在 `D:/AtomLib/build/modal-item-icons-execution-baseline/`，最终根测试见 `root-final5.log`。底页普通 Slider 的连续缩放契约保持不变。

M1 独立候选与三端验包/六消费者日志位于 `D:/AtomLib/build/composable-modal-candidate/` 和 `D:/AtomLib/build/modal-item-icons-execution-baseline/packaging-final3.log`。CreatorTrial 仅通过认证原始二进制 `implementation` 消费，证据位于 `D:/AtomLib-CreatorTrial/evidence/composable-modal-candidate/`；其历史 Loom 处理 sources 事故依旧披露。机器可读摘要为 `D:/AtomLib/build/composable-modal-candidate/results.json`。

未启动游戏或下载游戏资源；截图是 CPU 栅格，不含物品图标。真实 Minecraft 图标、游戏内输入/观感、性能、特殊渲染器、GL 和跨平台实机均 NOT_RUN。未提交、推送、发布、部署或升版。
