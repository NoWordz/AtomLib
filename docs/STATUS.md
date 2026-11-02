# AtomLib status

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

按[已授权方案](superpowers/plans/2026-11-01-appearance-replication-and-backdrop.md)实现设置行、共用组卡片、页头/分段导航、Action/Choice/Disclosure、单垂直滚动、缩放同几何与 AC 确认皮肤；独立样例组合五滑条、两组折叠及可达重置按钮，真实内存业务变化影响页面。主题/显示导航明确禁用，未实现整套外观模块或聊天编辑器。

背景磨砂及三份原创 GLSL 进入 Lib：每实例区域抓取/四轮滤波、显式源/目标、目标 alpha 写保护、圆角合成、状态/资源生命周期及三端薄入口。失败返回诊断，由库面板实底降级，不宣称普适 shader-mod 兼容。API 见 [controls](controls.md) 和 [backdrop](backdrop.md)。

候选仅在 `build/appearance-candidate/maven`；样例新锁 `evidence/appearance-candidate/input-lock.json`。公开 beta3、版本、AC 锁、前轮 Maven 和历史证据保留。实际测试、图像、摘要、保护项及未覆盖范围以[本轮报告](evidence/appearance-replication-and-backdrop/report.md)和 results.json 为准。未提交/推送/发布/部署/启动游戏；主代理独立审查和用户观感另列。

主代理独审已完成并修复三处滚动捕获/末端问题和样例跨 Screen 模糊帧号接线。独审三项真实红绿、最终根124项、三端GL各31项和样例21项通过，六消费者及最终制品/输入负控通过；源码及消费锁已按修复重建。当前状态为 `REVIEWED_PENDING_GAME_AND_USER_VISUAL`，详见[独审证据](evidence/appearance-replication-and-backdrop/review/results.json)。截图仍存在排印、间距和确认窗比例差异，未代签观感。

性能也未验收：独立热调用64×64场景在当前显卡/驱动下，CPU提交中位约6.2ms（32次），成本尚未拆分，不能用小区域或GPU query数值宣称廉价。具体逐端数据随GL证据保留，不等于游戏帧成本。

## 上一候选：真实字体与最小交互切片（2026-11-01）

用户已批准[最小方案](superpowers/plans/2026-11-01-minimal-controls-and-fonts.md)，实现与本地验证完成；主代理独立审查已收束并接受修复，待游戏和用户观感验收。公开版本／asset／beta3锁不变，没有提交、推送、发布、部署或启动游戏。

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
