# AtomLib status

## 当前状态（2026-11-03）

- 当前源码版本 `0.3.0`，上轮提交 `eca7ed9` 和 `d9c1da1` 已推送；默认分支已由 `codex/atomlib-extraction` 重命名为 `main`。当前提交以 Git 查询为准。公开发行仍是 `0.1.0-beta` beta3，0.3.0 尚未发布，也没有公开 Maven 仓库。
- `eca7ed9` 的 GitHub Actions 已通过：公共模块、Windows 原生测试、三端 hidden GL 和打包。上轮本地强制重跑 core 24 / runtime 48 / ui 137，全部通过；三端 JAR 与 AtomChat 0.5.0 的关键 API 预检各 8 项通过。不同验证的范围不能互相替代。
- 三套实例仍为 AtomChat 0.5.0 与本地 AtomLib 0.3.0 候选；AtomLib 旧包与部署哈希见 `D:/AtomChat-Backups/2026-11-03-atomlib-host/20261103-173741-616635/deployment.json`。本轮未启动游戏；真实 GUI 缩放、图标、长期 GPU 生命周期、Windows ARM64 和第三方渲染模组组合尚待验证。
- 面向宿主的文档源位于 [docs/wiki](wiki/Home.md)。下方为历史检查点，其中提交、推送与部署陈述只描述当时状态。

## 宿主按钮与英文换行（2026-11-03）

现有 `AtomChatControlSkin` 与 `AtomDefault.skin()` 已能用共享文字角色统一按钮字号，并通过 Yoga 居中无图标按钮的文本布局框。新增宿主式离屏用例覆盖中英文按钮布局框、背景光栅和点击，未验证实际字形墨迹的视觉居中；字号定制仍需自定义 `ControlSkin`。详见 [控件说明](wiki/controls.md)。

新增可选 `TextBreakPolicy.WORD`，优先按空白断行，长单词按 grapheme 拆分；默认策略不变，亦不等同 Unicode 完整断行或 CJK_UI 禁则。Terra 执行 runtime 48、ui 137 项通过；主代理复跑两个定向用例（runtime 命中缓存、ui 实际执行），并核对三端新 JAR 的制品检查通过。AtomChat 对三端新 JAR 的 API 预检各 8 项通过，无跳过；不是完整游戏运行兼容证明。

三实例已替换本轮 0.3.0 同版本库包并与 AtomChat 0.5.0 配对，旧库及新旧哈希见 `D:/AtomChat-Backups/2026-11-03-atomlib-host/20261103-173741-616635/deployment.json`。源码与 0.3.0 版本调整未提交、推送或发布；未启动游戏，真实 guiScale、绘制和输入仍待验证。下节部署记录保留此前 0.3.0 构建历史，不是当前包哈希。

## 0.3.0 本地候选与三实例部署（2026-11-03）

按用户要求将工作区版本升至 `0.3.0`，三端安装包采用 `atomlib-<Loader>-<Minecraft>-v0.3.0.jar`，Maven 坐标仍为 `com.atom.lib:atomlib-<目标>:0.3.0`；三端元数据简介为 `Skija & Yoga for Minecraft.`。构建、制品检查及 Maven 与安装包哈希一致性验证通过；隔离副本中的六个 Maven 示例消费检查均通过，示例没有 JUnit 测试。制品检查器 34 项、消费者检查器 2 项及示例守卫 3 项通过。

AtomChat 以现有 beta3 锁编译，并在三端对实际 0.3.0 JAR 运行关键 API 预检；三端全量测试分别为 Forge 1610、Fabric 1625、NeoForge 1610，各有 4 项跳过，零失败或错误。三端制品检查通过。已将 AtomChat 与 AtomLib 配对部署到 1.20.1-main、1.21.1-CCB、Mechanomania-航空学，备份与哈希记录位于 `D:/AtomChat-Backups/2026-11-03-atomlib-030/20261103-170157-794/deployment.json`。Forge 实例原先没有可见 AtomLib JAR，本次补装。未启动 Minecraft；guiScale、视觉、输入、真实 FPS 和运行兼容性仍待验收。

