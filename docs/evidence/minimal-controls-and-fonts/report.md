# 字体与最小控件实施报告

2026-11-01，用户批准方案推荐项，由唯一实施者串行完成 P0→P6实现与本地验证；不再委托子代理。**主代理独立审查已收束并接受修复，最终候选重新验证；游戏与用户视觉验收待执行。** 机器结果以同目录 `results.json` 为准；本文件不把当前候选冒充已公开 beta3。

## 实现与接口

- `runtime/font`：FontOptions、FontSource 默认资源工厂、FontRegistry 重载；真实变量轴选择、静态脸匹配、原子注册／reload。通用默认400，AC独立正文500、标题700；原两字体字节／OFL不改。明确 ANTI_ALIAS、NORMAL、subpixel=true。
- `runtime/text`：可选 CJK_UI，旧构造器仍为 GRAPHEME，TextRequest不变。禁则以字形簇回退，极窄退回原合同；不是完整国际文本或IME。
- `ui/controls`：Slider、Toggle、ConfirmationDialog、UiSession、ControlScreenAdapter与模型／事件类型；内部FocusState、ModalState、ControlTarget、ActionTarget、ControlSlots。没有新模块、通用Button、壳或路由。
- `ui/design/atomchat`：AtomChatFonts、实例不可变AtomChatControlStyle、AtomChatControlSkin；Design.defaultHost与UiCards／UiTokens显式上下文重载。旧全局入口兼容，新控件不替换全局host。
- `core/input/InputDispatcher`：匹配release在业务回调前清capture，保留按实例身份取消的合同；commit中同步open／close／替换目标不会误取消已完成手势。
- 三端 `examples/a/<target>/.../ControlsRecipe.java`：真实MC映射下编译完整输入／keyup／失焦／removed／绘制桥接线，不要求宿主继承库Screen。
- 独立样例只写页面组合、phoneWidth／halfWidth和内存计数；仅从认证候选Maven输入编译。F8 mixin打开屏幕，R打开库确认窗；未启动游戏。

最终接口相对草案的局部细化：Control采用sealed实现族且构造时接收ControlContext；新增ControlMotion、TextService.fontGeneration()与只读UiSession.controls()；确认按钮的居中文字也是Yoga子槽位（`cancel/text`、`confirm/text`）。为落实“p/u变化先回滚、再布局”，新增UiSession.prepare(viewport)，三端recipe和样例在update／Yoga前调用，paint仍有防御性调用。样例也断言密度改变后的回滚值先参与页面宽度量测。不以这些细化引入第三方控件继承面或额外框架。

独审后新增 `ControlSkin.modalDialogStyle(Viewport)`，由皮肤提供模态外层尺寸，UiSession只居中组合，不依赖AC包。非AC皮肤以两种宽度、两种视口及独立高度／留白运行真实Yoga验证。AC皮肤接回原token规格，保持原有几何。

## 可观察合同与修复

连续鼠标移动按最近成功显示的手柄中心行程累计delta，布局本身不改值；键盘step到端点，最后一个调整键keyup提交一次。不变点击不保存。取消回传起点，外部覆盖回传新权威值。内部事务与capture先清空再发业务回调；preview失败回滚一次，清理失败为suppressed。

Toggle只在完整手势激活，repeat不重复切换；按压反馈先于值改变。Tab依据同一显示快照过滤hidden／disabled目标，换焦点取消旧事务，并隔离仍按住的激活键。替换实例未被成功显示前不能点击；绘制失败不发布新命中几何。

模态立即取得输入，取消基页手势，吞掉打开它的尾事件；默认取消、新的完整Enter才激活，Tab到确认后新Enter才确认。结果先标为已决／CLOSING再回调。OPENING／ACTIVE／CLOSING隔离全部输入，退场中新增按下也保留尾事件归属；透明度0才恢复仍有效的焦点。关闭未决窗只回调一次取消，拒绝同时打开第二窗。

真实测试还抓到并修掉：静态字体无轴返回null；焦点初值null与不可变列表的查询；禁用preview回声后的回滚视觉值；极小值域被固定容差吞掉；显示前已按住Enter的repeat误激活；取消回调抛错后目标集合未安装；关闭态开关丢失alpha；按压状态缺独立像素反馈。

Forge构建另外发现生成YogaDirty使用平台默认编码，Java17 javadoc按UTF-8读取会失败。生成器现明确UTF-8输出，仍按目标实际绑定选择331／333的拼写；未改玩家native范围。

## 主代理独审与修复红绿

主代理独立编写的 IndependentReviewTest 三项断言保持原样，最终SHA校验通过。三个实际问题及补充边界均完成源码修复：

