# 完整外观调整页与库内背景磨砂：本地候选报告

## 结论

方案规定的实现范围已落盘并完成自动验证：设置行/组卡片/页头/导航/激活部件/折叠/单垂直滚动、完整“外观→调整”样例、AC 确认皮肤、Raw GL 背景磨砂与三份原创 shader、三平台入口、制品门和证据均已交付。状态仍是 **PENDING_GAME_AND_USER_VISUAL**：未启动 Minecraft，用户观感没有代签，也不宣称在真实 modpack、ARM64 或其他显卡上完成兼容验收。没有提交、推送、发布、部署、改版本或修改 AC 锁。

“完整”限定为本轮方案的“外观→调整”页。主题/显示明确禁用；聊天编辑器、IME、虚拟列表、路由、嵌套视口和全套外观模块仍不在范围内。

## 主代理独立审查

主代理完成源码与实际图像复核，新增三个真实滚动回归：最小手柄拖到底仍只滚到7600而非9900、右键释放误结束左键捕获、Escape错误透传且指针尾事件未隔离。三项在提交实现上均为断言失败（3 failures/0 errors），修复后全部通过。另修正样例向共享模糊服务传每屏局部帧号的问题，改用已采样的客户端单调时间；真实隐藏GL验证旧计数重启被拒绝、跨屏时间序列可用，游戏重开屏尚未运行。

新回归、红绿日志、逐端GL结果和保护核对见 [review/results.json](review/results.json)。独审后的库/样例已重新打包、重锁及运行最终验证。原七项库行为负控是提交阶段证据，未在独审修改后重复；新增三项独审是真实红绿，最终shader制品和消费者两项负控已按新候选重跑。根测试需显式提供atomlibTestFont夹具路径；首次漏参的失败记录保留。额外GL检查最初使用半值颜色作精确舍入断言，现改为端点纯绿，避免把1通道舍入当产品问题。

## 页面实现与视觉观察

独立样例只写业务 model、页面结构和回调，组合 Lib 的 SettingsShell、SettingsGroup、SETTINGS_ROW、Slider、Toggle、ActionButton、SegmentedChoice、Disclosure、ScrollViewport 和 ConfirmationDialog。五个真实滑条为 width 480[400,600]/10、scale 1[.75,1.5]/.05（x1.00）、contentscale 1[.8,1.5]/.05（100%）、opacity .93[.3,1]/.05（93%）、cardtint .235[0,1]/.05（24%）；normal halfWidth 600。

实际查看 1x 同尺寸参照后，结构已对齐：560×920 画布中的 480×860 面板、11:16 页头、三个导航项、两个分组、共同卡片的五行、滑条白手柄蓝轮廓、右侧开关、暗色确认浮层与同行警告图标。窄宽 360×920 会把面板约束到 280u，并让说明自然增高；半屏为直角贴边页头。照片与完整差异索引见样例 `evidence/appearance-candidate/output/`。

仍可见的差异：字体抗锯齿/字形栅格与 AC 渲染不同，逐行断行和若干边界不是像素相等；两套页面共用卡片材质和 token，但当前样例的具体 accent/card alpha 由业务状态重新组合；半屏图像是真实点击开关后的状态，因而保留焦点环。确认窗使用本轮的“重置外观”文案，AC 图为“清空聊天记录”。这些不能被自动图差阈值判成 PASS。只有原生 1x 参照参与像素差异；1.5/2x 是原候选真实光栅，没有放大的假参照。

六个 1x 对拍（像素数/背景平均 RGB 绝对差，仅观察）：

| 场景 | 改变像素 | 总像素 | 平均绝对差 |
|---|---:|---:|---:|
| phone | 405601 | 515200 | 17.084 |
| phone + modal | 515183 | 515200 | 31.033 |
| narrow | 236805 | 331200 | 21.518 |
| narrow + modal | 331174 | 331200 | 31.562 |
| half | 425090 | 515200 | 55.257 |
| half + modal | 515183 | 515200 | 36.842 |

当前约束：画布 560u 中 480u 初始面板只剩 40u 两侧边距，宽度右调或 1.00 以上的 uiScale 会被画布上限钳住；样例改用左调验证可见效果。更大游戏画布才能展示向右扩展。UI scale 以提交后生效为主，宽度/透明度拖动连续预览；这是为避免改变自有坐标比例时取消自己。

## 自动与隐藏 GL 验证

- Lib 根套：core 21、runtime 30、ui 73，共 124 项，0 failure/error/skip。
- Forge 1.20.1 / Fabric 1.21.1 / NeoForge 1.21.1 hiddenGlTest：各 31 项，共 93 项，0 failure/error/skip；真实 GLFW 隐藏窗口、NVIDIA RTX 5060 Laptop、OpenGL 3.3.0。
- 独立样例：AppearancePageTest 11、InteractionSliceTest 5、AppearanceReplicationTest 5，共 21 项，0 failure/error/skip。
- 制品 Python：`test_verify_artifacts.py` 34 项；consumer 验证测试 2 项；UI examples 3 项；候选 packager 测试 1 个正向脚本；均无失败。
- 六独立 Maven consumer、三平台打包和真实 jar 验证均通过。样例实际认证并消费 Fabric 原始 jar；自身 jar 内无 Lib/字体/shader/native。
- 输出 18 张标准/窄/半屏 × 1/1.5/2 × 正常/确认矩阵图，另有 7 张折叠/滚底/五值变化图；样例交付 JSON 共索引 25 张 PNG。

