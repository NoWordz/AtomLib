# CreatorTrial 5× 缩放反馈（2026-11-02）

用户实机反馈 5× GUI 缩放时界面过大。目标实例 `D:/Myworld/.minecraft/versions/1.21.1-CCB` 的 `options.txt` 实际为 `guiScale:5`，全屏、VSync 关闭、帧率上限 120；系统当前显示器模式为 2560×1600，不把该模式当成游戏 framebuffer 已实测值。

## 游戏日志

`latest.log` 17:54:17 的单次汇总：关闭状态 1925 个帧间隔 p50 13.4934ms、p95 19.8921ms；打开状态 557 个 p50 22.4955ms、p95 29.315ms。图标准备 559 个 p50 9.0667ms、p95 10.9218ms；页面绘制 p50 3.9827ms、p95 4.9591ms；图标关闭 p50 2.9403ms、p95 3.6645ms；可见 0–2 个，图标请求为 120px，模糊关闭。分类统计各自独立，不能把分项的中位数相加当帧中位数。关闭状态覆盖非弹窗画面，场景与摄像机等因素未由日志记录；它与打开状态的差值不能全部归因于图标，也不能用于多物品场景推断。

日志证明这次客户端载入了 CreatorTrial 并产生了诊断记录，但未记录每项物品是否正确显示、点击或动态效果，也不能说明资源重载、关闭重开及特殊模组兼容已通过。

## 根因与修正

旧 `TrialScreen.render` 将 `framebufferWidth / Screen.width` 用作设计密度，在 `guiScale=5` 时把页面的 24 设计单位图标扩成 120 物理像素；页面布局、绘制和鼠标原先都跟随 MC GUI 缩放。问题在 CreatorTrial 的宿主坐标选择，现有证据不足以认定 AtomLib 的布局/密度契约有误。

试用工程新增 `TrialCoordinates`：以 framebuffer 高度 `max(1, height/1080)` 作为设计密度，与 AtomChat 的基础密度同源；viewport 由 framebuffer / 该密度计算，绘制及物品像素尺寸使用同一个密度。来自 Minecraft 的 GUI 鼠标位置通过 `guiScale / pixelsPerUnit` 转回设计单位；按下、释放、拖动和悬停一致处理，滚轮位置转换而滚动步数不变。输入使用最近一次成功绘制的坐标快照。没有修改 AtomLib 或 AtomChat 生产代码。

## 验证与部署

先添加运行期红灯 `window-anchored coordinate transform is missing`，随后实现。JDK21 下 `gradlew.bat build trialChecks --rerun-tasks --offline --no-daemon --console=plain` 成功；12 组 JavaExec 检查通过，包括 2560×1600 下 GUI 缩放 1/2/3/5 的相同设计尺寸与映射后按键命中，原交互及 F9 检查通过。添加离屏图保存后又运行 `trialChecks --rerun-tasks`，12 组通过；`remapSourcesJar` 按既有设置 SKIPPED，JUnit 无实际测试。离屏图 `D:/AtomLib/build/item-icon-scale-fix-20261102/cpu-evidence/framebuffer-2560x1600-gui-5.png` 不含真实 MC 图标，也不证明游戏内点击、显示比例或视觉已验收。

新 CreatorTrial JAR SHA-256 `dc7c749bc58b25e8a3a44e310e2d01b6a69a499787b58f9c9a1a78dfc7c1489c` 已部署 CCB；旧版 `6abf374ad6ddb03ab6806ae377eedd52effb2cc893bd7d313f1c323542e77ac2` 备份在 `D:/AtomLib/build/item-icon-scale-fix-20261102/deployment-backup-20261102-180001-419397/`。保护核对其余 74 文件 SHA 不变，含 AtomLib `7a96b1274d2f07d4a2ff1b0da36b849d960171c31c9f2dc5f84f6b9930977398`。旧候选中的试用 JAR 已按原字节恢复，原 manifest 保持可验证。新试用源码快照 `D:/AtomLib/build/item-icon-scale-fix-20261102/creatortrial-source.zip` SHA-256 `c3f2b706fd7841954ef827466678b86ecd5983334c464ac8cb2f7debf042bd71`。

用户仍需在 5× 实机确认界面大小和所有位置的点击、滚动、重开与物品显示；改后的实机性能未采样。游戏未由代理启动。
