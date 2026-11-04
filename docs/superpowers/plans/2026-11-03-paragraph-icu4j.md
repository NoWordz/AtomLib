# Skija Paragraph 与 ICU4J 文本能力实施方案

> 下个会话实施；本文件是工作交接，不代表依赖已引入、功能已实现或验证通过。

**目标：** 让 AtomLib 宿主可选择使用 Skija Paragraph 与 ICU4J 获得更可靠的 Unicode 换行和段落排版，同时保留现有宿主文本接口与默认表现。

**架构：** Paragraph 是当前 Skija 依赖提供的能力，不另行引入第二份 Skija；ICU4J 用于明确需要的 Unicode 边界分析。先验证两者实际 API、native 和三端依赖来源，再实现显式可选的新排版路径；不将两套排版结果拼成同一份几何，也不未经验证宣称完整 bidi、编辑器或 IME 支持。

**技术栈：** Java 17，Skija（版本见 `versions/dependencies.properties`），Yoga，ICU4J，JUnit，Forge 1.20.1、Fabric / NeoForge 1.21.1。

## 全局约束

- 基线：AtomLib `main`，当前版本 0.3.0；实施前重新核对 `git status --short --branch -uall` 和 HEAD，保留他人工作。AtomChat 最新小改与本计划无关，本计划不要求改 AC。
- 旧构造器、默认 GRAPHEME / WORD / CJK_UI 行为及公开接口保持兼容；新路径由宿主显式选择。任何改变现有默认布局的提议先提供实测对比。
- `TextRequest` 当前拒绝 RTL 强方向字符；不能只删除这个校验就宣称 bidi 已实现。新路径只有在绘制、布局、点击和选区一致通过后才允许相应输入；旧路径维持现状。
- ICU4J 的 Java API 与 Skia native 使用的 Unicode 实现是不同边界：不得假设 ICU4J 自动替换 Paragraph 内部断行或 bidi。Paragraph 对最终几何是单一权威；仅当绑定允许显式注入边界且验证一致时才注入，否则 ICU 服务独立输出断词边界，不在 Paragraph 输出上二次重排。
- 不自动启动游戏；部署、发版、推送遵循用户已有授权及目标范围。编译和自动化测试按仓库正常工作流进行。禁止为了新能力假定 Linux 或 ARM64 已受支持。
- 兼容、可用性、license、尺寸、内存和失败行为优先于组件数量。不能通过 native 探测时保留现有路径，记录阻塞原因，不伪装成引入完成。

## 已核对的现状与文件

- `runtime/src/main/java/com/atom/lib/text/TextService.java`：`layout(TextRequest, Constraints)`、`draw(Canvas, TextLayout, float, float)`，持有字体注册表及有界缓存。
- `runtime/src/main/java/com/atom/lib/text/TextRequest.java`：当前输入验证；`TextLayout.java`：布局、`hitTest`、`selection`；`TextBreakPolicy.java`：原有断行策略。
- `runtime/src/test/java/com/atom/lib/runtime/TextLayoutTest.java`：已有文字、选区、缓存、非法输入与 grapheme 测试。
- `runtime/build.gradle`：依赖 Skija shared；`versions/dependencies.properties` 锁定版本；`gradle/atomlib-dependencies.gradle` 管理三端嵌入与 Maven 依赖；`versions/targets.json` 管理目标。
- `docs/wiki/runtime.md` 和 `docs/wiki/development.md`、`README.md`：公开范围、开发命令。`docs/STATUS.md` 记录交付证据。其他文件仅在实现证据表明需要时调整。
- 本机先前看到的 Minecraft 实例分别含 ICU4J 71.1（1.20.1）与 73.2（1.21.1）；这是实例观察，不能证明所有启动器、构建环境和 loader 的类可见性或 API 兼容。

## 任务 1：确定运行时能力和依赖方案

**产出：** 可复现的原型结果和选定的 ICU4J 版本/分发策略，不改变默认生产路径。

- [ ] 读取实际锁定的 Skija JAR，用 `javap` 检查 `io.github.humbleui.skija.paragraph` 的 builder、font collection、布局、绘制、命中、选区、line metrics、释放接口及字符偏移含义；不能用上游最新版 C++ API 代替当前 Java 绑定的证据。
- [ ] 写小型自动化探针，用同一段中英、组合字符、Emoji、RTL 混合文本分别执行创建、布局、绘制到离屏、hit、selection 和关闭；逐平台检查本地 native 是否含符号。将可重现命令和输出记录到实施证据；不把 `jar tf` 成功当作 native 可用。
- [ ] 对三个目标使用 Gradle dependency insight、目标游戏依赖元数据和独立消费者测试，查明 ICU4J 的版本、编译/运行可见性及冲突行为。优先验证 Minecraft 自带版本的共同 API，避免重复打包；仅当证明不可稳定使用时，比较固定版本与隔离打包方案及其对 jar 体积、license、类冲突的影响，再选择并记录。不可假定任意用户实例等同本机。
- [ ] 检查 Paragraph 的字体注册能否复用 `FontRegistry`，并核对字体资源、native 对象的所有权与重载语义。绑定缺少必要 API 时保留旧实现，报告具体缺口，不引入不能工作的入口。
- [ ] 明确第一阶段仅实现经探针证明的能力：若 bidi 几何或字体回退无法保证，则不放开 RTL。记录哪些行为是 ICU4J 独立边界、哪些由 Paragraph 决定。

