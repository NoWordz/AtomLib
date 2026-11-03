# AtomDefault 实施基线（2026-11-02）

## 后续阶段：动效与模糊（2026-11-03 补记）

本阶段在上述首轮修改之上继续，输出隔离到 `build/atomdefault-motion-20261102/`，没有写入先前的 `build/atomdefault-work/` 或历史同名候选。开关使用独立旋钮弹簧与有界颜色进度；首次显示、隐藏后恢复、连续反转、禁用动效、键盘事务及真实像素过冲均有测试。弹簧示例参数采用本机 animate 技能的通用物理示例（质量 1、刚度 100、阻尼 10，换算阻尼比 0.5），停摆阈值由皮肤显式提供为 0.5 秒；这是候选手感而非用户视觉验收，也未复制 Dango/UiStagger 的实现或数值。

`PageEntrance` 接受显式帧时间、调用方提供的当时可见根和可调时长/错峰/距离；同帧幂等，重入切换立即替换快照，关闭动效结束入场。其缓动取 animate 技能所列 cubic-bezier `(0.23, 1, 0.32, 1)`；绘制、命中、焦点、裁剪和滚动条状态使用同一帧几何。`SettingsShell` 支持内容组/行的入场图层，允许动画时输入；层叠透明度与真实游戏观感仍待用户确认。使用合同与例子见 [controls.md](wiki/controls.md)。

背景合成提供可选编码 RGB 加权亮度上限，限制发生在与目标混合之前；旧入口默认 1。三端隐藏 GL 读取亮/暗与彩色四轮模糊、源和目标 alpha、区域外以及第三 FBO；单独的无效 shader 资源夹具实际验证 `SKIPPED`、目标不写和 SettingsShell 不透明实底，隔离类加载器另验证缺失 shader。见 [backdrop.md](wiki/backdrop.md)。真实游戏、HDR、其他显卡/驱动和运动背景未验证；不得将本轮视为性能验收。

用户指出的 `SkiaDraw.drawBlurredBackground` 问题已复现并修正：关掉 Paint 后 Java `ImageFilter` 仍保持自己的原生引用，旧实现连续 8 次绘制留下 8 个 Java 句柄；新实现按 Paint、filter 顺序关闭，真实引用计数、画布像素和 GPU 重绘通过。`releaseShadowFilters()` 明确释放共享滤镜缓存、可重复调用并可重新绘制；仅当协调方确认所有宿主停止使用共享缓存后调用，不能由单个屏幕擅自全局关闭。长时间 GPU 资源统计与进程退出顺序仍待实机验证。

验证命令：公共模块执行 `./gradlew.bat --no-daemon --project-cache-dir build/atomdefault-motion-20261102/project-cache -I build/atomdefault-motion-20261102/isolate.gradle :core:test :runtime:test :ui:test -PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf`，core 24、runtime 30、ui 126 项通过，0 失败/错误/跳过；最后一次 core/runtime 为 UP-TO-DATE，本轮此前实际执行。三端分别以各自隔离的 `--project-cache-dir`、`-PatomdefaultOutput` 和 `-PatomlibRenderEvidence` 执行 `:distributionJar :verifyArtifact :hiddenGlTest :hiddenGlBrokenShaderTest`，各端隐藏 GL 44 项及损坏 shader 1 项通过。Forge JDK 17；Fabric/NeoForge JDK 21；构建有 UP-TO-DATE 和 NeoForge 工具链缓存命中。`python -B tools/test_verify_artifacts.py`、`tools/test_verify_consumers.py`、`tools/verify_ui_examples.py` 分别 34、2、3 项通过；shader 更新后首次例子守卫因摘要旧值失败，同步 `assets/MANIFEST.sha256` 后通过。

实际 JAR SHA-256：Forge `0434a5c84681427ee5fc8e505c22ca4df22dac5211c1a8820083891ef66c7c45`，Fabric `30dfa85b9127ce5abd7c14520a3314ccb6a6abd22f030dcba25488951d6e1c58`，NeoForge `99e21d08f2e27dd363544e6be277f43de26b5f0642455825f1a9a469c9434c0d`。它们是隔离构建物，不是发布物。`javap -protected -s` 分别与 beta3、实施前 0.2.0 和首阶段候选对照，三端均未发现旧公开类及公开/受保护成员缺失或类声明变化；实际 beta3 编译消费者的几何、弹簧、旧 ripple 用例在三端新包运行通过。另以首阶段包编译的自定义 `ControlSkin` 在三端新包上运行，验证新默认方法不启用弹簧并转发旧绘制方法。脚本、消费者源码和结果位于本阶段隔离目录；这些是有限二进制验证，不代表 AtomChat 本体或游戏已启动。

