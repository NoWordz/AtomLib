# AtomLib 完整外观调整页复刻与背景模糊实施方案

> 执行检查点：P0至P6的本地实现、打包和独立审查已收束；独审修复了滚动条末端映射、左键捕获被右键释放、Escape取消透传和样例跨屏模糊帧号。最终自动验证与精确产物以 docs/evidence/appearance-replication-and-backdrop/report.md、results.json及review/为准。本文保留原计划分解，不以checkbox取代执行证据。游戏和用户观感待验，公开beta3不变。

> 执行授权：用户于 2026-11-01 明确要求“那继续？写个方案，然后派一个子代理做”。据此编写方案后直接交一个实施子代理，主代理独立审查，不再重复询问执行方式。始终最多一个子代理，不自动加载其他协作工作流。
> 本文是实施约定，不是完成证明。游戏及用户观感结论单列；未授权提交、推送、发布、修改版本、部署或启动游戏。

**Goal:** 创作者仅组合 Lib 的页面部件、声明数据并处理业务动作，完成 AtomChat“外观→调整”的整页外观与交互，并能直接调用库内背景磨砂服务，无需自写 shader、FBO、控件、焦点或滚动。

**Architecture:** 在现有 core/runtime/ui 和三平台适配上增量扩展。控件行为、皮肤、分组容器和页面组成保持分工，所有绘制、命中、裁剪、焦点继续共享 PresentationFrame。背景模糊使用独立 Raw GL 区域抓取与多轮 Kawase 路线，库持有原生资源和原创新 shader，Skija 负责其上的页面绘制。

**Tech Stack:** Java 17 公共代码；Forge 1.20.1/JDK17/Yoga3.3.1；Fabric、NeoForge1.21.1/JDK21/Yoga3.3.3；Skija0.143.18；OpenGL3.3。不增加 Gradle 模块、UI 框架、Kotlin 或平台依赖。

## 全局边界

- 产品仓 D:/AtomLib，消费仓 D:/AtomLib-Usability；D:/AtomChat-Main 全程只读。AC 基线观察3f509f2b，已有参照图基线f6dbaa58，两者区分。
- 既有 Lib 字体/controls 候选、前轮审计 WIP、默认 Maven、controls-candidate/maven 和历史证据全部保留；不能 reset/checkout/stash/clean。主代理已保存 build/appearance-review/baseline.json 和完整源码快照。
- 新产物隔离为 build/appearance-candidate/{maven,...}；消费锁 evidence/appearance-candidate/input-lock.json；日常构建只读锁。公开 beta3、AC versions/atomlib.json 及旧样例锁不变。
- 新自有代码/GLSL Apache-2.0；现有已准入 MIT/第三方声明保留。不复制 Tuui 反编译代码或资产，不复制 provenance.md 判为 I/X 的旧 AC renderer/shader/整页。可读需求、规格和已准入 token，按合同独立实现。不要把“独立实现”称为人员隔离的 clean-room。
- 用户已给明确视觉目标，沿用原页深色面板、合并卡片、白字/灰说明、蓝滑条和红色危险确认，禁止重新设计成演示卡片风格。
- 样例允许入口/Screen 转发、业务值及回调、声明布局与库部件组合；禁止自写 Canvas/GL/GLSL 绘制、PointerTarget/手势、focus/modal/scroll 状态机，禁止引用 AC 类或使用 vanilla Widget 补缺。
- 本轮不迁移 AC 生产页、不接聊天网络/磁盘保存、不做聊天编辑器/IME/虚拟列表/任意路由或嵌套模态。单确认窗和既有取消/尾事件/异常合同必须保留。

## 事实与决策

当前样例 AppearancePage 只有两个独立卡片和 R 快捷确认窗；三控件交互已验证，但原页外壳、分组、五滑条、滚动和真实背景磨砂缺失。不能把108单测/69隐藏GL/14样例通过当作整页复刻成功。

