# AtomLib 来源门（T0）

审计日期：2026-10-29。对象：`D:/AtomChat-Main` 的 `28fd6de0caf1133ffba97c63315d55bdf21aef48`。需求依据：`D:/AtomChat-Main/docs/superpowers/plans/2026-10-29-atomlib-extraction-and-yoga.md` 第 1、3、5 节与 T0，以及用户本次限定写范围。审计者：Codex。

本文件是**复制准入清单**，不是法律意见，也不是最终发行许可验收。已完成的工作是来源调查、许可原文归档与候选处置；没有迁入代码、图形、音频或字体，没有运行产品 build，没有生成基线文件，没有提交。T0 的构建／图像基线由另一执行者负责，本文件不宣称整个 T0 已通过。

## 1. 不可改变的边界

- AtomLib **新写自有代码 Apache-2.0**；允许迁移的 AC 代码保留 MIT 版权与许可，不因搬入新库自动变为 Apache。第三方内容保留各自许可。
- 用户确认 Tuui 为 ARR：仅可学习设计思路；本次**没有打开或读取 Tuui 反编译目录、源码、资源或二进制**。调查只读取 AC 自己的源码与 Git 历史，以及下文官方许可／API 资料。
- 不带字体，含测试字体与 Emoji 字体；宿主选字体，库提供字体服务。
- 首版 native 仅 Windows x64／Windows ARM64；不以此文批准 Linux／macOS 分发，也不更改 AC 现有平台政策。
- AC 版本、远端、代码、许可证保持原样。只写 `docs/provenance.md`、`assets/provenance.json`、`LICENSE`、`NOTICE`、`licenses/`。

## 2. 结论和消费规则

| 标记 | 结论 | 后续执行者的权限 |
|---|---|---|
| A | 允许保留并附声明 | 仅限本表标明的源内容；保留 MIT 或上游许可，完成所列解耦；依赖的其他文件不能随手带入 |
| I | 独立实现；旧实现排除 | 可以实现该能力，禁止复制、翻译、机械改名或局部拼接旧实现；新实现另登记作者、依据、差异和提交 |
| X | 排除 | 本轮不能复制；范围外、来源不明、发行证据不齐均明确排除，不用“暂按 MIT”兜底 |

**仅 A 是复制白名单。未列入或无法确认的对象一律 X。** I 不是对现有实现的许可认定。检索未发现外来注释只是调查范围说明，不是原创证明。A 的依据是 AC 中可定位的自有能力新增历史、当前明确的 MIT 授权与已检查的来源线索；任何新增反证会关闭相关项。提交作者仅表明 Git 身份，不单独证明版权归属。

A 项有未批准的依赖时，只能提取已准入的自有逻辑、重新接上独立服务；不能通过复制调用者把被排除的字体、Animator、渲染桥或第三方资产间接带入。相同名称不要求保留相同实现。

## 3. AC 许可与历史证据

- 当前根 `LICENSE:1–21` 为 MIT，原版权行 `Copyright (c) 2026 E33EPUS`，逐字保存为 `licenses/AtomChat-MIT.txt`。
- `e8cf67ca7c075b8ba563108b8f8c42d9a9794a8a`，提交作者 E33EPUS，2026-10-09：`chore: relicense to MIT, cut 0.3.0-Preview and repoint to NoWordz/AtomChat-Mod`；补丁明确从 Apache 改回 MIT。`07e0a05c` 的 Apache 历史不能覆盖当前 MIT 正本，也不能解决第三方来源问题。
- `THIRD_PARTY_NOTICES.md:4` 仍称 AC 自有代码 Apache，和根许可冲突。**本次不修 AC**；复制采用当前根 MIT；错误文案不作为第三方授权依据。
- `51b8fca6406aac36024794e15f6e41230a06ca56`，作者名 AtomChat，2026-08-21，初始 MVP：已有下节多项 Tuui 注释。早期身份或出处不能补齐的内容，按 X／I 处理，不能仅靠后来仓库改许可解除疑点。
- `7b79f766`（E33EPUS）、`a3e1f2cf`（扭曲）、`95bf6802`（扭曲）分别为多目标骨架、shared 迁移、映射层迁移；**移动／提取提交不是新原创证据**。下方自动列出更早的能力引入提交，避免只追到目录搬运。

复核命令（只读，路径相对 AC）：

```text
git log --all --format="%h %an %aI %s" -S Tuui -- "*.java"
git show --format= --unified=0 51b8fca6 -- src/main/java
git show --format= --unified=0 aa0a8400 -- src/main/java
git show --format= --unified=0 cdc17a1a -- src/main/java
git show --format= --unified=0 507fe72b -- src/main/java
git show --format= --unified=0 56e6c060 -- src/main/java
git show --format= --unified=0 eedb9bf9 -- src/main/java
git log --follow --format="%h %an %aI %s" -- <candidate>
git show e8cf67ca:LICENSE
```

## 4. Tuui 线索：旧实现不迁移