AtomLib 工作区起点 `878ccc4cc5080b74546b0c3f892d647dce5598d0`，AtomChat 只读观察 `6452920ab60e46bfafd1a18191ed2e9725ce24ca`，后者存在用户授权的未提交依赖改动。方案开头“待确认”的旧状态已由本次用户批准实施覆盖；不授权改动 AtomChat、部署、启动游戏或发布。

以下均为本机文件的 SHA-256，不根据文件名推断公开发行身份。beta3 三项与 AtomChat `versions/atomlib.json` 锁一致；0.2.0 是实施前候选，不是已发行物。测试环境 Windows x64、Java 21.0.11；Java 17 编译目标以实际构建结果为准。

| 平台 | beta3（`build/test-maven`） | 0.2.0（`build/version-0.2.0-check/maven`） |
| --- | --- | --- |
| Forge 1.20.1 | `19bcf14a064b85c46771c586f29d7444b5bfffa39cf4545fb0a0a4a67cdd5c0e` | `2775edfe2a1c1add13529edb46b5b2da749c317cb7176d3ea6dc4a2061d71a0b` |
| Fabric 1.21.1 | `2d0040c9de92d2270f7815a74a4c2f0b7f5393379324364546f02ef0cececaef` | `5fc00b623f922375045721bdbfb070bd7262166da65518f2d705efa2c6089fc3` |
| NeoForge 1.21.1 | `f211ce9ba146f7f7c1672806f1fd64efb2c9e59647b7ae4b42a35eb85af0519b` | `1074259c0cb988da580244e487280b15e726ee7a238d0ac5e324b3dc7a23188b` |

`build/atomlib-delivery-20261030-final` 下三份 AtomChat 0.4.0 JAR 分别为 Forge `ad6f16936c4945337ada37c8f1c45f1b4bee55f1d3247bb7ebe7c878eb237d17`、Fabric `9fb727d6b51efd58677ef9b12870d2f09cc9a47b7005a16075b67d862a9bf4ac`、NeoForge `cdeeca18b80d96f0645951c6ccd952e3c7ff826a3f440e5ec26882851fbe85dc`。这些是历史制品，不推断其与当前源码完全对应。仅发现制品和摘要不等于运行兼容：三端真实客户端、实际运行时资源、已编译消费者配对及方法/字段链接仍待验证。

另外只读检查了 AtomChat 各平台 `build/atomlib-consumer/libs/atomchat-*-v0.4.0.jar`，Forge SHA-256 `a22e6e5d1ba01d28159f15aa72b1ee38155d13fb478b240f37523c7c2060a8e1`、Fabric `8b5fb2f2ee52ee0244c2fd2e5d3b4e574ff1d85ccf36a60bca38c1d4fed6b0e5`、NeoForge `d58edf3c4ffa00ca8648e65c9fead2d8045c29d01456b33a66901bb733fbe81f`。用 JDK 21 `jdeps --ignore-missing-deps -verbose:class` 枚举静态指向 `com.atom.lib` 的类，再比对上述实际 Lib JAR 的 ZIP 条目；每端均有 11 个引用类，beta3 和实施前 0.2.0 中均存在。记录与脚本位于本次隔离目录 `build/atomdefault-work/class-baseline.json`、`class_baseline.py`。此检查不解析方法、字段或反射，不检查动态资源，也没有运行旧 JAR。

AtomChat 当前未提交的 `docs/STATUS.md` 另记载三端 beta3 与 0.2.0 公开/受保护成员比对无缺失；那是对方工作区的既有检查，本次没有重跑该成员比对。当前源码的启动检查已移除版本相等判断，具体状态以 AtomChat 工作区为准；它尚未构成完整能力检测。

## 本次代码与验证

新入口和用法见 [design-package.md](wiki/design-package.md)。明暗对照图在 `build/atomdefault-work/previews/minimal-controls.png`、`raven-controls.png`，只包含离屏滑条和开关；透明背景显示随查看器而异，不能代替用户视觉验收。`ControlVisual.position` 目前严格限制在 0..1，旧皮肤的颜色和几何也消费同一值：直接把过冲弹簧接入会违反现有合同。因此本次只验证已有帧时钟，不擅自宣称弹性开关已实现。页面绘制、命中、焦点与裁剪的转场合同、页/行中断及滚动行为仍须设计与验证。

