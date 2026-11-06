# GlStateScope 每面板帧计数（2026-11-05）

本轮仅交付诊断。游戏中每帧调用次数、性能改善、画面验收均未测。
后续实测已完成计数：[11/05 10:34 采样结果](gl-scope-sample-20261105-103433.md)。23,368 个完整面板帧全部 1 次、无嵌套，capture p50 2.1335ms；性能改善和画面验收仍未测。本文前述“未测次数”保留为诊断交付时的历史状态。
交接正本：`D:/AtomChat-Main/docs/HANDOFF-panel-performance-codex.md:206`。
issue：https://github.com/NoWordz/AtomLib/issues/1 。192 个纹理单元本轮已在隐藏 GL fixture 中实测；“游戏每帧 4–7 次”仍是待验证估计。

## 实现与口径

- `platforms/shared/src/main/java/com/atom/lib/client/render/GlStateScope.java:168`：capture 入口按 JFR 事件开关记录每次尝试；包含构造失败，计时只覆盖 capture，不包括作用域内绘制和 close 恢复。
- `platforms/shared/src/main/java/com/atom/lib/client/render/GlScopeDiagnostics.java:15`：事件 `com.atomlib.GlStateCapture` 默认关闭、无调用栈；开启后记录单调时钟起点、captureNanos、进入前 depth、实际纹理单元/目标数量、success。
- `tools/analyze_gl_scopes.py:31`：按 `eventThread.javaThreadId` 及半开区间 `[startedAtNanos, startedAtNanos + panelNanos)` 关联 `com.atomchat.PanelSegments`。真实宿主字段定义见 `D:/AtomChat-Main/shared/src/main/java/com/atom/chat/diagnostics/FrameSegments.java:64`。这是面板帧，不能替代整个 Minecraft 帧。
- `tools/analyze_gl_scopes.py:77`：同线程首末 capture 起点形成保守覆盖包络，完整落在包络内才给出 `captures`；边界帧为 null，`observedCaptures` 仅表示已观察数量。拒绝缺事件、重复帧、重叠区间、`jdk.DataLoss`；完整的零工作帧允许为 0。
- `nestedCaptures` 是 depth>0 的次数，不代表这些 scope 都可安全合并。`captureNanos` 为 CPU 墙钟，不是 GPU 时间。窗外数量可能包含其他线程、初始化、销毁、模糊或其他面板外工作，不能直接算到游戏帧里。
- 前提：自定义事件在整个采样期间连续开启、threshold=0；动态开关、正阈值、过滤或事件缺样会使计数不完整。边界未知帧还保留部分时间/深度数据，只用于诊断，不用于完整帧汇总。开启打点有开销，先用来数次数，不把它当严格性能 A/B。

既有 fastState、MAX 缓存及 AtomChat 点阵实验原样保留；本轮不裁剪 GL 状态，不改恢复语义，不改 AtomChat 源码/依赖锁，不提交、不发布、不启动游戏。

## 证据

所有新日志与产物放在 `D:/AtomLib/build/scope-diagnostics-20261105/`，没有用默认 evidence 目录覆盖旧结果。

- `JfrProbe.java:1` 三行本机探针，`probe.json:23` 确认 `@Timespan` JSON 实际为 `PT0.123456789S`，线程字段为 `eventThread.javaThreadId`；按真实格式解析，避免把时长字符串当整数。
- `red.log`：旧 capture 已实际执行 3 次，事件数 expected 3 / actual 0，行为红灯；`green.log` 对应通过。
- `analyzer-red.log`：真实时长格式与采样边界回归红灯；`zero-frame-red.log`：完整零工作帧回归红灯；`analyzer-final.log` 四项通过。
- `unit-suite.log` 首跑缺显式测试字体失败；`unit-suite-font.log` 补仓库要求的 `-PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf` 后 root suite 成功。包含已有跳过项和 UP-TO-DATE，不声称所有项目都强制重跑。
- `fabric-gl.log` 61 项全量隐藏 GL 通过；之后新增一项真实导出 fixture，`fabric-final.log` 定向 4 项通过，并通过 build/verifyArtifact。
- `forge-final.log`、`neoforge-final.log`：分别运行 hiddenGlTest 全量（各 62 项）、build、verifyArtifact。最终状态见 `verification-summary.json`。
- `fabric-gl/scope-pairing.jfr` → `pairing-events.json` → `pairing-analysis.json:3`：真实 GL capture，合成宿主 panel 事件；1 个完整区间、2 次 capture、1 次嵌套、192 个纹理单元、2 次窗外调用。验证配对链路，绝不冒充 Minecraft 实测。
- 独立 code-reviewer 三轮审查：修复真实 JFR 时长格式、采样边界误报零、完整零工作帧被拒绝；最终无遗留 P1/P2。连续采样前提仍适用。
- 部署辅助脚本另补 `deployment-safety-tests.log` 四项模拟测试：WMI 错误/空或无效输出均停止，预检失败刷新旧成功回执，检测到游戏时不复制实例；实际只读 WMI 探针也已验证。异常处理覆盖预检、进程检查、复制阶段。