以下路径均在 AC；`S` = `shared/src/main/java/com/atom/chat/`；`O` = `layers/mapping/official/src/main/java/com/atom/chat/`；`F` = `platforms/1.21.1-fabric/src/main/java/com/atom/chat/`。行号对应上述固定基线。

| 对象 | AC 证据及最早线索 | 结论和替代依据 |
|---|---|---|
| `S/render/SkiaFontRenderer.java:14` | `ported from Tuui's FontRenderer`；`51b8fca6` 的 `src/main/java/com/atom/chat/render/SkiaFontRenderer.java` 已有同注释，作者 AtomChat | I：文本服务重新设计，宿主字体、字体身份／代际缓存键、统一排版测量绘制；依据 Skija Font／TextLine／shaper 的实际固定版本 API，不能照搬旧 renderer |
| `S/render/GlStateUtil.java:19` | `Ported from ... Tuui ... (Kotlin -> Java)`；初始 MVP 已有 | I：依据 GL 状态、Blaze3D 契约实现实例 scope，不搬静态单保存槽；PACK/PBO、FBO、纹理、混合、viewport 等恢复需求可单独写行为测试 |
| `O/render/SkiaGraphics.java:22`、`F/render/SkiaGraphics.java:22` | `Pattern ported from Tuui's Graphics class`；初始 MVP 同注释；映射搬运未提供额外授权 | I：依据 Skija backend render target／surface API 与 MC 帧缓冲契约重做；context/surface 身份、resize、关闭与混绘异常恢复分别验证 |
| `S/render/Animator.java:4` | `Ported from Tuui's SmoothAnimator concept`；初始 MVP 已有 | I：通用插值思路不是此实现的准入依据。用明确的时间输入、起终值、时长和中断契约独立实现；禁止直接搬类 |
| `S/ui/AbstractCanvas.java:10` | `modeled after Tuui's AbstractCanvas`；初始 MVP 已有 | I：新树／clip／scroll／事件系统从布局快照契约建立，不移植该容器 |
| `S/render/Easing.java:53` | `Tuui's EaseOutQuart` 比较注释由 `8845e121`（E33EPUS，2026-08-30）加入；类本体在初始 MVP 已有 | I：通用数学函数从定义重新表达、验证端点与单调性；不据注释措辞轻重豁免旧文件 |
| `S/render/ChatBubble.java:13` | `as in Tuui's canvas helpers`；`f5023437`（E33EPUS，2026-10-20）引入 | X：聊天气泡业务留 AC。若需要通用四角圆角与裁剪，依据 Skija RRect/Canvas API独立实现，不抽出本文件 |
| `S/ui/ScrollController.java` 的祖先逻辑 | `aa0a8400`（E33EPUS，2026-08-30）在旧 `src/main/java/com/atom/chat/screen/AtomChatScreen.java` 引入 `Tuui WheelUtils-style scrolling`；`cdc17a1a`（E33EPUS，2026-09-04）提取控制器时删去 screen 旧块；当前仍依赖 `Easing` | I：目标滚动／限界／嵌套消费／时钟契约可复用需求，旧实现不直接迁移。**当前文件没有 Tuui 字样不能自动过门** |
| 背景模糊／shader 接入链 | `507fe72b`、`56e6c060` 的旧 `src/main/java/com/atom/chat/render/SkiaGraphics.java` 注释明确提到 Tuui BlurProgram、Tuui shader 路径与反编译参考；`eedb9bf9` 移除旧说明并新增 `PanelBlurRenderer`／shader | I：三端 `PanelBlurRenderer` 与六个 blur shader 均禁止直接复制。仅学习渲染职责、失败回退和 RGB/alpha 行为需求，按标准 GL/GLSL/Skija API 重写；不推断这些注释已经证明具体复制或侵权 |

这些是保守的工程准入结论，**没有对 AC 或作者作法律定性**。没有读取 ARR 内容来进行逐行比对，也没有宣称本次实现者从未接触相关 AC 代码或满足隔离开发；事实上本次已读取上表 AC 片段和历史。

另外发现 **Melodify 来源参照**：`S/ui/UiMotion.java:55,123` 明确写 `Melodify's M3 step`／`Melodify M3.Motion.step`，后者由 `07e0a05c`（E33EPUS）加入；`S/nav/PageTransitionDirector.java:84,180` 同样指向 Melodify 的串行 zoom／step。本次没有核查其具体实现与许可，不能因属于通用指数数学就批准复制该旧表达；两文件旧实现均排除，相关能力改为独立实现。此结论不判断 Melodify 的许可证类别或是否存在复制。

## 5. 全部方案候选的处置

下表“允许”仍受第 2 节依赖隔离和保留 MIT 的约束。具体源路径、原始引入提交／作者／日期和当前 SHA-256 见第 9 节，避免把推测当作作者证明。