| 问题 | 修复与实证 |
|---|---|
| dialog body被overlay当作取消 | 卡片title/icon/body/padding只吞事件；按下与松开使用同一排除卡片的遮罩判据。拖入／拖出不取消，外侧完整点击和按钮各自有效 |
| RIGHT取消后repeat重启并commit | 普通取消把仍按住的键隔离至各自keyup；ESC和密度变化均验证，多个调整键独立释放后新完整操作有效 |
| controls登记顺序覆盖PresentationFrame绘制顺序 | 绘制遍历frame.paintOrder，命中读同一快照。compose(B,A)叠放后更改登记顺序，像素相同且点击A |
| 通用行为直接依赖AC尺寸 | 外层style归ControlSkin；独立非AC皮肤真实布局不被强制套AC宽度 |
| 隔离尾键可能在窗口漏keyup后卡住 | ControlScreenAdapter失焦沿在取消后finally清物理键／鼠标跟踪；Enter与RIGHT均覆盖失焦无keyup→返焦新down/up。普通取消仍隔离尾序列 |

原两项独审红测为2 tests／2 failures／0 errors；新层序项按要求单独跑红1／1／0。补充边界6／5／0，按钮基线通过。先落实尾键隔离、尚未增加失焦reset时，组合11项仅返焦两项失败；reset后局部14 suites／42 tests全绿。完整日志文本（`.log.txt`，避免全局ignore遗漏）和XML归档于本目录 `review/`，当前13项负控XML与JSON也在其中；缺类型编译失败与这些行为失败分开记录。上一候选、旧mutation与证据另存 `build/controls-candidate/history/pre-independent-review/`。

主代理另独立通过33项制品verifier、2项consumer verifier、候选打包检查和3项examples检查；这些是主代理回传的独立结果。下表对应实施者本轮最终串行复跑，不把两者重复计为更多测试用例。

## 验证与命令

均由PowerShell调用Git Bash；Gradle串行、`--no-daemon --console=plain`，源测试和样例用已缓存依赖离线运行。Java17为17.0.18，Java21为21.0.11。

| 入口 | 实际观察与范围 |
|---|---|
| Lib `:core:test :runtime:test :ui:test -PatomlibTestFont=.../bundled-default.otf` | core21／runtime30／ui57，共108项，0失败／0错误／0跳过 |
| `tools/verify_controls_mutations.py` | 13项真实行为负控失败，逐一按字节恢复 |
| `tools/run_packaging.py all --jobs 1 --maven .../controls-candidate/maven` | 三目标编译／发布候选／制品校验，六独立Maven消费者及三端recipe编译 |
| 三目标各自wrapper的 `hiddenGlTest` | 每端23项，含每端新增1项真实控件bridge绘制／读回／事件往返；其他用例包括原有GL和布局回归 |
| `tools/test_candidate_packaging.py` | 临时真实jar按指定仓打包，坏路径在写输出前拒绝，默认Maven不变 |
| `tools/test_verify_artifacts.py`／`test_verify_consumers.py`／`verify_ui_examples.py` | 33／2／3项全通过，保留前轮字体必需项／白名单门及独立消费／接线守卫 |
| 样例 `tools/lock_candidate_input.py --maven ... --output evidence/candidate-lock.json` | 仅显式重钉候选；日常构建不重写锁 |
| 样例 `tools/verify_candidate_mutations.py --maven ... --lock ...` | 原始输入一字节不符在配置阶段拒绝；丢release使真实保存计数断言失败，按字节恢复 |
| 样例 `test remapJar dependencyEvidence -PatomlibMaven=... -PatomlibInputLock=evidence/candidate-lock.json` | 9个栅格／布局测试、5个真实交互切片；1project／0includeBuild／全部源码根在消费者内 |
| 样例 `tools/verify_delivery.py --maven ... --lock ...` | 同一原始输入锁、无夹带Lib/native/字体/PNG，6自有class，实际refmap映射存在 |
| `tools/package_maven_input.py --maven ... --output .../atomlib-controls-input.zip` | 独立本地候选zip及摘要，不覆盖公开asset |

完整命令及stdout留在 `../../../build/controls-candidate/final-*.log`，每个wrapper的packaging／consumer／controls-gl日志也保留；阶段红测与缺类型编译失败日志分别命名。**缺类型编译失败不算行为红测**。后续真实断言负控的XML位于 `mutations/`，错误数均为0；恢复后重跑正向全套。

负控明细：布局前漏密度取消、旧release顺序、默认100字重、旧grapheme断行、漏真实文字、漏轨道、漏手柄、模态滚轮提前放行，以及独审五项（卡片误当遮罩、取消尾键复活、登记顺序绘制、硬套AC模态宽度、失焦残留按键）。消费者另有输入摘要不符和漏release。库13项均编译成功后被行为断言拒绝、错误数0；消费者1项是配置认证拒绝，另1项是行为断言失败，不能混称15项行为红测。正向JUnit总数为108＋69＋14＝191，Python单测38项另计，候选打包／六消费者／打包验证也分别报告。不能从某个子集绿测推导游戏或美术已验收。