## 任务 2：接入 ICU4J 边界服务

**范围：** `runtime/build.gradle`、`versions/dependencies.properties`、必要时 `gradle/atomlib-dependencies.gradle`、`tools/verify_artifacts.py` 和消费者检查；runtime 文本包中新建独立边界适配器及对应测试。具体依赖声明按任务 1 的三端验证结果确定，不提前强制嵌入。

- [ ] 测试行边界、词边界和 grapheme 边界：中文标点、英语空白、无空格文字、组合字符、代理对、ZWJ emoji、空字符串；检查返回 UTF-16 偏移、单调且不拆 surrogate。使用固定 locale（如 `Locale.ROOT`），单独验证语言 locale 的效果和迭代器实例线程隔离。
- [ ] 提供内部边界能力并在新路径确有用途时接线。旧 `TextBreakPolicy` 不悄悄改义；ICU 边界不会自动成为 Paragraph 的实际换行位置。
- [ ] 增加缺依赖及类加载验证：三个目标各从干净消费者构建/解析，确认没有 `ClassNotFoundException`、`NoSuchMethodError`、重复捆绑或未声明的传递依赖；构件扫描检查 POM 和最终 jar 中 ICU 所在位置符合选定策略。
- [ ] 跑 `./gradlew :runtime:test` 对应 Windows 命令，并检查新增测试实际执行而非跳过。

## 任务 3：可选 Paragraph 排版路径

**范围：** `runtime/src/main/java/com/atom/lib/text/` 中新建职责单一的内部 Paragraph 适配器；按实际兼容设计调整 `TextService`、`TextLayout`、`TextRequest`；测试位于 `runtime/src/test/java/com/atom/lib/runtime/`。若现有 `TextLayout` 无法无损表示 bidi 视觉顺序，新增独立结果类型或适配层，不虚构可兼容语义。

- [ ] 先写失败测试：同一文本的渲染宽高、绘制位置、hitTest 和 selection 来自同一次 Paragraph 排版；覆盖中英混排、组合字符、Emoji、空文本、CR/LF/CRLF、显式换行、窄/零宽、maxLines、ellipsis、clipping、字体缺字和字体代次变化。记录 UTF-16 偏移与 grapheme 对齐规则。
- [ ] 以显式选项暴露新路径；旧构造器与原先输入拒绝规则仍保持。通过已确认的 FontRegistry 字体来源和 Paragraph 构建对象，确保测量与绘制使用同一文本、字体、宽度及方向。
- [ ] 确保 native Paragraph、builder、style、font collection 的生命周期与线程约束明确。若布局缓存 native 对象，淘汰、关闭、字体重载和异常路径均须释放；不得把失效 native 引用暴露给宿主。选择纯 Java 快照还是持有 native，根据 binding 的 hit/selection 能力与泄漏测试决定。
- [ ] 如需支持 RTL，先加入阿拉伯/希伯来、LTR/RTL 数字和标点、双向边界、跨方向选区的几何及像素测试；检查 hitTest 的逻辑偏移与视觉位置。若不能完整满足这些用例，RTL 保持拒绝，并在文档写明，不用简单 reverse 字符串。
- [ ] 为新路径的缓存 key 加上影响结果的排版策略、locale、字体 generation 和请求参数；核对现有容量、字符预算和关闭行为，确保默认路径回归测试通过。

## 任务 4：宿主验收与文档

- [ ] 从仓库根目录运行 `./gradlew :core:test :runtime:test :ui:test --no-daemon -PatomlibTestFont=<可用字体路径>`；Windows 使用 `gradlew.bat` 和适用字体，记录执行数、跳过和失败。
- [ ] 按 `.github/workflows/build.yml` 的三端构建、`hiddenGlTest`、打包和制品/消费者检查执行；工具链或图形环境不可用时如实记录，不把其他目标结果外推。执行 `python tools/test_verify_artifacts.py`、`python tools/test_verify_consumers.py`、`python tools/verify_ui_examples.py`。
- [ ] 在独立宿主用例中验证按钮/标签使用可选路径后的布局、绘制与点击；做字体重载、反复 layout/draw/close 的资源测试。必要时对比旧路径，不以离屏结果宣称真实游戏无问题。
- [ ] 更新 `docs/wiki/runtime.md`、必要的宿主接入示例、第三方许可说明与 `docs/STATUS.md`：实际 ICU 版本和来源、Paragraph native 支持、支持的脚本、已知限制、性能/资源观测、测试范围。若改 Wiki 源，按项目流程同步到 GitHub Wiki；外部推送需有当次用户授权。
- [ ] 最终交接报告明确：实际新增依赖/是否复用 Minecraft ICU、各端和各架构验证结果、是否支持 RTL、已有宿主兼容性、未验证项；不宣称新 API 稳定或自动覆盖 AC。

## 参考

- [Skija 项目与支持模块](https://github.com/HumbleUI/Skija/blob/master/README.md)
- [ICU 边界分析](https://unicode-org.github.io/icu/userguide/boundaryanalysis/)
- [ICU4J BreakIterator](https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/text/BreakIterator.html)
- [Skia Paragraph 上游接口（仅作概念参考，须核对当前 Java binding）](https://github.com/google/skia/blob/main/modules/skparagraph/include/Paragraph.h)