公共模块实际执行 `./gradlew.bat --no-daemon --project-cache-dir build/atomdefault-work/project-cache -I build/atomdefault-work/isolate.gradle :core:test :runtime:test :ui:test -PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf`：core 24、runtime 30、ui 106 项，0 失败/错误/跳过。最后一次调用 core/runtime 为 UP-TO-DATE；此前本次完整运行实际执行。首次漏传字体参数造成 runtime 10 项失败，补参数后通过。新增配色入口、浅色模态、滤镜淘汰均先复现预期失败再修正；帧行为测试是对现有行为的刻画。

三端分别使用独立 `-PatomdefaultOutput=...` 与 `--project-cache-dir ...` 执行 `:distributionJar :verifyArtifact`，Forge 使用 JDK 17，Fabric/NeoForge 使用 JDK 21；均通过，NeoForge 使用工具链缓存。新 JAR 的 SHA-256：Forge `d12d35be0db6883cf1121798f8ec243cbbf825aab73291e1e0953a056bd714be`，Fabric `c62282034d7fe7f3e01bc669a83e48ec7f82bfdf8217bd13a9462ed54dcc097f`，NeoForge `8d98406a4ef4814a17a16561fe8b2a0288cfe4b52ca86cba9d85900b051cf12c`。它们是本次隔离输出，不是已发布版本。

`javap -protected -s` 对实际 beta3 与新 JAR 比较公开类声明及公开/受保护成员签名和描述符：Forge/NeoForge 各 68 类、735 成员，Fabric 69 类、737 成员，没有缺失或类声明变化；对实施前 0.2.0 候选分别为 125/1155、126/1157、125/1155，也未发现缺失或类声明变化。脚本及结果位于 `build/atomdefault-work/{compare_public_api.py,public-api-comparison.json,compare_candidate_api.py,candidate-api-comparison.json}`；不覆盖反射、动态资源、运行时所有调用或行为。三端各用相应 beta3 编译 `OldConsumer.java`，分别以 beta3 和新 JAR 运行，几何、弹簧与旧全局 ripple 检查均通过；此消费者不是 AtomChat。

三端分别以独立 `-PatomlibRenderEvidence=...` 运行 `:hiddenGlTest --tests com.atom.lib.client.render.BackdropHiddenGlTest --tests com.atom.lib.client.render.BackdropIndependentReviewTest --tests com.atom.lib.controls.ControlsHiddenGlTest`，每端 9 项通过，不启动 Minecraft。测试覆盖目标 alpha、区域外像素、源 alpha、部分 GL 状态、实例隔离及现有控件绘制；不覆盖新增亮度上限或动态背景实机。Fabric 的 64×64、32 次提交采样报告中位数约 4.92ms、p95 约 5.88ms；Forge 约 4.93/5.90ms，NeoForge 约 5.23/5.95ms。它们是该夹具的 CPU 提交时间，未分段，不是游戏帧耗时或优化收益；GPU query 及设备信息见各端隔离证据目录。

## 来源准入

| 候选 | 来源与依据 | 处置 |
| --- | --- | --- |
| 主题色 | AtomChat `shared/src/main/java/com/atom/chat/theme/ThemeService.java`，观察版本 `6452920`，文件最近提交 `4f78ab7a727213c3641e1eb9bbc09a398c9b32df`；根许可证 MIT，原文 `licenses/AtomChat-MIT.txt`，现有 `docs/provenance.md` 第 3、6 节 | 仅抽取通用颜色及材质事实，保留原 MIT 许可；聊天气泡、配置与动作不迁移。 |
| 页面入场 `UiStagger` | AtomChat `3b47f632` 历史注释自述逐字取自 DangoMusic `Ui.kt`；`d833327e` 删除说法不证明来源已闭合；DangoMusic 根许可 GPL-3.0 | 不复制表达和数值，必要时只记录行为合同并独立设计。 |
| AC `SpringAnim` 后续自计时改动 | `d833327e` 删除自述 Dango 关联；早期自有实现记录于 `docs/provenance.md`，但不能据此自动放行所有后续改动 | 不迁移有疑点的自计时表达；沿用 Lib 自有显式帧时钟并单独验证。 |
| 图片 `ImageQuality` | AtomChat 当前源码及方案中的生命周期缺口；本次未完成许可与所有权验证 | 不迁移实现或阈值。 |
| 探针和性能基准 | AtomChat 记录的历史探针不等于可复跑夹具 | 不迁移旧脚本；须先固定输入、成本与资源所有权。 |
| 背景模糊 | Lib 自有 raw GL 候选；旧 AC 刷新门会造成运动背景拖影 | 不移植旧缓存门；GPU 读回、性能分段和实机运动背景均待验证。 |

