# 交互修复记录（2026-11-01）

本轮停止追逐像素级复刻；用户认可当前还原程度。最终范围为修复已确认缺陷并自动验证。曾尝试独立开发客户端验收，用户停止 Computer Use 后没有再操作或启动游戏。AC 本轮只读，模糊缓存和 shader 没有改动。

## 修复

1. 样例 Fabric Mixin 配置原来以业务父包为保留包，真实启动抛 `IllegalClassLoadError`，客户端入口无法加载。将 Mixin 移至 `dev.atomlib.usability.mixin` 并同步 JSON；新增入口与 Mixin 包隔离回归，真实 1 红→1 绿。之后客户端已初始化并显示原版界面；没有完成样例 F8/拖动验收。
2. 样例 F8 原来会替换任意当前 Screen 且没有返回路径；现在只允许主菜单或没有其他 Screen 时打开。此项根据 Minecraft 字节码与调用路径复核，最终条件已编译/验包，未称实机通过。
3. SettingsShell 的确认窗在 360×180、200×180 等短窗口超过视口，UiSession.paint 会抛 `Control does not fit viewport`。只将窗体等比适配，遮罩仍覆盖完整窗口，普通尺寸保持原布局；绘制、命中、裁剪读取同一份 PresentationFrame。未增加模态滚动系统；直接组合 modalNode 的其他宿主仍须提供足够布局空间。
4. 窄窗缩放暴露出反算几何误差：40u 文字槽变为39.999996u，绘制重新换行并报文字空间不足。PresentationFrame 保留量测时的精确几何，scaled/compose 传播，forPainting 使用原量测尺寸；没有放宽文字断言或截字。

## 验证

- 库根套：core21/runtime30/ui77，共128，0失败/错误/跳过。新增4项模态回归，校正测试建页顺序后实际3红→全绿；覆盖两种短窗、普通尺寸不变、真实按钮填色与点击、卡片内部隔离、整窗遮罩取消。
- 样例：11+5+5+3+1，共25，0失败/错误/跳过；真实认证新平台 jar 后完成 test/remapJar/dependencyEvidence。新增3项生命周期与1项入口包回归。
- Forge1.20.1、Fabric1.21.1、NeoForge1.21.1重新构建、验包，六个独立 Maven consumer 通过。三份 sources.jar 中三个受审库文件与工作区逐字节相同。
- 实际消费 jar 的 Mixin 配置与类路径一致；入口在保留包外，旧 Mixin 类路径不存在，未夹带 Lib/native/font/shader。
- 25张样例图中23张与前轮字节相同；changed-scale/changed-contentscale分别改变914/442像素（560×920），对应量测精度修复。没有新设像素验收阈值。
- 前轮 appearance Maven及原输入锁共91文件哈希不变；公开beta3和AC锁未改。未重跑GL或性能测试，不复用旧数值宣称新一轮性能通过。

测试探针修正单列：初版库测试在 shell 首次 attach 之前 open，触发正确的坐标切换取消，改为真实宿主的先建页后打开顺序；像素探针原位置在窄按钮上覆盖了文字，改从实际量测的按钮 padding 取样；Rect.intersect 的重算宽度存在浮点舍入，改为直接检查四条边是否在clip内。原红日志和探针日志均保留。

## 输入与复现

候选 Maven：`D:/AtomLib/build/interaction-fixes-candidate/maven`。
样例锁：`D:/AtomLib-Usability/evidence/interaction-fixes-candidate/input-lock.json`。
Fabric原jar：`90ebec2894d4ca2143ff3281637658e8ca43acf45372d58ab8a64f7b88db2158`。
样例jar：`de77be39d1c992a952c51efad7c8ca5a2ebbd4779a90007e8611fe0df89f5bbd`。
准确路径、全部SHA、测试计数和源文件清单见 [results.json](results.json)。旧候选不覆盖。

```text
cd D:/AtomLib
JAVA_HOME=C:/Program Files/Java/jdk-17.0.18 ./gradlew.bat test -PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf --offline --no-daemon --console=plain
python tools/run_packaging.py all --jobs 1 --maven D:/AtomLib/build/interaction-fixes-candidate/maven --test-font D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf

cd D:/AtomLib-Usability
JAVA_HOME=C:/Program Files/Java/jdk-21.0.11 ./gradlew.bat test remapJar dependencyEvidence -PatomlibBuildPrefix=interaction-fixes-inputs -PatomlibMaven=D:/AtomLib/build/interaction-fixes-candidate/maven -PatomlibInputLock=evidence/interaction-fixes-candidate/input-lock.json -PevidenceRoot=evidence/interaction-fixes-candidate -PevidenceOutput=evidence/interaction-fixes-candidate/output --offline --no-daemon --console=plain
```

完整尝试日志：`D:/AtomLib-Usability/evidence/game-acceptance-20261101/`。首次离线启动缺资源，随后复制并SHA1核对本机已有Minecraft资源至Gradle开发缓存（原资源目录未修改）；中断下载遗留锁造成9个缓存文件被Loom重试清理，重新从已验证本地源恢复后启动。没有另装整合包。用户停止后客户端已正常退出，未继续游戏操作。

Terra（gpt-6.1-terra/xhigh）完成第一轮独立复核和回归用例，后续执行遭429，主代理收尾实现；最终仍由同一个Terra xhigh作独立静态复核，未发现新增P1/P2；未运行构建或操作游戏，范围单列于results.json。未提交、推送、发布、部署或升版；游戏内交互、ARM64、整合包兼容和性能仍未验收。