| 决策 | 其他选项及代价 | 本轮采用 |
|---|---|---|
| 控件组合 | 每控件固定卡片无法合并；让宿主自画违背实用性目标 | 增量增加紧凑设置行/独立卡片样式，由库画控件，共用 SettingsGroup 卡片 |
| 页面导航 | 实现全部外观子页会扩成完整 AC；把未做的标签伪装可用有误导 | 本轮完整交付“调整”；主题/显示只作为明确禁用的范围外导航，SegmentedChoice 本身用真实可选项测试，不冒称完整外观模块 |
| 页面外壳 | 完整框架/路由过重；让样例手算外壳违反边界 | 可选设计包提供轻量 SettingsShell/SettingsGroup/页头绘制组合，无路由和宿主业务 |
| 滚动 | 宿主补逻辑仍踩坑；一次引入惯性/嵌套树范围太大 | 一个垂直滚动视口，限界、滚轮/拖动条、焦点可见性和模态隔离；嵌套及弹性暂不提供 |
| 模糊 | 只放GLSL仍需创作者处理FBO；Skija对未知alpha的世界背景有前提 | 库原生 Raw GL 服务+自有GLSL+三平台薄入口；已有 Skija Image helper保留 |
| 抓取策略 | 全屏静态单例浪费小面板预算且多区尺寸互相驱逐 | 每服务实例区域缓冲复用，带核半径padding；抓取与合成分离，明确源和目标 |
| alpha | 只采RGB仍会写目标alpha | 模糊合成显式禁alpha写入，并用不同源/目标alpha图案读回验证 |

GL API依据：Khronos glColorMask、glBlitFramebuffer、glGetQueryObject 官方参考；Skia resetContext仅使后端状态缓存失效，不能当作资源自动销毁依据。无同场景GPU基准，不声称此路线全面胜过Tuui。

## P0：固定参照、基线与验收矩阵