公开发行仍是 `0.1.0-beta` beta3；0.3.0 是本地候选，未推送或发布。下方保留较早历史记录，其版本及提交状态不代表当前状态。

## 库侧离线收束（2026-11-03，已做范围限定的独立复核）

图片预处理支持宿主显式从 ImageStore 接入，旧入口不变；覆盖真实 GPU 图片回退和超预算源图。修复已展示滚动帧二次缩放丢失滚动快照，离屏验证绘制、点击、滚轮和逆变换；详细合同与限定见[证据](evidence/atomdefault-image-20261103/report.md)。

主代理对最终源码强制重跑公共测试 core 24 / runtime 47 / ui 136，共 207 项实际执行通过；独立核对三端最终 JAR 哈希、制品扫描、旧公开声明及旧消费者有限运行，未发现差异。另从隔离工作区重跑六个独立 Maven 消费者，三端 JAR 与交付候选逐个哈希相同，六项均通过。Terra 执行的三端各 47 项 hidden GL 与 1 项 shader 故障回退通过；主代理独立跑过前一候选三端完整 GL、修正后的模糊探针三端定向 GL 和新 GPU 图片回退 NeoForge 定向 GL。NeoForge 工具链部分命中缓存；样本和候选摘要见[证据](evidence/atomdefault-image-20261103/report.md)。未启动游戏，真实 guiScale、视觉、动效、真实图片/GPU 长时、ARM64、Iris/Gnetum 与 AC 运行组合均待验收；旧拆分组绘制不自动共享透明图层。未部署、提交、推送或发布。

## 独立复核与中断状态（2026-11-03）

本节最初复核后，Terra 再次尝试继续，但服务端返回 429 而中断。期间新增 `ImageStore.draw` 的可选降采样接线和 GPU 回退测试；主代理对最新源码强制重跑公共模块 core 24、runtime 45、ui 134 项，全部通过。Forge、Fabric、NeoForge 最后各 46 项隐藏 GL 和 shader 故障回退及制品验包通过，部分构建任务命中缓存；随后修正模糊探针参数并在三端各实际运行两项定向 GL 测试，见[证据](evidence/backdrop-timing-20261103/report.md)。三端与 beta3 及此前 0.2.0 的旧公开声明对照未发现缺失。原 `ImageStore.draw` 行为不变，是否预处理由宿主选择；没有真实游戏和长期资源验证。

对 MuoniumPlayer 渲染性能交接做只读核对：尚不能将用户观察到的帧率差归因于某一个组件；优先研究状态捕获、模糊及文本，但不能直接缩减状态保护或重用旧帧。独立 CPU raster 探针确认 AC 现有 NeoForge 0.4.0 JAR 的单源超预算场景会返回已关闭图片，详见[复现记录](evidence/atomchat-image-quality-20261103/report.md)；AC 并行工作区未修改，实际游戏触发概率未知。

Terra 的图片预处理和像素密度阶段因服务端 429 限流中断，工作区保留，不视为整份方案完成。主代理复核发现设置组入场时背景与控件分别混合导致透明度叠加；`SettingsShell` 候选改为共同合成，并增加透明背景及不透明背景的像素回归。旧的独立 `SettingsGroup.paint` 和 `UiSession.paint` 两次绘制不能自动合并成一个组图层，宿主需要使用 `SettingsShell` 的共同合成路径或自行管理合成。

主代理随后发现装饰回调会泄漏画布变换，按失败用例修复并重跑定向测试。该检查点公共模块强制重跑 core 24、runtime 39、ui 134 项，均通过；Forge、Fabric、NeoForge 分别通过 44 项隐藏 GL、shader 故障回退及验包。与 beta3 及实施前 0.2.0 对照，三端旧公开类及公开/受保护成员声明无缺失，不能代替实际客户端启动；后续最新结果见本节开头。单独调用的限制、图片服务及大范围代码仍需进一步独立审查，游戏和用户视觉未验收。无提交、推送、发布或部署。