## 产物、图像与保护

完整候选文件清单见 [changed-files.txt](changed-files.txt)，包含前轮已授权审计WIP；样例改动见 [sample-changed-files.txt](sample-changed-files.txt)。源码快照为 `build/controls-candidate/atomlib-controls-sources.zip`和`atomlib-usability-controls-sources.zip`，摘要在同目录`source-artifacts.json`；包括未跟踪的新文件，不能仅看git diff遗漏新增实现。

- 平台Maven：`build/controls-candidate/maven`；三jar、输入zip及准确SHA见 `results.json`，不修改版本号或AC锁。
- 本轮重新编译三端、显式重钉candidate-lock，再运行样例test/remapJar/dependencyEvidence/delivery；当前候选与源码快照均包含独审修复。锁schema仍为1（认证原始jar）；汇总results schema为2，含独审红绿与逐层验收字段。源码zip摘要在外置source-artifacts.json／.sha256，避免把源码包自身SHA写入被打包文件造成循环。
- 消费jar：样例 `build/inputs/<候选SHA>/libs/atomlib-usability-0.1.0-partial.jar`，准确路径及SHA见样例 `evidence/controls-candidate/result.json`。旧 `build/libs`静态包不覆盖。
- 消费者jar本轮SHA与上轮相同：消费者源码未变且不内嵌Lib。新依赖身份由新candidate-lock、摘要隔离输出目录及resolved-dependencies中的inputSha256共同证明，不能把这个相同jar摘要误认为仍在消费旧候选。
- 版本仍为同一个本地候选坐标，MC元数据只检查版本号，不能在运行时区分公开beta3与候选字节；后续实例验证必须配套本次候选Lib，不能把样例新jar和公开旧Lib混装。正式发版／独立re-pin另行决定。
- 新12张页面／确认窗PNG：样例 `evidence/controls-candidate/output`，480／280u × 1／1.5／2px-u。正文15u、500；标题700，图为CPU Raster。字体100／400／500／700／真实AC BODY500的同源原生对照在本目录 `fonts/`。
- 初始Git HEAD/status/diff在 `build/controls-candidate/baseline/`。早期全文件／样例源码快照于P0接线开始后保存，不能把它当成严格原子化的开工前全源码备份；历史图像／JSON另按摘要逐项核对。
- 默认Maven90个文件、样例53个历史证据文件、两份字体源字节与前轮字体verifier／quickstart相关修复均保护。未使用stash/reset/checkout，也未创建新版本或变更公开下载。
- 主代理历史205项保护清单核验：204项摘要相同，仅原AC BannerProbe因外部提交删除；没有恢复外部改动。用户 `docs/skija-backdrop-vs-rawgl-blur.md`摘要不变并随完整源码快照保留，它是讨论输入，没有实施模糊功能。控件源码完成后，主代理才按用户新增要求为方案比较只读研究 `D:/tmp/tuui` 的BlurProgram／core blur shader，未复制、未用于本轮控件实现；原“未读取”事实限定原控件实施者及实现阶段，不扩为所有参与者从未读取。历史T0事实不改。

AC在这段时间被**外部工作**提交并更新：当前观察HEAD `3f509f2b`，`ed2bbfd6`收进了前轮审计修复，另有通知／Gnetum／TrChat相关提交；最初未跟踪的BannerBackdropIrisAlphaProbe.java已不在原路径。实施者从始至终只读AC，没有恢复这些外部变化。不能宣称AC全树或原f23fa139工作区diff仍逐字节不变；完整观察与变动路径列在 `results.json.protection`。AC的AtomLib输入锁摘要仍与本轮初始相同。

## 未覆盖与后续验收

主代理独立审查已结束，源码修复范围与逻辑获接受；其结论不代替游戏或视觉验收。未启动Minecraft，因此F8／真实MC输入、窗口失焦、真实GUI缩放／resize、玩家GPU兼容仍待实例验证；隐藏GL仅覆盖当前Windows x64驱动。ARM64硬件、专服、真实多mod共存、Maven host未由本轮覆盖。中文禁则仍是有限UI策略；无通用滚动／IME／嵌套模态栈。构建没有被宣称字节级可复现。

样例本轮由库维护者同时实现，因此只能称**编译输入独立的消费验证**，不是第二次创作者盲测。观感需用户将新图与AC参照并排评估，不能由agent按自定阈值代签。未执行commit、push、release、deploy或游戏启动。