原 `SkiaDraw.BLUR_FILTERS` 达到上限时只清空 Java 映射。`ShadowFilterLifecycleTest` 先证实当前画笔与缓存各持有原生引用，并复现淘汰后缓存句柄仍未关闭；修正后淘汰时先把画笔滤镜设为 null，再关闭缓存拥有的句柄，之后再次绘制。此处验证的是缓存淘汰，不是进程关闭时对静态画笔与剩余缓存的完整生命周期清理；真实 GPU 长时间运行和原生内存统计仍未覆盖。

## 本任务工作区改动文件（含首阶段）

- `assets/original-backdrop.json`
- `docs/STATUS.md`
- `docs/backdrop.md`
- `docs/controls.md`
- `docs/design-package.md`
- `docs/superpowers/plans/2026-11-02-atomdefault-and-atomchat-feedback.md`
- `gradle/render-adapter.gradle`
- `platforms/1.20.1-forge/src/main/java/com/atom/lib/client/render/MinecraftRenderBridge.java`
- `platforms/1.21.1-fabric/src/main/java/com/atom/lib/client/render/MinecraftRenderBridge.java`
- `platforms/1.21.1-neoforge/src/main/java/com/atom/lib/client/render/MinecraftRenderBridge.java`
- `platforms/shared/src/main/java/com/atom/lib/client/render/backdrop/BackdropRenderer.java`
- `platforms/shared/src/main/resources/assets/MANIFEST.sha256`
- `platforms/shared/src/main/resources/assets/atomlib/shaders/backdrop/composite.frag`
- `platforms/shared/src/test/java/com/atom/lib/client/render/BackdropHiddenGlTest.java`
- `platforms/shared/src/test/java/com/atom/lib/controls/ControlsHiddenGlTest.java`
- `ui/src/main/java/com/atom/lib/controls/Control.java`
- `ui/src/main/java/com/atom/lib/controls/ControlSkin.java`
- `ui/src/main/java/com/atom/lib/controls/Toggle.java`
- `ui/src/main/java/com/atom/lib/controls/UiSession.java`
- `ui/src/main/java/com/atom/lib/design/atomchat/AtomChatControlSkin.java`
- `ui/src/main/java/com/atom/lib/design/atomchat/SettingsGroup.java`
- `ui/src/main/java/com/atom/lib/design/atomchat/SettingsRows.java`
- `ui/src/main/java/com/atom/lib/design/atomchat/SettingsShell.java`
- `ui/src/main/java/com/atom/lib/design/atomchat/SkiaDraw.java`
- `ui/src/main/java/com/atom/lib/layout/PresentationFrame.java`
- `ui/src/main/java/com/atom/lib/layout/ScrollViewport.java`
- `ui/src/test/java/com/atom/lib/controls/ControlMaterialIsolationTest.java`
- `core/src/test/java/com/atom/lib/motion/SpringFrameContractTest.java`
- `docs/atomdefault-baseline-2026-11-02.md`
- `platforms/shared/src/test/java/com/atom/lib/client/render/BackdropMissingResourceTest.java`
- `platforms/shared/src/test/java/com/atom/lib/client/render/BackdropShaderFailureTest.java`
- `ui/src/main/java/com/atom/lib/controls/ControlSpring.java`
- `ui/src/main/java/com/atom/lib/design/atomdefault/AtomDefault.java`
- `ui/src/main/java/com/atom/lib/design/atomdefault/AtomDefaultPalette.java`
- `ui/src/main/java/com/atom/lib/layout/EntrancePose.java`
- `ui/src/main/java/com/atom/lib/layout/PageEntrance.java`
- `ui/src/test/java/com/atom/lib/controls/AtomDefaultComparisonTest.java`
- `ui/src/test/java/com/atom/lib/controls/AtomDefaultModalTest.java`
- `ui/src/test/java/com/atom/lib/controls/ElasticToggleTest.java`
- `ui/src/test/java/com/atom/lib/controls/EntranceInteractionTest.java`
- `ui/src/test/java/com/atom/lib/controls/SettingsEntranceTest.java`
- `ui/src/test/java/com/atom/lib/design/AtomDefaultTest.java`
- `ui/src/test/java/com/atom/lib/design/BlurredBackgroundLifecycleTest.java`
- `ui/src/test/java/com/atom/lib/design/ShadowFilterLifecycleTest.java`
- `ui/src/test/java/com/atom/lib/layout/PageEntranceTest.java`