## 图片与像素密度续轮（2026-11-03，未完成）

新增宿主持有的可选 CPU 光栅降采样缓存、离屏画质/资源测试、密度与点击坐标用例，以及不依赖聊天业务的离屏性能夹具。来源、预算、调用边界、原始样本和限制见 [记录](evidence/atomdefault-image-20261103/report.md)。后续已增加 ImageStore 可选重载，旧调用不会自动启用；这不证明真实游戏收益。

**历史记录：**独审发现组入口透明度重复合成；`SettingsShell.paint` 已在共同图层修复，并补不透明背景、多组、失败还原用例。旧 `SettingsGroup.paint` 与旧 `UiSession.paint` 的分离调用仍不会自动合成单个透明图层；当前 `GroupEntranceOpacityReviewTest` 验证的是 `SettingsShell` 路径，最新公共测试结果见本文件顶部。实际游戏、视觉、Iris/Gnetum、GPU 长时生命周期、ARM64、HDR 和真实游戏性能均未验证。

## 动效与模糊后续实施（2026-11-02，待独立复核）

AtomDefault 开关采用皮肤配置的实例弹簧，颜色进度保持 0–1；旧皮肤默认仍用原插值。页面入场通过可见项快照、显式帧时间和 `PresentationFrame` 的共同几何接入控件与设置页；支持中断、减少动效、滚动与裁剪。参数及实机手感待用户验收，页面内容和切换动作仍由宿主拥有。

显式背景服务与三端入口支持可选的编码 RGB 加权亮度上限；旧入口默认不限制。GPU 读回覆盖明暗、颜色、源/目标 alpha 和其他 FBO；故意损坏 shader 的独立测试覆盖失败后不写目标和面板实底。`drawBlurredBackground` 显式关闭本次创建的 `ImageFilter`；共享阴影缓存提供协调方在渲染线程调用的 `releaseShadowFilters()`。接口与限制见 [controls](wiki/controls.md)、[backdrop](wiki/backdrop.md)。

公共模块 24 / 30 / 126 项通过；三端各 44 项隐藏 GL 和 1 项 shader 故障回退通过，三端隔离 JAR 验包通过。旧版消费者和上一阶段编译的自定义皮肤各在三端新 JAR 上完成有限运行检查；旧公开 API 声明对照未发现缺失。命令、缓存命中、来源依据和摘要见[实施基线](atomdefault-baseline-2026-11-02.md)。

本轮未运行 Minecraft，Iris/Gnetum、多模组共存、真实设备/ARM64、视觉与动效手感均待验收。图片降采样、GPU 长时间资源统计、像素密度统一及消息性能夹具不属于本阶段已完成内容。AtomChat 只读；未部署、提交、推送或发布。详细命令与结果见下方实施基线中的后续实施记录。

## AtomDefault 进行中（2026-11-02，待独立复核）

用户已批准方案中的 AtomLib 实施。新入口提供 12 个不可变通用配色与实例皮肤；浅色预设模态表面随面板色变化，旧皮肤构造器保持原外观。新皮肤不自动应用面板透明度、轮廓色或模糊；选择哪套默认外观、完整主题覆盖及用户观感仍待验收。阴影滤镜淘汰先解除画笔引用再关闭缓存句柄，剩余静态资源的关闭与 GPU 长时资源占用仍待验证。

独立目录 `build/atomdefault-work` 中，公共模块 core 24、runtime 30、ui 106 项通过；三端新 JAR 构建和验包通过，三端各 9 项隐藏 GL（背景与控件）通过。beta3 编译的独立 Java 消费者在各端新旧 JAR 上运行通过；`javap` 对照未发现 beta3 或实施前 0.2.0 公开类的公开/受保护声明缺失。范围、命令、摘要与局限见[实施基线](atomdefault-baseline-2026-11-02.md)。以上不构成真实 AtomChat 启动或视觉通过。

