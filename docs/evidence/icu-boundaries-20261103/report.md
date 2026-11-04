# Paragraph / ICU4J 探针与本地候选（2026-11-03）

以下前置探针保留原始范围；后续候选实现与验收记录见文末。旧 TextService 和内置皮肤的默认行为未改变。

## Paragraph

`versions/dependencies.properties` 锁定 Skija 0.143.18。`javap` 检查本机缓存的 `skija-shared-0.143.18.jar`：绑定提供 Paragraph、ParagraphBuilder、FontCollection、TypefaceFontProvider、TextStyle、ParagraphStyle、LineMetrics，包含 layout、paint、命中、范围矩形、行度量、close 等接口。Paragraph 的文本由 builder 转交；Paragraph.close() 同时释放对应 ManagedString。

Windows x64 的 `ParagraphNativeProbeTest` 实际使用 FontRegistry 中的字体注册到 TypefaceFontProvider，对中英、组合字符、ZWJ emoji 和希伯来语的混排执行 layout、离屏 paint、范围矩形、命中与关闭，检查非空像素、组合字符及 emoji 完整范围选区、UTF-16 末尾索引。运行：

```powershell
.\gradlew.bat :runtime:test --tests com.atom.lib.runtime.ParagraphNativeProbeTest "-PatomlibTestFont=C:\Windows\Fonts\msyh.ttc" --no-daemon --console=plain
```

测试通过。另用系统字体的独立进程探测 Windows x64 和 WSL Linux x64，均能调用 native 完成排版、绘制、命中和选区；实验中拆分 `e\u0301` 或 ZWJ emoji 的内部范围返回零选区框，适配器须先对齐 grapheme。RTL 点击呈视觉顺序变化，尚无完整双向几何验证。Linux 探针不改变本项目受支持平台声明，Windows ARM64 未运行。该测试不验证完整字体回退、资源泄漏、所有偏移、空文本、截断或宿主控件。

后续增加 `layoutKeepsFaceAfterProviderAndRegistryReload`：先构造 Paragraph，关闭创建它的 Font、Typeface、TypefaceFontProvider、FontCollection、style 和 builder，再重载 FontRegistry；该 Paragraph 仍能在当前 Windows x64 上 layout、获取选区并绘出像素。两个 ParagraphNativeProbeTest 均通过。这不证明重复创建/淘汰时无泄漏，也不改变应用层必须拒绝过期字体代次的约束。

## ICU4J

三个平台分别在各自目录执行：

```powershell
.\gradlew.bat dependencyInsight --dependency icu4j --configuration compileClasspath --offline --no-daemon --console=plain
```

Forge 1.20.1 选中 71.1（Minecraft client；ANTLR 的 61.1 被冲突解析升级），Fabric 1.21.1 选中 73.2，NeoForge 1.21.1 由 minecraft-dependencies 严格选中 73.2。NeoForge 使用 JDK 21。此结果仅说明当前构建解析，不能代替干净宿主的运行时类可见性与冲突测试。

`BoundaryProbe.java` 和 `BoundaryProbeTest.java` 是独立实验，不是产品 API。使用本机 71.1 编译，分别用 71.1 / 73.2 运行：

```powershell
javac --release 17 -encoding UTF-8 -cp D:\Myworld\.minecraft\libraries\com\ibm\icu\icu4j\71.1\icu4j-71.1.jar -d build\icu-probe docs\evidence\icu-boundaries-20261103\BoundaryProbe.java docs\evidence\icu-boundaries-20261103\BoundaryProbeTest.java
java -cp 'build\icu-probe;D:\Myworld\.minecraft\libraries\com\ibm\icu\icu4j\71.1\icu4j-71.1.jar' BoundaryProbeTest
java -cp 'build\icu-probe;D:\Myworld\.minecraft\libraries\com\ibm\icu\icu4j\73.2\icu4j-73.2.jar' BoundaryProbeTest
```

两次均输出 `PASS thai=[0, 4, 7, 12, 16, 20]`，覆盖行、词、grapheme、空串、代理对、ZWJ 和并发实例。尚未完成三端独立消费者、缺依赖、POM 和实际类加载核验，未确定最终依赖分发策略。