## 用户采样

三端候选包已复制到 `build/scope-diagnostics-20261105/candidates/`。
首次部署前 WMI 检查发现 Minecraft 客户端 PID 37984，先停止替换。之后只读检查确认游戏已退出，最终于 2026-11-05 10:21（上海时间）部署三端诊断版；`build/scope-diagnostics-20261105/deployment-receipt.json:3` 为 `deployed_and_hash_verified`，三端备份与部署哈希均通过，errors=[]、wmiGameProcesses=[]。三份原包备份后缀为 `.bak-scope-20261105-102111`；AtomChat JAR 哈希及其他现有模组文件元数据不变。

本轮已部署，无需重跑。以后重建需要重新部署时，先关闭游戏，再执行以下脚本；脚本会再次检查游戏进程、备份旧包、校验 SHA-256 和其他现有模组文件元数据，并校验 AtomChat JAR 哈希不变。它只替换三端 AtomLib，不改配置、不启动游戏：

```powershell
python D:\AtomLib\build\scope-diagnostics-20261105\deploy.py
```

采样配置 `build/scope-diagnostics-20261105/scope-profile.jfc` 从现有 `atomchat-panel.jfc` 生成，保留原面板事件，并启用 capture 事件与 0ns 阈值。
采样脚本 `build/scope-diagnostics-20261105/auto-record-scopes.ps1` 是原脚本副本，只改 JFC 路径、输出目录；PowerShell 语法解析通过，未运行等待/启动游戏流程。

11/05 用户反馈原入口闪退：Windows PowerShell 实际执行策略为 Restricted。已提供桌面 `C:/Users/NIUQU/Desktop/开始面板采样.cmd`，双击即可，不需要用户手输命令。入口只对本次进程设置执行策略，结束保留窗口；提示中文，启动错误与录制输出写到 `build/scope-diagnostics-20261105/samples/采样启动日志.txt`。真实 Windows PowerShell `-CheckOnly` 及 CMD 入口副本 smoke 均通过，未执行录制或启动游戏。

推荐双击桌面入口，再自行启动 Fabric CCB 实例，按原流程开关面板、切页。脚本最多等待启动 10 分钟，开始后最多录制 3 分钟。也可以在 PowerShell 执行下面命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\AtomLib\build\scope-diagnostics-20261105\auto-record-scopes.ps1
```

分析命令（将路径换为新 JFR 文件）：

```powershell
& 'C:\Program Files\Java\jdk-21.0.11\bin\jfr.exe' print --json --events 'com.atomlib.GlStateCapture,com.atomchat.PanelSegments,jdk.DataLoss' 'D:\path\recording.jfr' | Set-Content -Encoding utf8 D:\AtomLib\build\scope-diagnostics-20261105\events.json
python D:\AtomLib\tools\analyze_gl_scopes.py D:\AtomLib\build\scope-diagnostics-20261105\events.json --output D:\AtomLib\build\scope-diagnostics-20261105\analysis.json
```

需保留原有 AtomChat debug/JFR 诊断开关。旧 JFR 未包含新 capture 事件，不能追溯出精确调用次数。得到真实计数后再决定 scope 合并方案；状态子集裁剪还需证明第三方绘制的修改范围和 GL 恢复安全。

重新生成 JFC：

```powershell
& 'C:\Program Files\Java\jdk-21.0.11\bin\jfr.exe' configure --input D:\AtomChat-Main\docs\verification\2026-11-04-panel-sampling-kit\atomchat-panel.jfc --output D:\AtomLib\build\scope-diagnostics-20261105\scope-profile.jfc '+com.atomlib.GlStateCapture#enabled=true' '+com.atomlib.GlStateCapture#threshold=0 ns'
```