此历史检查点尚未实施入场、弹性开关和亮度限制；其后续进度以上一节为准。有来源疑点的入场表达没有复制；图片降采样、设备像素统一和可重复消息性能夹具仍待完成。AtomChat 未修改，真实客户端、用户视觉、ARM64、发布与部署均未执行。

## 工作区版本与后续方案（2026-11-02）

工作区版本已按用户要求从 `0.1.0-beta` 升为 `0.2.0`；公开发行仍为 beta3。升版不表示本地候选的真实游戏、性能或观感验收已经完成。此历史检查点的 AtomChat 当时严格要求 `0.1.0-beta`；当前未提交的 AtomChat 工作区已调整版本判断，实际兼容仍待验证。AtomDefault、动效、性能反哺和兼容策略见[实施方案](superpowers/plans/2026-11-02-atomdefault-and-atomchat-feedback.md)。

三端 `0.2.0` 本地构建及 `verifyArtifact` 通过，隔离 Maven 位于 `build/version-0.2.0-check/maven`；六个独立示例的 `verifyConsumer` 通过，三端库 JAR 与示例 JAR 版本元数据均核对。制品检查器 34 项、消费者检查器 2 项、示例守卫 3 项通过；构建有 Gradle 缓存命中。本轮检查未启动游戏或部署，Git 提交和推送状态以实际查询为准；AtomChat 兼容能力仍需实施验证。

## 阶段收口：可组合模态与原版图标代码候选（2026-11-02）

用户要求到此收口、更新方案并本地提交。M1 可组合模态和 M2 控件图像槽、三端原版离屏适配、CreatorTrial 薄 Screen/F8 接线已实现，并通过限定范围独立复核；实际游戏图标、输入、资源重载与性能仍未验收。此检查点更新覆盖下文候选的“尚未提交”历史表述；没有推送、发布、部署或升版，公开 beta3 与 AC 输入保持不变。

独立重跑为 UI 97 项、Fabric 图标相关 7 项和消费者 9 组 JavaExec 场景；三端 JAR 摘要及每份 sources JAR 的 111 个共享/平台源码核对一致。三端完整 GL 各 38 项、验包和六消费者是实施者执行记录，未混称主代理全部重跑。见[当前方案](superpowers/plans/2026-11-02-composable-modal-and-item-icons.md)、[M1 复核](evidence/composable-modal-candidate/review.md)、[M2 复核](evidence/modal-item-icons/review.md)。

当前候选 `build/modal-item-icons-candidate/maven` 保留；摘要、关键证据和消费者源码快照见 `docs/evidence/modal-item-icons/`。纯 GL 图标 prepare+close 热 CPU 中位约 9–10ms，下一步先拆分成本；这不是实际物品 GPU 耗时，不能称性能可用。按用户要求不再自动启动游戏。

以下章节保留各轮历史事实，当前范围以上述检查点为准。

用户提供的模组 logo 已替换三端 `atomlib.png`：原1254×1254、1,202,584字节，缩放优化为256×256 PNG、47,867字节（减少96.02%）。原图和处理摘要保存在 `assets/branding/`；三端元数据原已指向该路径。Logo 打包候选隔离在 `build/logo-candidate/maven`，原交互候选与输入锁保留，未发布或部署。

## 本地交互修复检查点（2026-11-01）

本轮修复 SettingsShell 短窗口确认窗越界，以及缩放反算误差导致的文字换行；样例另修 Fabric Mixin 包覆盖业务入口的启动崩溃，并限制 F8 只从主菜单或无其他页面时打开。代码、测试、候选身份和实际执行范围见[修复记录](evidence/interaction-fixes/report.md)。新候选 `build/interaction-fixes-candidate/maven`，旧候选和公开 beta3 保留，未迁移 AC。

用户已认可当前复刻的还原程度，本轮停止追逐像素差异。曾启动独立开发客户端并验证入口初始化，随后用户停止电脑操作并明确要求只修复；未完成游戏内交互验收，不再自动启动客户端。AC 的模糊性能结论不直接用于 Lib，本轮未改模糊缓存或重新宣称性能通过。