| 方案候选／支持项 | 结论 | 可迁范围、需移除的内容或替代行为 |
|---|---|---|
| `SpringAnim`、`UiSpring`、`PressScale` | A / MIT | 自有弹簧与按压逻辑；`SpringAnim:3` 明确说明为 AC 重写；原有 AC 调参不是通用强制默认；替换 `UiSpring` 对排除 `Easing` 的调用 |
| `UiMotion` | I | 有未闭合的 Melodify M3.Motion.step 线索，旧实现不复制。按指数函数定义与墙钟时长需求重新实现，时间／主题需求可保留 |
| `CropMath` | A / MIT | 图片覆盖约束、裁剪框中心缩放及源像素矩形；只迁数学，不带头像存储、上传或文件选择器 |
| `NavigationStack` | A / MIT | 泛型栈与根不可 pop 的模型，避免带 AppPage 类型 |
| `InputRouter`、`InputHandler` | A / MIT | 已有顺序路由协议；不能描述为完整 focus/capture 系统；新模型另实现取消、焦点和模态 |
| `ConfirmationState` | A / MIT | 纯确认状态；不带 AC 动作与文案 |
| `NotificationHudLayout`、`NotificationHudEditorModel`、`NotificationHudEditorLayout` | A / MIT | 九宫格、偏移与等比缩放；移除翻译 key，替换 `UiLayout.Rect` 为公共几何；不是自由宽高 resize |
| `NotificationHudEditor` | A / MIT | 事务与手势逻辑；预览、初始值和提交由宿主注入，重接独立文字／绘制服务，不搬 NotificationBanner |
| `SkiaGraphics`、`GlStateUtil`、`SkiaFontRenderer`、`Animator`、`Easing`、`AbstractCanvas` | I | 第 4 节逐项替代；不允许其他 A 项顺带引入 |
| `SkiaDraw` | I | 初始 MVP 作者仅 AtomChat、出处不能进一步确认；旧 helper 实现排除，按 Skija 官方绘制／阴影／clip API 自写资源作用域，不复制其静态 Paint／MaskFilter 缓存 |
| `FontManager` | I | 同属初始 MVP 的来源未闭合项；宿主字体注册／回退／释放重新设计，排除 atomchat 固定资源路径、打包字体与 AC 日志 |
| `DrawPrimitives` | X（整类） | 从聊天 screen 提取，混有业务交互与第三方图标；只允许资产清单明确准入的单个图标数据，绘制入口独立实现 |
| `AppIcons` | X（整类） | 混合 Lucide／Feather、手写标注及 e33chat 参照；只允许第 6 节登记的单项；不能整体冠名 Apache 或“原创资产库” |
| `PanelBlurRenderer`（三端）／blur shader | I | 见历史新增线索；独立实现并重新跑渲染回退与混绘测试 |
| `UiTokens`、`ThemeService` | A / MIT | 自有主题规格与配色计算；移除 AtomChatConfig 读取，变成宿主给定不可变快照，不带聊天语义与设置存储 |
| `SettingsSlider` | A / MIT | 自有值域／步进／preview 与 persist 分离逻辑；移除 AC 配置绑定，保留连续拖动、松手一次 commit、取消还原需求 |
| `O/settings/SettingsSectionPage`、`F/settings/SettingsSectionPage` | X（整页） | 业务页不迁；2759／3078 附近的轨道重算与拖动只作行为依据，新控件独立接布局；不得把整个设置目录带入 |
| `ToggleSwitch` | A / MIT | 控件状态与自有绘制逻辑可解耦；删除对排除 Animator/Easing/SkiaDraw 的实现依赖，主题从宿主取 |
| `ConfirmationDialog`、`MenuPopup` | A / MIT | 自有浮层结构／交互与已知版式逻辑，注入新文字、主题、绘制、模态协议；所引用未列入的 helper 不自动准入 |
| `ColorPickerOverlay`（O/F） | A / MIT | HSV／调色交互与自有界面逻辑；替换旧文字／绘制依赖、分离 preview 与 commit，不带 MC 宿主业务 |
| `ImageCropper`（O/F） | A / MIT | 自有裁剪交互／PNG 输出逻辑，重接独立渲染；宿主负责资源输入、选择、存储、上传；不带 FlatLaf；保持裁剪框中心缩放 |
| `ScrollController` | I | 祖先有 Tuui-style 线索，见第 4 节；从行为重新实现 |
| `UiLayout` | A / MIT | 自有几何值与布局需求；聊天坐标公式不发布为通用布局体系，新页面以 Yoga 快照为准 |
| `ShellHeader`、`BottomTabBar` | A / MIT | 自有壳结构可参数化；只带准入图标，删除排除 Animator／旧 renderer 依赖；实例动效、可变导航槽代替静态状态／固定三 Tab |
| `PageTransitionDirector` | I | Melodify 串行 zoom／step 线索未闭合，不直接搬旧类；按可中断转场、完成后 pop 的行为独立设计，草稿、引用和页面枚举留 AC |
| `UiComponent` | X（旧体系） | 同初始 MVP 来源不能进一步确认，且现有事件体系不完整；从最小行为契约独立建新组件协议 |
| `Animations` | X（旧适配） | 包装排除 Animator 的 AC 开关适配，不为迁移带来公共实现；按库 MotionPolicy 重建 |
| `ChatBubble` | X | Tuui 比较线索与聊天业务，留 AC |
| `NavStackController`、`PageTransitionComposite`、`HeaderChrome`、`ScrollSyncService` | X | 方案明确留 AC 的业务协调层 |
| ComposerInput、聊天列表／消息／网络／草稿／配置／资料、NotificationBanner／通知队列 | X | 业务留宿主；不把聊天限制当公共默认 |
| Trellis／AtomSound 内容 | X（直接复制） | 本轮没有核查这些仓库的具体文件、提交与许可；不因方案泛称 MIT 或作为设计参考就准入。AtomSound 不修改 |
| 新 LayoutEngine／capture／资源 scope／组件协议／Gallery 等 | 独立新写 | Apache-2.0；从方案契约与固定版本官方 API 实现，未来作者／提交尚不存在，禁止冒填“已实现”或用排除文件作模板 |