**Files:** 保留 build/appearance-review/*（主代理专有）；消费新增 evidence/appearance-candidate/；Lib新增 docs/evidence/appearance-replication-and-backdrop/。

- [ ] 读取本方案、docs/{controls,provenance,render-bridge,packaging}.md及相关技能，确认旧候选可读且原始输入SHA不变。
- [ ] 参照消费 evidence/reference/{phone-480,narrow-280,half-480}及对应confirmation，全部只读。标准矩阵面板480×860u@(40,20)，画布560×920u；压力面板280×860u@(40,20)，画布360×920u；真实1/1.5/2px-u。每张元数据记录画布/面板/密度/状态，不能靠缩放PNG冒充新density。
- [ ] 主代理在 build/appearance-review 独立提供参照分析及最终审查；实施者不改该目录、不改主代理测试的原断言。布局数值从生产规格/参照实测提炼，不能自己发明容差判绿。
- [ ] 新输入用现有 tools/run_packaging.py --maven 与 tools/lock_candidate_input.py 显式生成；不能复用旧同坐标Loom缓存造成假绿。

## P1：控件可组合与 AC 设置行皮肤

**Files:** ui/src/main/java/com/atom/lib/controls/{Control,ControlSkin,Slider,Toggle,ConfirmationDialog}.java；ui/.../design/atomchat/{AtomChatControlSkin,AtomChatControlStyle,UiTokens}.java；对应 ui/src/test/java/com/atom/lib/controls/。

**Interfaces:** 增加可选设置行呈现（例如 ControlPresentation.CARD / SETTINGS_ROW）；旧构造器/旧皮肤入口保留兼容。新选项属于部件规格，不增加业务状态。ControlSkin 的新增入口有清楚兼容策略，不能强制所有自定义皮肤改写。

- [ ] 先写紧凑行的真实Yoga/光栅测试，捕捉“每控件强画卡片”“数值掉到下一行”“toggle落到底部”的旧行为。缺API编译失败单独列，不充当行为红测。
- [ ] 实现 slider 标题左/值右、下方轨道；toggle 标题说明左/开关右；行内禁用、焦点、hover和press反馈；description为空时不留下空说明高度。
- [ ] SettingsGroup 提供统一卡片及分隔线，子控件不重复画卡片。共用容器与原 standalone 卡片同时可用，画/点/clip全从frame取。
- [ ] 对齐 AC 手柄（白色主体/蓝色边或当前生产实测）、轨道、行高、字体/权重、圆角/分隔线。ConfirmationDialog对齐原参考的图标标题同行、暗色浮层、正文和胶囊按钮，保留默认取消和蓝色焦点提示。
- [ ] 旧独审 IndependentReviewTest、ModalSkinLayoutTest、WindowFocusBoundaryTest及全部controls回归保持通过；两个会话的style/状态不串。

## P2：通用激活部件与页面组合材料

**Files:** ui/.../controls/{ActionButton,ActionModel,SegmentedChoice,ChoiceModel,Disclosure}.java（允许基于单一Action部件复用，避免重复状态机）；controls/internal/；design/atomchat/{SettingsShell,SettingsGroup,SettingsPageStyle}.java；对应tests；docs/controls.md。

**Interfaces:** ActionButton接受label/enabled/semantic-role与Runnable；SegmentedChoice接受稳定选项ID/label/enabled/当前值及Consumer<ID>；Disclosure接受标题/图标/expanded/enabled及Consumer<Boolean>。值受控，宿主声明状态；按下/释放/键盘/repeat/焦点/取消均由库管理。Shell仅负责布局/材质/坐标变换，Group仅布局/共同表面，无全局当前页。

- [ ] RED：完整press/release一次激活，拖出/禁用/模态/失焦不激活；repeat不重复；选项disabled不参与导航；收起时子目标马上不可用且捕获取消一次。
- [ ] GREEN：扩展sealed族及UiSession登记，组件各文件职责明确；控制器不引用AC包。
- [ ] Shell展示圆角面板、页头标题/可注入时间、分段导航及内容视口。时间由宿主传入，不读系统时钟；截图固定11:16。尺寸依据原页规格，不手写到样例里。
- [ ] contentScale/uiScale必须同时影响画和点。由库提供坐标映射/样式尺度入口；样例只设置业务值，不自己实现逆变换或第二套命中矩形。
- [ ] 新导航/组按钮与旧模态使用同一输入会话，不能各自抢键盘或在底层收到模态尾事件。

## P3：一个可靠的垂直滚动视口

**Files:** ui/.../controls/{UiSession,ControlScreenAdapter}.java；ui/.../layout/ScrollViewport.java（或同职责独立文件）；PresentationFrame.java按需；对应 ScrollViewportTest、ScrollSessionTest、ScrollRenderTest。

**Interfaces:** ScrollViewport持有offset/extent/viewport，读取自然布局后生成PresentationFrame的scroll offsets和clip；UiSession登记当前scroll viewport并负责wheel/焦点可见性。宿主不持有offset。只支持一个垂直内容视口，外层页头固定，modal另层不滚。

- [ ] RED：自然内容高于可视区时最后一行可滚到；超限clamp；裁剪外控件不可点击；滚动后的画与点一致；modal可见/退场中滚轮不得改变下层offset。
- [ ] 实现原始布局保留溢出高度，使用现有 constrainRootHeight/scroll map；不能把长文字先AtMost裁掉后伪造extent。
- [ ] 允许控制根在viewport外但受有效clip裁剪，替换UiSession原“所有控件必须完整fit”校验；严格保留viewport合法、隐藏目标不可命中、失败paint不发布新几何。
- [ ] Tab按内容顺序到达下一项时自动滚动显示，不能只在已可见子集中循环；scrollbar拖动在库内，resize/收起重新限界。
- [ ] 拖slider中滚动/折叠/scale变化按明确合同取消并回滚一次，不能把旧坐标事务继续提交；错误清理及尾键隔离回归。

## P4：独立 Lib-only 完整调整页

**Files:** D:/AtomLib-Usability/src/main/java/dev/atomlib/usability/{AppearancePage,MaterialsScreen}.java；新增仅页面数据/结构所需类；tests；build.gradle；tools/verify_delivery.py；evidence/appearance-candidate/。

- [ ] 先写期望真实页面结构和动作测试，确认所有五滑条、半屏、两组折叠、滚动、reset按钮可达；不把“类存在/字段相等”当行为测试。
- [ ] 使用库Shell/Group/行控件组合原调整页：布局缩放组含半屏/面板宽度/界面缩放/内容缩放；透明层次组含背景不透明度/卡片不透明度。准确初值、范围、格式、顺序和中文取生产需求/参考，字段在业务model中，不复制AC页面代码。
- [ ] 五项变化产生真实可观察页面效果（宽度/scale/字面内容密度/面板alpha/卡片alpha），half两宽度分别记忆；存储仅内存计数。压力280是上限约束，不把业务宽度默认400硬撑出画布。
- [ ] Reset以库ActionButton实际可点，位置放不干扰原首屏的附加区域，确认窗只增加内存动作计数；R仍可保留辅助入口，不能是唯一入口。
- [ ] 输出标准/half/窄宽 × 三density × 基页/确认窗，共18张真实光栅图，另出折叠、滚动末端及scale变化截图。保存同尺寸AC参照与新页并排、差异图和数值披露；只有1x真实参照时，其他倍率不伪称像素对拍。
- [ ] 样例扫描及jar实际检查：无AC依赖/源码根、无Lib/native/font/shader内嵌、无自写通用控件与原生Canvas/GL；启动入口F8及全部事件转发编译有效。

## P5：库内背景模糊与原创 shader 资产

**Files:** platforms/shared/src/main/java/com/atom/lib/client/render/backdrop/{BackdropRenderer,BackdropSnapshot,BackdropStyle,BackdropOutcome}.java；platforms/shared/src/main/resources/assets/atomlib/shaders/backdrop/{fullscreen.vert,kawase.frag,composite.frag}；三个MinecraftRenderBridge.java薄入口；GlStateScope.java仅有证据时改；shared/src/test/.../BackdropHiddenGlTest.java；docs/backdrop.md；provenance/NOTICE按实际登记。

**Interfaces（可因实际固定版API微调命名，合同不变）:**

```java
// 物理像素、左上原点；明确source/destination，不隐式相信当前绑定FBO。
BackdropSnapshot capture(FramebufferTarget source, Rect paddedRegion, long frameId);
BackdropOutcome draw(BackdropSnapshot input, FramebufferTarget destination,
                     Rect outputRegion, BackdropStyle style);
// style包含有限非负blurRadius、cornerRadius、0..1opacity和fallbackColor。
// BackdropRenderer implements AutoCloseable；snapshot只借用实例资源。
```

- [ ] 首先真实隐藏GL RED：源RGB相同但alpha=0/255，模糊RGB相同；目标每像素alpha哨兵完全不变；圆角及区域外RGB不变；源与目标FBO不同且当前绑定第三个时仍画对目标。零强度明确为未模糊拷贝或disable（文档固定），不得突然变黑。
- [ ] 独立GLSL按归一化多点滤波数学定义实现Kawase，每轮在私有ping-pong纹理之间流动，采RGB并生成自有alpha；合成时glColorMask(...,false)保护宿主alpha。不能把GLSL移个命名空间就算原创。
- [ ] capture同步取本帧、读区域外扩核padding防边缘污染；边界clamp及上下翻转准确。允许source==destination但先capture形成只读快照，draw不能采自己正在写的附件。
- [ ] 每renderer绑定创建线程和GLcontext；同尺寸稳定复用，resize/目标generation变化失效旧token，交错两个实例尺寸不互相驱逐，close幂等且在context销毁前执行；resource reload有明确invalidate/reload入口。
- [ ] 绘制调用必须在Skija pass之外且前一pass已flush，公开高层入口检查嵌套；完整恢复FBO/read-draw buffer、viewport/scissor、program/VAO、纹理/sampler、blend/color mask、depth/stencil及被改GL状态。
- [ ] v1明确支持非多采样RGBA8 2D附件与OpenGL3.3；不支持/不完整source、编译/链接/资源失败返回诊断和安全SKIPPED/FALLBACK，由现有库面板材料绘制实底；不能吞错返回成功，不能用矩形clear越过圆角污染目标。线程/context/过期token等编程错误明确抛出。
- [ ] GL tests覆盖连续帧源变化、两个实例、边缘区域、resize/reload/close、无反馈回路、脏GL状态及异常后恢复。负控实际去掉alpha mask、绑错target或采源alpha都必须行为失败并恢复。
- [ ] Minecraft薄入口提供默认主目标便捷调用，同时保留显式target路径供HUD缓存FBO宿主；不反射禁用第三方mod、不承诺Iris/Gnetum普适兼容。sample无需导入GL操作即可调用背景效果，自己的页面材质透明度作用可见。
- [ ] 模糊证据用真实GL合成的棋盘/颜色线场景，保存无模糊/有模糊/边缘/alpha哨兵图及CPU提交时间、设备/分辨率/区域/轮数。GPU时间只有实际query完成才记录；不忙等、不用glFinish混成生产路径，不宣称游戏fps或优于Tuui。

权威资料：
- https://registry.khronos.org/OpenGL-Refpages/gl4/html/glColorMask.xhtml
- https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBlitFramebuffer.xhtml
- https://registry.khronos.org/OpenGL-Refpages/gl4/html/glGetQueryObject.xhtml
- https://api.skia.org/classGrDirectContext.html

## P6：最终打包、独立审查与诚实交付

**Files:** docs/{STATUS,controls,backdrop,ai-guide,provenance,packaging}.md；docs/evidence/appearance-replication-and-backdrop/{report.md,results.json,changed-files.txt}；sample README/docs能力表；build/appearance-candidate/。

- [ ] 根工程 `./gradlew.bat test --offline --no-daemon --console=plain`，旧108项及新增行为测试全部通过，无skip冒充绿测。
- [ ] 用 `python tools/run_packaging.py all --maven D:/AtomLib/build/appearance-candidate/maven` 重新打包三个目标并跑六消费者；脚本实际CLI以help核对。每个平台 `hiddenGlTest` 全量执行，记录XML实际数，不把compileTests算成测试通过。
- [ ] 对最终fabric原始jar重钉 appearance-candidate/input-lock.json，样例运行 `test remapJar dependencyEvidence -PatomlibMaven=D:/AtomLib/build/appearance-candidate/maven -PatomlibInputLock=evidence/appearance-candidate/input-lock.json -PevidenceRoot=evidence/appearance-candidate -PevidenceOutput=evidence/appearance-candidate/output --offline --no-daemon --console=plain`，再实际verify_delivery。
- [ ] 原有字体/制品/consumer/examples验证全过，新增shader资源缺任一/损坏的真实jar负控被门拒绝，consumer不得夹带shader；认证原jar一字节变化配置阶段拒绝。
- [ ] 生成隔离input zip、三jar摘要、源码快照（包括untracked新增文件）、完整命令/日志及输出图索引。最终源码/输入/样例锁/图像来自同一候选，审查修改后必须重新打包与重锁。
- [ ] 实施者先完成全部阶段并回报；主代理随后做独立代码审查、实际看A/B截图、关键行为与GL读回复核。只允许同一个实施子代理返修，不再派第二个并行代理。
- [ ] 核对baseline保护项，AC如被外部改变如实披露，绝不恢复外部工作；旧历史证据与旧候选输入摘要不变。
- [ ] 报告单列：实现完成度、自动行为、CPU视觉证据、隐藏GL、游戏未跑、用户观感待验。若页面仍明显是演示卡片或缺五滑条/分组/可达确认，不称“完整复刻完成”。

## 交付判据

1. 用户能看到同一原页结构的Lib-only页面及并排参照，差异有逐项说明；480基准先对齐，280以可读/可操作/不溢出验收，不照抄原页压力缺陷。
2. 创作者新增同类设置行无需写绘制/手势；模糊只调用库公开能力，无shader/FBO接线。
3. 旧交互合同不回归，新增group/scroll/scale/modal使用同一几何，隐藏GL实测alpha和状态恢复。
4. 产物可审查可复现，候选身份明确；观感权属于用户，自动测试不代签。