## 本地候选：完整调整页与库内背景磨砂（2026-11-01）

按已授权方案（已完成，见 Git 历史）实现设置行、共用组卡片、页头/分段导航、Action/Choice/Disclosure、单垂直滚动、缩放同几何与 AC 确认皮肤；独立样例组合五滑条、两组折叠及可达重置按钮，真实内存业务变化影响页面。主题/显示导航明确禁用，未实现整套外观模块或聊天编辑器。

背景磨砂及三份原创 GLSL 进入 Lib：每实例区域抓取/四轮滤波、显式源/目标、目标 alpha 写保护、圆角合成、状态/资源生命周期及三端薄入口。失败返回诊断，由库面板实底降级，不宣称普适 shader-mod 兼容。API 见 [controls](wiki/controls.md) 和 [backdrop](wiki/backdrop.md)。

候选仅在 `build/appearance-candidate/maven`；样例新锁 `evidence/appearance-candidate/input-lock.json`。公开 beta3、版本、AC 锁、前轮 Maven 和历史证据保留。实际测试、图像、摘要、保护项及未覆盖范围以[本轮报告](evidence/appearance-replication-and-backdrop/report.md)和 results.json 为准。未提交/推送/发布/部署/启动游戏；主代理独立审查和用户观感另列。

主代理独审已完成并修复三处滚动捕获/末端问题和样例跨 Screen 模糊帧号接线。独审三项真实红绿、最终根124项、三端GL各31项和样例21项通过，六消费者及最终制品/输入负控通过；源码及消费锁已按修复重建。当前状态为 `REVIEWED_PENDING_GAME_AND_USER_VISUAL`，详见[独审证据](evidence/appearance-replication-and-backdrop/review/results.json)。截图仍存在排印、间距和确认窗比例差异，未代签观感。

性能也未验收：独立热调用64×64场景在当前显卡/驱动下，CPU提交中位约6.2ms（32次），成本尚未拆分，不能用小区域或GPU query数值宣称廉价。具体逐端数据随GL证据保留，不等于游戏帧成本。

## 上一候选：真实字体与最小交互切片（2026-11-01）

用户已批准最小方案（已完成，见 Git 历史），实现与本地验证完成；主代理独立审查已收束并接受修复，待游戏和用户观感验收。公开版本／asset／beta3锁不变，没有提交、推送、发布、部署或启动游戏。

- 新增可选 `ui/controls`：Slider、Toggle、单ConfirmationDialog、UiSession及完整输入适配；宿主仍拥有页面结构、业务值与保存。无新模块、壳、路由、滚动或编辑器。
- 默认常规体真实400、AC正文角色500、标题700；原字体字节不变。新增有限CJK_UI。prepare(viewport)先取消旧密度手势，再更新模型和Yoga；成功绘制后才发布命中快照。
- 修正release回调前清capture；覆盖拖动重排、preview回声／外部覆盖、一次提交／回滚、repeat／尾事件、模态关闭隔离、回调异常与双会话隔离。新材质使用实例style，旧全局接口保留。
- 独审修复：卡片内非按钮区域只吞事件、普通取消隔离held-key尾序列、画与点同PresentationFrame层序；ControlSkin.modalDialogStyle拥有模态规格，通用会话无AC依赖；失焦漏keyup后返焦新操作可用。主代理3项原断言未改，真实红绿与补充边界归档见报告。
- 最终复跑：core21／runtime30／ui57共108项全绿；三端打包及六Maven消费者全绿；每端23项hiddenGlTest共69项全绿；独立样例9栅格＋5交互测试、12张新PNG。JUnit共191项；Python单测33＋2＋3另计。库13项行为负控＋消费者1项认证和1项行为负控均拒绝缺陷并按字节恢复。
- 候选仅在 `build/controls-candidate/maven`；样例单独candidate-lock与摘要隔离构建。默认Maven90文件、样例53份历史证据、前轮字体门修复保留。完整命令、产物SHA、文件清单与范围见[实施报告](evidence/minimal-controls-and-fonts/report.md)及同目录results.json。
- 当前候选／输入zip／源码快照已重新生成，样例锁schema仍为1，汇总results schema为2。历史205保护项中仅原AC BannerProbe被外部提交删除，204项不变；不恢复外部工作。用户模糊讨论文档只按原字节随源码保留，本轮未实现模糊。
- 样例有编译验证的F8／R入口，但未开游戏；本轮由库维护者接线，不称新盲测。隐藏GL不代替MC实例／ARM64／专服／真实多mod共存或用户视觉验收。原完整SDK目标尚未整体交付。
- AC在本轮期间有外部新提交至观察值 `3f509f2b`；本实施者始终只读AC，不能宣称其全树不变。前轮审计修复被外部提交收录，原Banner探针已不在原路径；详细变动与保护限制见报告。