## 6. 图标、音效、shader 与其他资产

`assets/provenance.json` 逐项记录三端实际文件和内联图标的来源、作者线索、原始提交、许可、SHA-256 与结论。记录的是**待迁源内容**：`copied_to_atomlib` 全为 false、目标路径为空。允许项也未在本次复制进库。

### 6.1 DrawPrimitives / AppIcons

准入的四个图标是 `AppIcons.ICON_TAB_SETTINGS_SVG`、`AppIcons.ICON_GLOBE_SVG`、`DrawPrimitives.ICON_MENTION_SVG`、`DrawPrimitives.ICON_PHRASE_SVG`。它们分别可追溯到 settings／globe／at-sign／zap。AC 的圆与线被转换为单 SVG path，mention 尾部坐标有调整，zap 与 Feather 4.29.2 polygon 相符；按**改编素材**记录，不叫原创。

对照的是 [Feather 4.29.2 图标](https://github.com/feathericons/feather/tree/v4.29.2/icons) 与其 MIT 许可；同时保留 AC 注释指向的 [Lucide 完整许可](https://github.com/lucide-icons/lucide/blob/5aae256c6479aef74c609b536d10675c97c714e3/LICENSE)（含 ISC 和 Feather 衍生图标的 MIT 部分）。**“ISC-licensed, no-copyright” 的旧注释不能代替版权声明**，ISC 不是无需保留通知的“无版权”。当代 Lucide zap 与 AC 路径不同，不能声称 AC 来自当前 Lucide 精确版本。

其余内联图标虽然部分标为 hand-written，但缺少逐项可闭合的设计出处；send 还有 Feather-style 线索、空列表图标有 e33chat 参照。依用户“未知直接排除”，本轮全部 X。可后续从有明确许可的固定上游版本直接重新取材，另登记；不能批量改包名当作放行。

### 6.2 反馈音效

- `feedback_button.ogg`、`feedback_send.ogg`、`feedback_panel_open.ogg`：A／CC0-1.0。依据 AC `tools/audio-source/README.md:1–20`、`tools/generate_feedback_sounds.py:1–110`、`23b744f3`（E33EPUS，2026-10-11）。上游作者 DuffyBro，作品 [Pop (made by DuffyBro)](https://freesound.org/people/DuffyBro/sounds/319107/)，官方页面本次仍标 CC0；处理过程为公开 HQ MP3 预览转单声道 WAV，再裁剪、低通、变调、增益、淡入淡出与 OGG 编码。原音频不是 AC 原创；加工脚本和录音许可分别处理。
- 源 WAV 本次 SHA-256 必须与 README 的 `7ebbfe12c59fb9029c7c18372ad12d35db63391ace719bf94aab17627da64fb1` 相符；若不同，三个反馈音效关闭准入。预览 MP3 的 README 哈希只作历史记录，本次没有重新下载 MP3、重新编码或宣称位级重现。
- `notification.ogg`：X，来源与独立许可未闭合。不能把它和三份 CC0 反馈文件混为一项，也不能用根 MIT 猜录音授权。
- 不复制整个 `sounds.json`，其中含 AC 事件名与被排除声音；后续库独立定义可选反馈事件。

### 6.3 其他资源

- 六种 blur shader 的实际资源副本逐文件 X／待独立实现，历史线索见第 4 节；实际副本数量以 JSON 中 Git 跟踪路径为准，不假设每目标都有独立一套；材质与特效不可只因是几行数学就直接搬。
- 两份 OTF 与 OFL、logo、wordmark、about_hero、作者头像、两份语言表逐文件 X。字体即使 OFL 可分发也被用户明确排除；品牌、作者头像和业务翻译留 AC，不对其法律归属作推断。
- 运行时用户图片／皮肤／壁纸、截图、测试 golden、未知插画／材质均不准入；不把用户数据或参考图当库素材。

## 7. 依赖许可清单与发行缺口

许可原文逐字保存，获取 URL、日期、字节数、SHA-256 见 `licenses/sources.json`。根 `LICENSE` 是从 Apache 官网下载的**完整 Apache-2.0**，含附录；没有把第三方版权行换成 AtomLib。

| 依赖／工具 | 许可资料与结论 | 范围与后续门 |
|---|---|---|
| Skija shared 0.143.18 | A／Apache-2.0；该 tag 的 LICENSE 与 JetBrains／Nikita Prokopov NOTICE 已保存 | 绑定 API 可消费，原 NOTICE 必须进入源码和最终发行包，修改上游文件需标变更 |
| HumbleUI types 0.2.0 | A／Apache-2.0；该 tag LICENSE 已保存 | 保留原作者／文件头；最终绑定版本与传递依赖由骨架验证，不能由许可清单代替 |
| Skija windows-x64／windows-arm64 0.143.18 | **X（当前发行准入）**；可继续隔离测试，不能据本文宣布可发行 | 本机 Gradle cache 两份 JAR 与 shared/types 均未发现 LICENSE／NOTICE 条目。Skija build.py 固定 SkiaBuild `m143-da51f0d60e-5`；其脚本配置可见 freetype、harfbuzz、ICU、libjpeg-turbo、libpng、libwebp、zlib、expat 等构建依赖。仅归档 Skia BSD **不足以闭合 native 内全部第三方通知**。要针对两架构的实际成品／构建依赖补齐清单、许可、NOTICE 后另行转 A；不要用“各自作者”一句话代替 |
| Skia `da51f0d60e` | 上游本体 BSD-3-Clause 原文已归档 | 这只证明 Skia 本体许可；不是上行 native 分发放行。字体服务不附字体文件，不等于可省 native 内字体解析／塑形依赖的许可 |
| AppliedEnergistics Java Yoga | 许可候选允许 MIT；固定源码 `6e419a3e407469e02fadbda6eb1ccce88b626318` LICENSE，保留 Facebook 原版权 | 尚未选中发行实现，T1 实际 Maven 版本／源 revision／产物哈希要匹配；不把源码 main 许可当作任何未知二进制的授权证明 |
| Yoga 原生上游 | MIT；`6455a848a76f433bdb48b2640b7f4644774c76fd` 原文已存 | 仅与 AE README 声明对齐；若选 LWJGL，另核实 3.3.1／3.3.3 实际 native 对应 Yoga commit／许可，不假设等于该 commit |
| LWJGL Yoga 3.3.1／3.3.3 | 两 tag 的 BSD-3-Clause 原文已存，候选许可可用 | T1 决定唯一后端；native Yoga 成品未闭合前 X（发行）。不得为此嵌入替代 MC 的 LWJGL core/glfw/opengl |
| Gradle wrapper（骨架工具） | 工具本身不属于本次 UI 复制白名单 | 由骨架执行者核对所用 Gradle 发行许可／wrapper 来源；不将工具许可声明冒称为 UI 运行时许可 |
| FlatLaf／Swing 选择器 | X（库提取） | 留 AC；不因为裁剪器需要文件输入而搬入，既有 AC 重定位声明不更改 |
| Lucide／Feather／三份 CC0 反馈声音 | A，仅第 6 节／JSON 允许项 | 全许可归档；MIT／ISC／CC0 各保留，不改成 Apache |

上表把技术探针与分发准入分开：T1 可测试未知二进制来源条目，但不得将其打入声称许可已闭合的公开产物。本轮没有给两架构或三加载器运行盖章；没有自行扩大发行平台。

## 8. 独立实现登记及后续复核

| 新能力 | 行为／官方 API 依据（不能从 ARR 拿实现） | 作者与提交状态 | 和旧实现的必要差异 |
|---|---|---|---|
| RenderSession／GlStateScope／MinecraftRenderBridge | [Skija 固定 tag](https://github.com/HumbleUI/Skija/tree/0.143.18/shared/java)、[Khronos OpenGL 参考](https://registry.khronos.org/OpenGL-Refpages/gl4/)、目标 MC 帧缓冲和 Blaze3D 契约 | 尚未实现，后续执行者记录实际作者／提交；本审计不是实现提交 | 实例 scope、异常恢复、禁止嵌套活动 pass，去除 AC 配置与诊断依赖 |
| FontRegistry／TextService／ImageStore | 同 tag Font／TextLine／shaper／Image／Canvas API；宿主字体与生命周期契约 | 同上 | 无默认字体、按宿主／fontId／weight／generation 区分，统一测量绘制与显式释放 |
| 时间推进／easing／滚动 | 数学定义、墙钟输入、方案中的捕获／取消／滚动行为；不用旧 Animator/Easing/ScrollController 为模板 | 同上 | 每实例时钟、可中断、界限快照、嵌套一次消费 |
| 容器／布局／交互协议 | 方案第 7 节与 T1 选定 Yoga 的固定版本官方 API | 同上 | 单布局快照供画／点／裁剪，明确 focus/capture/cancel；旧 UiComponent／AbstractCanvas 排除 |
| 背景模糊／圆角绘制 | GLSL/OpenGL 标准 API、Skija shader／filter API、宿主输入输出行为 | 同上 | 不带旧 shader 表达，自己验证显示帧 RGB、透明离屏 alpha、失败实底与资源释放 |

实现后在同一来源记录补齐目标文件、作者、实际提交或未提交状态、改动说明、上游 API 固定版本与实际依赖；本轮不伪造这些未来字段。MIT 迁移文件需标明 AC 基线源路径、原版权、保留 MIT 文件与解耦改动。需要重新取上游图标时记录精确 revision 和目标哈希。

新增文件不以“不含 Tuui 字符串”作为完成条件；必须人工核对来源与行为实现过程。源版本变化、额外资产、未记录 helper／算法、native 或字体混入，均需要重新过门。打包阶段验证 LICENSE／NOTICE／实际使用的第三方声明进入 source 与运行 JAR；本轮没有改打包规则，不能假称这一步已经完成。

## 9. 固定基线文件与引入历史

以下表由 AC Git 的只读查询生成。历史原路径沿用当时目录；作者字段为 Git 提交作者。当前文件哈希锁定本次可审阅对象，不代表批准整文件迁移；**结论以上表为准**。初始骨架大批搬入的文件继续追到更早 `src/main/java`，不把迁移作者冒作原作者。


| 当前路径（AC 根相对） | SHA-256 | 最早同名能力文件／提交／作者／日期 |
|---|---|---|
| `layers/mapping/official/src/main/java/com/atom/chat/avatar/ColorPickerOverlay.java` | `6a92e7ccac497d8e044b221de012444f39ba41766411ed921920d8ee3a014fec` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/avatar/ColorPickerOverlay.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `layers/mapping/official/src/main/java/com/atom/chat/avatar/ImageCropper.java` | `663d421d8742f4ba2d0632a06f0a9baf818ca0056ac895e2f63765744c7b0f1d` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/avatar/ImageCropper.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `layers/mapping/official/src/main/java/com/atom/chat/render/SkiaGraphics.java` | `1238d18005b3b74c5e904e47978d81f01646eddc525598717b1033b74f7b694d` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/SkiaGraphics.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsSectionPage.java` | `8ac854ac066cb100306b93fb19bde66e5f171b9b9e8c19496f382d8cc3d384f7` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/settings/SettingsSectionPage.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java` | `0eaa228d22b1fef10f29b07948d038fe2d43ca71384e93075c713696aa0e168f` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `platforms/1.21.1-fabric/src/main/java/com/atom/chat/avatar/ColorPickerOverlay.java` | `ff649ae72bab097b7d54b7b97dc59e959b41a4147e58a53f32bb8cfba4306669` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/avatar/ColorPickerOverlay.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `platforms/1.21.1-fabric/src/main/java/com/atom/chat/avatar/ImageCropper.java` | `9f3004cc4d81f2a065c3157af2b3ebde1780382a501d97ca1a1da2d11ddc42ff` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/avatar/ImageCropper.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/PanelBlurRenderer.java` | `e8d3b00a72140bf229a2c80525d7ed21beab47133955baebf7b48665fbd770a0` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/SkiaGraphics.java` | `cd3c7a9bdbef5133423043c3a31782f71e6eb750610607fc0c27d23705ddd0b6` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/SkiaGraphics.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java` | `6f905280cbb2c420611b97888560dc34384b5d1d76491ae4e986737e565f5e10` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/settings/SettingsSectionPage.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `platforms/1.21.1-neoforge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java` | `e416bc5161d7c996a0f95e0b62efdbc2af50c2329ffd2bee9ffd43fe3089d567` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/avatar/CropMath.java` | `da796b8dbad7a7606c43a7d26a74ef9abd22f5fd2cc4cdf955e308a4a90f5759` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/avatar/CropMath.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/font/FontManager.java` | `98eb35b37136c6f810a225d508082f6906f3cf16cc454cb7b6c2391ef9b0c96e` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/font/FontManager.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/nav/NavStackController.java` | `4826efecfe01d178338933b9afd5e477b4e810e620861c70eaf32ee5246565c4` | `shared/src/main/java/com/atom/chat/nav/NavStackController.java`；`d496f8c4317d`；E33EPUS；2026-10-10T19:42:45+08:00 |
| `shared/src/main/java/com/atom/chat/nav/NavigationStack.java` | `9e45c7a1d3ef347f3584abba5246584050ff35e86b9e01bcbe1188fb77a97544` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/nav/NavigationStack.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java` | `af65bc8d76abda96f90139e7218b30042b1acb5bd9b1d011629e8f2c7fc23345` | `shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java`；`da3a8717bdb7`；E33EPUS；2026-10-10T23:53:50+08:00 |
| `shared/src/main/java/com/atom/chat/nav/PageTransitionDirector.java` | `574833def5b23d7e567ac6716d2a6a385d55114f87ebb09d4e3fcc18dd08c5d8` | `shared/src/main/java/com/atom/chat/nav/PageTransitionDirector.java`；`8896951d7351`；E33EPUS；2026-10-10T20:13:38+08:00 |
| `shared/src/main/java/com/atom/chat/notification/NotificationHudEditor.java` | `0f10b284cfe1778698e6a9c00d302fa1a0ef9bb8d5cadf184e47fccc8e61be18` | `shared/src/main/java/com/atom/chat/notification/NotificationHudEditor.java`；`a1dff96b0076`；E33EPUS；2026-10-23T15:49:35+08:00 |
| `shared/src/main/java/com/atom/chat/notification/NotificationHudEditorLayout.java` | `a82de3891d217e266cc4717369e3c6fe4e4009e29dbef95563f6709e18b06360` | `shared/src/main/java/com/atom/chat/notification/NotificationHudEditorLayout.java`；`a1dff96b0076`；E33EPUS；2026-10-23T15:49:35+08:00 |
| `shared/src/main/java/com/atom/chat/notification/NotificationHudEditorModel.java` | `98e48cd4165b0d18891e6bcb5849e92dd38bbd7b2c3a4fc280d9be0f00f33cca` | `shared/src/main/java/com/atom/chat/notification/NotificationHudEditorModel.java`；`a1dff96b0076`；E33EPUS；2026-10-23T15:49:35+08:00 |
| `shared/src/main/java/com/atom/chat/notification/NotificationHudLayout.java` | `2d9fc1b6ab83177c04860dc83b54a8a8c7a45238d731f6e87511b43374ff4a13` | `shared/src/main/java/com/atom/chat/notification/NotificationHudLayout.java`；`a1dff96b0076`；E33EPUS；2026-10-23T15:49:35+08:00 |
| `shared/src/main/java/com/atom/chat/render/Animator.java` | `502b3bb4048deba86316c7287cacd1c43c888101d8eaaeaf74776c1f8510407c` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/Animator.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/render/ChatBubble.java` | `51ccac6038b99fa7bdefa515236f2227e83af33dd7dc50377ba5028405691ec7` | `shared/src/main/java/com/atom/chat/render/ChatBubble.java`；`f50234375593`；E33EPUS；2026-10-20T13:47:38+08:00 |
| `shared/src/main/java/com/atom/chat/render/DrawPrimitives.java` | `02ff309c5cd57495a4faa0410822151236502683ee8701aac058431b97c985c5` | `shared/src/main/java/com/atom/chat/render/DrawPrimitives.java`；`79e1bfa56fba`；E33EPUS；2026-10-10T13:25:48+08:00 |
| `shared/src/main/java/com/atom/chat/render/Easing.java` | `569f1f4ade402de56678d723e4a62539449082f97e5445b4ca59e60215921385` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/Easing.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/render/GlStateUtil.java` | `42cc153a3f1df59d6667d87a257f2678b58c7a95c38de6b899b68e5243aca3f6` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/GlStateUtil.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/render/SkiaDraw.java` | `1aaa11a0b222e6a9edde50fc8964fddceaca32722d26caf693bd0b2d7c35cbf4` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/SkiaDraw.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/render/SkiaFontRenderer.java` | `6450a5b1aee125704eddaab469770e5a6f52d5183fea81b07eb4bc499581727b` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/SkiaFontRenderer.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/settings/SettingsSlider.java` | `384c01d68d5c3057e7f4203d7445bea80a96fff36b02f83ff024be0919bd5beb` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/settings/SettingsSlider.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/theme/ThemeService.java` | `2bc8bf7e6775641163a95a14b78077269183853f437e68060f63b292ef933a49` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/theme/ThemeService.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/AbstractCanvas.java` | `ac5973bbe822695d7d7af0eadd80b453f4bdffea1f3084d3f8b5094edcb97b04` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/AbstractCanvas.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/Animations.java` | `0314831288ea645528cfade780b98b8545ae3d69853a87359ed3c9e720dc8a3e` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/Animations.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/AppIcons.java` | `e39fa6460fb73ef1de28b81e186b6c488c7873666ce383539cac1686ef4df2d0` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/AppIcons.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/BottomTabBar.java` | `4a80a974c6960f48ba4ff3ef522652a60c65a40e51cb28bb8e84049cb15b4934` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/BottomTabBar.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/ComposerInput.java` | `b677de3c27ecda5c4bdf818744193f2fa7077cdcc065f8d7adae0dc331fb89f8` | `shared/src/main/java/com/atom/chat/ui/ComposerInput.java`；`4e06a0e06c10`；E33EPUS；2026-10-10T17:53:56+08:00 |
| `shared/src/main/java/com/atom/chat/ui/ConfirmationDialog.java` | `f85992cc77e2dd95ee8885fcb17d9b2d3a1d8ede2e069265e5039c3143dd4166` | `shared/src/main/java/com/atom/chat/ui/ConfirmationDialog.java`；`28fd6de0caf1`；E33EPUS；2026-10-29T16:40:12+08:00 |
| `shared/src/main/java/com/atom/chat/ui/ConfirmationState.java` | `00287aeb5c5ea3935851a8c9620ba4d506d42f61a43b65a1cc284974ee9006dd` | `shared/src/main/java/com/atom/chat/ui/ConfirmationState.java`；`28fd6de0caf1`；E33EPUS；2026-10-29T16:40:12+08:00 |
| `shared/src/main/java/com/atom/chat/ui/HeaderChrome.java` | `f733ae136c5bd25f7fbf63d5102d104edfbd392ded567ea631aa475035a9d6c4` | `shared/src/main/java/com/atom/chat/ui/HeaderChrome.java`；`73469ee9a5dc`；E33EPUS；2026-10-10T17:33:59+08:00 |
| `shared/src/main/java/com/atom/chat/ui/MenuPopup.java` | `78973dbcca9528c691e1361b30079e6f3f93a34aebdfd26f0d93b276166c9c7a` | `shared/src/main/java/com/atom/chat/ui/MenuPopup.java`；`07e0a05c903e`；E33EPUS；2026-10-07T22:11:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/PressScale.java` | `2ad4095852879c015c27194de6cf8783356c1f631a14407a9996bff2e3569366` | `shared/src/main/java/com/atom/chat/ui/PressScale.java`；`4cae394bbb58`；E33EPUS；2026-10-04T19:07:46+08:00 |
| `shared/src/main/java/com/atom/chat/ui/ScrollController.java` | `cd8bc25c3fcedc48c09dcad03695a2f2e0e0617a5421fc1f19e03732d9cd5c28` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/ScrollController.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/ScrollSyncService.java` | `cefcd72c44dad7aa85fa45aa7ef7260c3b9002911d7667c5383f85ffedf8eb61` | `shared/src/main/java/com/atom/chat/ui/ScrollSyncService.java`；`6dfd5865e881`；E33EPUS；2026-10-10T14:05:23+08:00 |
| `shared/src/main/java/com/atom/chat/ui/ShellHeader.java` | `6f6dd2d0374d6efee9bdef6bc997e76755fb8fb3164ca4f8fe6a15d0e90146c4` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/ShellHeader.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/SpringAnim.java` | `af59e31101da6875e1ef25019c368997884a3527f5f9268d1ab4463615c37fb5` | `shared/src/main/java/com/atom/chat/ui/SpringAnim.java`；`1ce3ead64540`；E33EPUS；2026-10-04T16:05:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/ToggleSwitch.java` | `c9712dd1ad4a3c86f9db83a2443dc30b7af8a151618ef0c2d4878658862a4462` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/ToggleSwitch.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/UiComponent.java` | `6e4fbd6ecb50e0f799b8974aebd071383f1bd27445ce788c6649943b3c371459` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/UiComponent.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/UiLayout.java` | `eb88f8e9c6c01ba97a09b4e46537d3b19e29371e6c06b14899b601d218363916` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/UiLayout.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/UiMotion.java` | `7a875d126d7260bb5f49007f97785cb9f19d791a30763c6bc6de367f634e177d` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/UiMotion.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/UiSpring.java` | `4ae0309c734d7b31e29ca5189c6e1133a08b0a68157f2a335eadefe153a7ecc5` | `shared/src/main/java/com/atom/chat/ui/UiSpring.java`；`1ce3ead64540`；E33EPUS；2026-10-04T16:05:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/UiTokens.java` | `265583fc8d5745fa8561a31a6c36a4981d0072af7a483ced35cc2294b9818fbf` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/UiTokens.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/input/InputHandler.java` | `f26389593014eade97cabf7c153fc5c56c276cec8475a6f8c5af6fa2ab2f5e8d` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/input/InputHandler.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |
| `shared/src/main/java/com/atom/chat/ui/input/InputRouter.java` | `c7509803db4136db5730f1f9c55724d5b4bfac29b186afcd4832cae5542c0c6d` | `platforms/1.20.1-forge/src/main/java/com/atom/chat/ui/input/InputRouter.java`；`7b79f7660db0`；E33EPUS；2026-09-12T16:12:42+08:00 |

## 10. 实际发行候选收口

2026-10-30 已把四个准入图标路径写入 `ui/.../IconPaths.java`，并保留 AtomChat MIT、Lucide ISC 与 Feather MIT 声明；把三个准入的 CC0 反馈音效写入 `platforms/shared/.../assets/atomlib/sounds/`，对应哈希与 `sounds.json` 记录在 `assets/MANIFEST.sha256`。字体、AtomChat 品牌/作者图、通知音、Tuui 内容与业务素材均未进入。`BackdropBlur` 是按 Skija `ImageFilter.makeBlur` 与既有行为契约独立实现，没有复制 AC 的 shader/GL 链。

`assets/provenance.json` 的 `copied_to_atomlib` 与 `target_path` 已同步为实际文件；重复三端的候选音效仍只从 Forge 源复制一次到共享平台资源，避免三份重复内容。最终发行内容以三个 `0.1.0-local` JAR 的实际扫描、`assets/MANIFEST.sha256` 和 `licenses/` 为准；此文档不替代正式发布前的法律复核。