新增行为负控 7 项：设置行回退独立卡片、漏 Action 激活、折叠保留捕获、丢滚轮、取消目标 alpha mask、绑错 FBO、shader 信任源 alpha，均真实失败并按字节恢复。样例负控 2 项：原始 jar 改一字节在配置阶段拒绝，丢掉 release 后真实保存计数失败。最终制品对每个平台分别删除/损坏三份 shader，并验证 consumer 夹带 shader 被拒绝，共 19 项实际 jar 负控。

## 背景磨砂证据

每端四轮私有 ping-pong、只取 RGB、圆角 SDF、目标 alpha mask，并在显式不同 source/destination/第三当前 FBO 下验证全像素 alpha 哨兵和区域外不变。连续帧、两实例、零半径同目标、resize/reload/close、错误线程/context、脏状态和 COLOR_LOGIC_OP 恢复也通过。

真实 512×320 程序生成棋盘证据在 `gl/`：本次复跑CPU提交（含惰性准备）Forge/Fabric/NeoForge分别10.606/8.595/7.999ms；读回后可用的GPU query分别70/63/47µs。独立Fabric热调用64×64共32次，CPU提交中位6.193ms、p95 7.153ms，具体逐端值见independent-warm-timing.json。成本尚未拆分，性能未验收；GPU query不是CPU提交成本或游戏帧率，不能据此宣称足够快。

## 候选身份

| 产物 | SHA-256 |
|---|---|
| Forge jar | `0ec0f8205c815d04d41903be37cfcfda227efedba5ea2653d11ee7b8a8c0b0ee` |
| Fabric jar / 样例锁 | `c051386335fab712f756d8f9b340fd9af6b07767057ab47aed5269035f9f7f08` |
| NeoForge jar | `4a2b4262d4164c44e8c2a972ced651900a849e97e518807b80a9bc685b6a814e` |
| 样例 jar | `6f48b5216316fb40816eb35a9d990c7982c25d15b4ce6eb60322a945e716c928` |
| input.zip | `f9f702e42470ecc72ed4f5601c0c8278b0e8a38c0aaea875417a68f0f762f78a` |
| source.zip | `72f5fa180adf10be3282bceac3bf8a553bd33ef39e31033d3e0604f4eecc67e0` |

机器可读的测试计数、所有输出/图像 SHA、正负控、比较结果和未覆盖项在 [results.json](results.json)。`source.zip` 排除 build/.gradle/.git 与本目录生成证据；它包含两仓本轮源码和配置。input.zip 从最终隔离 Maven 生成。

## 精确命令

```text
cd D:/AtomLib
JAVA_HOME=C:/Program Files/Java/jdk-17.0.18 ./gradlew.bat test -PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf --offline --no-daemon --console=plain
python tools/run_packaging.py all --jobs 1 --maven D:/AtomLib/build/appearance-candidate/maven --test-font D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf
<platform-root>/gradlew.bat hiddenGlTest --offline --no-daemon --console=plain
python tools/test_verify_artifacts.py
python tools/test_verify_consumers.py
python tools/verify_ui_examples.py
python tools/test_candidate_packaging.py
python tools/verify_appearance_artifacts.py --maven D:/AtomLib/build/appearance-candidate/maven --output build/appearance-candidate/shader-mutations.json
python tools/verify_appearance_mutations.py

cd D:/AtomLib-Usability
python tools/lock_candidate_input.py --maven D:/AtomLib/build/appearance-candidate/maven --output evidence/appearance-candidate/input-lock.json
JAVA_HOME=C:/Program Files/Java/jdk-21.0.11 ./gradlew.bat test remapJar dependencyEvidence -PatomlibBuildPrefix=appearance-inputs -PatomlibMaven=D:/AtomLib/build/appearance-candidate/maven -PatomlibInputLock=evidence/appearance-candidate/input-lock.json -PevidenceRoot=evidence/appearance-candidate -PevidenceOutput=evidence/appearance-candidate/output --offline --no-daemon --console=plain
JAVA21_HOME=C:/Program Files/Java/jdk-21.0.11 python tools/verify_candidate_mutations.py --maven D:/AtomLib/build/appearance-candidate/maven --lock evidence/appearance-candidate/input-lock.json --evidence evidence/appearance-candidate --work build/appearance-candidate/mutations --build-prefix appearance-inputs
python tools/create_appearance_comparisons.py
python tools/verify_delivery.py --profile appearance --maven D:/AtomLib/build/appearance-candidate/maven --lock evidence/appearance-candidate/input-lock.json --evidence evidence/appearance-candidate
```

日志和失败红测位于 `D:/AtomLib/build/appearance-candidate/`。可复制的源码/input 档案及三端 Maven 在 `D:/AtomLib/build/appearance-candidate/`；临时输出不是发布物。

## 未覆盖与保护

未启动 Minecraft，未验证真实 F8 窗口、拖拽时序、游戏内文本观感、Iris/OptiFine/Gnetum、ARM64、其它 GPU、真实 modpack 共存或客户端退出。隐藏 GL 不是游戏实机。样例是本实施者同时写业务结构，因此不是新创作者盲测；尤其尚不能据此声称完整聊天 UI 可由作者零补代码搭建。

主代理独审 baseline 中 404 个受保护文件（默认 Maven、前轮 controls Maven、AC audit Maven、历史证据、旧锁、默认字体字节）逐一 SHA 核对，missing=[]、changed=[]。AC 全树未被本任务写入；相对开工基线观察到外部工作改动25个既有文件，原样保留，详细路径见 review/protection-review.json。不能宣称AC全树仍等于开工快照。