## 公开 beta3 检查点（以下为现役发行物状态）

Status date: 2026-11-01. Version `0.1.0-beta`, current release
[v0.1.0-beta-beta3](https://github.com/NoWordz/AtomLib/releases/tag/v0.1.0-beta-beta3).

The library now has its own public repository and CI (six jobs: pure-Java
contract tests, Windows source suite, hidden GL round trips on Xvfb for all
three loader targets, and the packaging/verifier job). AtomChat consumes the
pinned input archive from this repository's releases; the old secret-based
fetch on the AtomChat side is retired.

Scope boundary: the original 2026-10-29 creator SDK proposal has been reduced
to a foundation. Components, scrolling, modal/focus management, the shell and
devtools are outside the current release. Its original ease-of-use criterion
of building a complete UI from only data, actions and page structure remains
unmet; implementing the current scope is not delivery of the full original
proposal.

Implemented since the extraction checkpoint: Yoga bindings compiled per
target behind a generated bridge (3.3.1 vs 3.3.3 renamed symbols, the resize
crash blocker), subpixel text shaping in `FontRegistry`, the decided
`LayoutStyle` expansion (`Edges`, per-edge margin/padding, `aspectRatio`,
`align`/`justify`; absolute positioning is a non-goal), the default font
package (same-source Noto Sans SC VF re-subset, 7671 codepoints, zero drift
against the previous subset including at wght 500, Latin-1 and kaomoji ranges
added), `ScreenInputAdapter` with zero Minecraft types, and the
pixels-per-unit contract in `render-bridge.md`.

Evidence: the offscreen settings-home replication test lives in AtomChat and
its reports live in [evidence/settings-home-replication/report.md](evidence/settings-home-replication/report.md);
line-width drift is fixed to 4/4, the four remaining geometry deltas are the
documented 1u rounding contract.

What has not: no external consumer yet, ARM64 hardware untested, D (Maven
host beyond GitHub releases) and the deferred E (multi-consumer
coexistence) not started. See [next-directions.md](next-directions.md) for
the live plan and its status notes.

## Local post-beta3 corrections (2026-11-01)

The distribution verifier now requires both default fonts exactly once at
the top level; the existing path, magic and duplicate-entry checks remain.
Consumer mode still permits jars without fonts. The quickstart now dispatches
release to `ScreenInputAdapter` before vanilla, matching press and drag, so
vanilla cannot consume the release before the captured library target sees
it. README and authoring guidance now describe the bundled defaults and host
overrides instead of the superseded no-font rule.

Validation: `python -B tools/test_verify_artifacts.py` initially failed all
nine missing-font subcases and now passes 33 tests; `test_verify_consumers.py`
and `verify_ui_examples.py` pass 2 and 3 tests. Local mutation of the
authenticated beta3 jars (remove regular, bold, or both on each loader)
changed from nine false accepts to nine rejections, while all three original
jars pass. A JShell probe using the actual Forge 1.20.1 container, beta3
adapter and the current quickstart release expression changed from stale
capture and a swallowed next click to one release, cleared capture and a
passed-through next click.

These are source corrections after beta3, not a new release or rebuilt
distribution. Full loader builds, game sessions and ARM64 hardware were not
retested for these corrections.