## 其余验证与未完成项

前一轮根工程 core / runtime / ui 测试通过，分别 24 / 49 / 137 项，零跳过；三个 Python 检查分别 34 / 2 / 3 项通过。当时未重新构建三端制品、未跑 hidden GL 或实际游戏，三个子代理执行因服务端 429 中断。

## 后续本地候选

- 新增显式 `ParagraphTextService` / `ParagraphTextLayout`，同一 native Paragraph 负责布局、绘制、命中、选区。ICU 只用于 grapheme 对齐；RTL 仍由 TextRequest 拒绝。布局和 service 需关闭；字体代次变化在下一次 service 布局或布局操作时清理旧 native，若无后续操作则关闭时清理。高度裁剪只标记 truncated，省略号由 maxLines 控制。
- `UnicodeBoundaries` 使用 ICU 的行、词、grapheme 边界并返回 UTF-16 偏移；旧 TextService 不引用它。与 71.1、73.2 分别用同一编译字节在独立 JVM 执行通过。制品不打包 ICU，POM 与 Gradle runtime metadata 声明 71.1；游戏依赖图解析分别为 71.1、73.2、73.2。缺 ICU 时旧 TextService 类和 TextRequest 可加载，调用新路径的隔离测试给出明确错误。
- 独立审查指出横向裁剪状态、字体代次释放及消费者未检查 ICU 三处问题，已增加失败测试并修正。随后复核指出旧候选和 Minecraft 自带 ICU 会掩盖缺失声明；新建纯 Maven 客户端并主动排除 ICU，实际执行按预期失败，重新构建候选。字体回退用例依赖测试字体覆盖指定字形；本轮 XML 显示无跳过，不推广到任意字体。
- `gradlew.bat :core:test :runtime:test :ui:test -PatomlibTestFont=C:\Windows\Fonts\msyh.ttc --no-daemon --offline --console=plain --rerun-tasks`：24 / 71 / 138 项实际执行，零失败、零跳过。新增宿主自定义皮肤用例对同一个 Paragraph 布局测量、绘制并完成按钮点击，内置皮肤尚不能直接切换 Paragraph。
- 三端 `:build :publishToTestRepository :verifyArtifact` 成功；POM 与 Gradle module metadata 验证包含 ICU runtime，制品扫描拒绝打包 ICU。候选仓库 `build/paragraph-icu-final/maven`；三端 sources.jar 的四个新增文本源文件与工作区逐字节一致。Forge / Fabric / NeoForge JAR SHA-256 分别为 `6451f2c63d0f7c0072d99b2b707b08a57683a31efea251e36b0dc232c995102d`、`48673336083e2d1b6703a75f54e637a7abba1a8115161a3a0390e3eb52d56d51`、`d30a3f00e6f26776e8cada3af2db8245d2d1df48a78093dc4e9be65337c1c781`。
- 隔离副本六个 Maven 消费者均通过，每个在真实 JVM 运行 Paragraph 消费探针；纯 Maven 消费者三坐标分别通过，排除 ICU 的负控按预期失败。纯 Maven 正负控已纳入 `tools/run_packaging.py` 的 all / consumers 路径，日志位于 `examples/plain-paragraph/build/plain-consumer-*.log`；POM 与 Gradle module 缺失 ICU、POM optional ICU 均有拒绝测试。宿主按钮用例检查文字墨迹落在实际测量框，故意平移 30 像素时失败，恢复后通过。验证 Gradle 依赖与独立 JVM，不等同真实游戏加载器。Windows x64 三端 `hiddenGlTest` 各 47 项通过、无跳过；这些主要是已有渲染回归，不代表 Paragraph GPU 路径或其他架构通过。该测试写入历史证据目录的本轮输出已保存在 `build/paragraph-icu-final/gl-evidence`，已跟踪历史文件恢复至测试前字节。Python 制品 / 消费者 / UI 示例检查分别 40 / 4 / 3 项通过。
- 未启动游戏、未部署、未提交或发布。未验证 Windows ARM64、真实游戏加载器/多 mod、完整 bidi/IME、native 内存长期泄漏或真实性能；本地 Wiki 源修改尚未推送。
