# T9/T10：交互编辑器与可选应用壳

本轮只实现通用 HUD 编辑、图片裁剪、导航模型和可选壳。没有修改 UiView、component、runtime、加载器、AC、root build、版本或发布配置，没有启动游戏或安装到真实整合包。生产类不是占位骨架：行为测试包含真实 Skija Raster Canvas、PNG 解码、嵌套页面的 Yoga 布局与点击、字符输入及可中断转场。

之前的生产专服验证已由用户取消：只完成安装器下载/帮助探查，没有安装完成或启动 Minecraft server；六个组合均未验证。下载与网络错误证据保留在 `build/verification-instances/`，状态记于该目录 `status.json`，不能据此宣称生产专服边界通过。

## 来源与边界

- `core/.../geometry/CropMath.java` 与 `ui/.../shell/NavigationStack.java` 从 T0 白名单允许的 AC MIT 内容迁入，保留 Copyright (c) 2026 E33EPUS、MIT SPDX、原始来源和 `licenses/AtomChat-MIT.txt` 链接，只调整包位置。
- 其他新增类为本轮独立实现的 Apache-2.0 代码。转场没有读取或复制旧 PageTransitionDirector/UiMotion，不借用其 Melodify 来源实现。PageTransition 组合库中已经准入的 SpringAnim，并自行管理回调、重定向和帧去重。
- 不带字体、聊天草稿、页面枚举、固定标题、固定 Tab 数量、文件选择器、网络上传或业务保存。HostContext、ValueBinding、UI 元素和 Canvas 均从现有公共契约消费。

## HUD 编辑器

公共类型在 `com.atom.lib.interaction`：

- `HudAnchor`：TOP_LEFT、TOP_CENTER、TOP_RIGHT、CENTER_LEFT、CENTER、CENTER_RIGHT、BOTTOM_LEFT、BOTTOM_CENTER、BOTTOM_RIGHT，提供归一化 `x()/y()`。
- `HudPlacement(anchor,offsetX,offsetY,scale)`：不可变宿主值。偏移为逻辑 UI 单位；等比缩放范围 0.5–2，不是任意宽高 resize。
- `HudEditor(id,naturalWidth,naturalHeight,snapDistance,ValueBinding<HudPlacement>)`，可额外传 `HudPainter` 绘制宿主自己的预览。默认只有主题色矩形，不携带 AC 横幅或其他业务美术。
- `HudPainter.paint(Canvas,Rect,HostContext,FrameTime)` 借用 Canvas，由宿主画预览内容。不得关闭 Canvas。

`HudEditor` 实现 UiElement/PointerTarget，可作为 UiView 根或放入有明确可用尺寸的布局槽。`layout` 申请可增长的内容空间；`geometry` 读取该元素 LayoutBox 的 content/clip，绘制与按下命中均读取由此推导的同一 `itemBounds()`。

实际位置公式为：父槽起点 + anchor × (父槽尺寸 − 缩放后元素尺寸) + offset。因此同一锚点在父槽 resize 时仍保持对应边缘/中心关系，偏移按逻辑单位保留。拖动时按最近九宫格基准选择锚点并重新计算 offset，保持当前画面位置；到该基准的距离小于等于传入 snapDistance 时，两个偏移吸附为零。允许显式偏移把元素移出槽位，预览按槽位裁剪；库不强制决定宿主是否允许离屏 HUD。

指针 press 开始事务，move 仅 preview，release 应用最后位置并 commit 一次；取消恢复按下前状态。独立属性控件可使用：

```java
editor.beginEdit();
editor.setAnchor(HudAnchor.BOTTOM_RIGHT); // 选择锚点时清零 offset
editor.setOffset(-12, -12);
editor.setScale(1.25f);
editor.commit(); // 或 editor.cancel()
```

`setScale/setAnchor/setOffset` 也可隐式开始事务，开始时读取最新宿主值。setScale 将有限输入夹到 0.5–2，非有限值明确拒绝。`placement()` 和 `itemBounds()` 供宿主显示当前预览。外部编辑面板关闭、失焦或取消其自有滑条手势时，宿主应调用 cancel；只有库的指针捕获手势能由 UiView 自动发送 cancel。

## 图片裁剪器

`CropState(zoom,offsetX,offsetY)` 表示：zoom 是 cover 基础比例的 1–4 倍，offset 是**源图片像素**中相对图像中心的偏移。它不存储屏幕坐标；父槽 resize 后仍可计算相同来源的画面。

```java
ImageCropper cropper = new ImageCropper(
    "banner.crop", borrowedImage, outputWidth, outputHeight,
    cropBinding, pngBytes -> hostAcceptsEncodedPng(pngBytes));
```

裁剪框在父 content 槽内按 outputWidth:outputHeight 等比居中。`cropFrame()`、`sourceRect()` 分别返回显示裁剪框与当前源像素矩形。绘制、pan、缩放与 PNG 输出消费同一 crop 状态与 CropMath 公式，不另存一份视觉坐标。

- press/move/release 是一次 pan 事务，图像必须覆盖裁剪框，不露透明边。`pan(dx,dy)` 的参数是逻辑 UI 单位，内部换算为源像素偏移。
- `zoom(factor)` 围绕**裁剪框中心**缩放，保持中心下的源图片点，再重新夹紧 cover 约束；不是鼠标锚点缩放。无活跃编辑时每次 zoom 是一次 preview/commit；有活跃 pan 或显式 beginEdit 时只更新当前事务。
- 公共 `beginEdit()/commit()/cancel()` 允许宿主把连续缩放滑条组合成一次提交。状态变化只调用宿主 binding，不读写 AC 配置。
- `exportPng()` 在 CPU Surface 中绘制 sourceRect 到指定输出尺寸，真实编码 PNG 后调用 Consumer<byte[]>。它不自动 commit、开文件选择器、写文件或上传。编码失败/回调异常向调用者传播，局部 Surface/Image/Data 仍按 try-with-resources 释放。
- 输入 Image 为**借用**，由宿主关闭；cropper.close 只取消未完成编辑并关闭自身逻辑状态，不关闭输入 Image。输出宽高须为 1–8192；没有面积预算/后台任务调度，宿主应选择合理尺寸。导出要求可供 CPU 绘制读取的图片，GPU-only 图片应由宿主在正确 context 线程准备可读副本。
- 目前为矩形裁剪输出，没有圆形 PNG 遮罩、EXIF 自动旋转、动图帧、任意角旋转或文件解码 UI。空槽位不接受交互，未有面积的裁剪框不能导出。

PNG 的存储、上传、取消业务或线程调度均由宿主拥有，不因该回调获得网络/外部写入授权。

## 可选应用壳

公共类型在 `com.atom.lib.shell`：

| 类型 | 构造与用途 |
| --- | --- |
| `ShellAction(id,label,Runnable)` | 宿主给定动作。没有内置聊天、返回目标或图标语义。 |
| `ShellHeader(id,title,back,actions)` | title 可为 String 或 Supplier<String>；back 可为 null；actions 为可变数量的构造列表。标题通过现有 Label/TextService 测量。 |
| `TabSpec(id,label)` | 宿主导航槽身份及文字。 |
| `TabBar(id,tabs,selectedId,Consumer<String>)` | 任意非空槽集合；重复 ID/未知选中项拒绝。`select(id)` 相同项不重复通知；回调异常恢复原选择。选中项样式来自主题。 |
| `NavigationStack<T>` | MIT 泛型栈，push/pop/peek/size/snapshot/replaceWithRoot；根不能 pop，snapshot 不可变。 |
| `PageTransition(initial)` | 独立位置/速度 spring；retarget(target,completion)、advance(FrameTime,motionEnabled)、value/velocity/active。 |
| `AppShell(id,rootPage,header,tabs)` | UiElement + AutoCloseable；header/tabs 均可为 null。管理可选 chrome、内容视图和导航生命周期。 |

Gallery/宿主可直接把 AppShell 作为 UiView 的根：

```java
AppShell[] shell = new AppShell[1];
ShellHeader header = new ShellHeader("host.header", () -> hostTitle(),
    new ShellAction("back", "返回", () -> shell[0].pop()), hostActions);
TabBar tabs = new TabBar("host.tabs", hostTabs, initialTab, hostSelectsTab);
shell[0] = new AppShell("host.shell", hostRootPage, header, tabs);
UiView view = new UiView(hostContext, shell[0], viewport);
// view.paint / pointer / textInput 沿用现有调用方式。
// 先关闭/移出外层 view，再 shell[0].close()；宿主最后关闭其 fonts/text/runtime。
```

标签动作只调用宿主路由，不偷偷决定页面映射或清空业务数据。若宿主希望换整套根页，可重新构造壳；NavigationStack 本身也可独立使用，不强制消费 AppShell。

## 页面转场、快照和生命周期

AppShell 提供 `push(page)`、`pop()`、`cancelPop()`、`currentPage()`、`depth()`、不可变 `pages()`、`transitioning()/transitionValue()/transitionVelocity()`、`advance(FrameTime,motionEnabled)`。所有 UI 状态在创建壳的线程使用和关闭。

- push 立即把新页加入逻辑栈，再进入；pop 在动画**完成时**才从栈移除顶部。返回键重复触发不会重复删除。
- 进行中的 push 可用 pop 反向退出；进行中的 pop 可用 cancelPop 反向恢复。重定向直接改目标，保持现有位置和速度，不 reset 起点。被替代的完成回调不会执行。
- 每个 FrameTime.frameId 只推进一次，同帧重复 paint/advance 不加速，也不会重复触发新登记的完成回调。frameId 必须单调，降低动效策略会在下一次有效推进时贴到目标。
- 当前壳最多同时渲染前后两页；**活动转场期间 push 第三页明确拒绝**，没有无限路由队列或冻结复合截图。宿主可等待完成，或先用支持的反向操作结束当前转场。PageTransition 单独使用时可自由 retarget 有限标量目标。
- 壳的 header/content/tabs 几何由外层 LayoutSnapshot 决定。内容槽内使用已有 UiView 创建每页独立的局部 LayoutSnapshot，页面内部绘制、裁剪和命中共用它；没有复制页面布局算法。
- 内容页的滑入/背页视差只从该槽 Rect 的宽度推导。动画期间取消内容页捕获并暂停内容输入，防止显示位移中的元素按旧坐标触发；chrome 仍可调用 pop/cancelPop。稳定后以同一 content Rect 原点把全局指针换算为局部输入。
- `contentBounds()` 返回外层逻辑槽位，`contentFrame()` 返回当前前页的局部 LayoutSnapshot；它适合调试、Gallery 定位控件。活动转场中不能据该快照绕过输入暂停自行点击。
- 普通页面点击和 Unicode committed text 通过 ShellPageHost 转发到页内 UiView，已验证嵌套 TextField 的中文输入。`contentKey(event)`、`contentWheel(globalX,globalY,distance)` 供宿主显式路由页面内部键盘/滚轮；当前外层 Tab 焦点与页内 Tab 焦点仍是两个范围，**没有自动融合成一个跨 chrome/页面的全局焦点序列**。IME composition 或 AC 原生键位适配不在本轮。
- 导航开始取消旧页捕获；动画完成关闭离场页的 UiView/Yoga 状态。shell.close 幂等地关闭仍持有的两个页视图，异常清理会尝试两者，不关闭宿主 fonts/text/Image 或借用页面元素。页面元素若另有 close（例如 ImageCropper），宿主仍负责其最终生命周期。
- 页面实例不能重复压入同一壳；并发显示的页面不应共享可变子组件实例。每个壳、Header 动作与 Tab 按压 spring 均为实例状态；没有静态动作、固定三 Tab 或固定 16ms 时钟。

这些均为普通 UI/CPU 测试覆盖，不等同于 MC Screen/HUD、输入法、不同 GUI scale、Iris 或原版物品混绘的游戏验收。复杂 AC 导航接线和游戏视觉验收由主代理/用户继续完成。

## 文件与测试证据

生产文件：

- `core/src/main/java/com/atom/lib/geometry/CropMath.java`。
- `ui/src/main/java/com/atom/lib/interaction/{HudAnchor,HudPlacement,HudPainter,HudEditor,CropState,ImageCropper,EditTransaction}.java`。
- `ui/src/main/java/com/atom/lib/shell/{ShellAction,ShellButton,ShellHeader,TabSpec,TabBar,NavigationStack,PageTransition,AppShell,ShellPageHost}.java`。

测试与辅助：`core/src/test/java/com/atom/lib/geometry/CropMathTest.java`；`ui/src/test/java/com/atom/lib/interaction/{InteractionTest,ImageCropperTest,TestHost}.java`；`ui/src/test/java/com/atom/lib/shell/{NavigationTest,AppShellTest}.java`；`ui/src/test/interaction-shell.init.gradle`。只有这些新增范围与本文由本轮修改。

TDD：先写接口行为测试并观察缺失类红灯，再实现；后续两组真实断言分别发现并修复 HUD 沿用旧宿主值/转场同帧二次完成，以及嵌套字符输入丢失/错误线程关闭污染壳状态。CropMath 覆盖中心锚定、cover 约束和源像素矩形；真实 PNG 回调另解码检查尺寸和像素。

普通 root/module 构建不被 init 脚本修改。该脚本只为并行开发期单独编译本轮测试，使用独立 classes、binary 与 XML 输出目录，不覆盖其他工作者的测试结果。

### 最终执行结果（2026-10-30，Windows x64 / JDK 17.0.18）

标准命令实际执行成功：

```powershell
& 'C:/Program Files/Java/jdk-17.0.18/bin/java.exe' -classpath 'D:/AtomLib/gradle/wrapper/gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain :core:test :ui:test '-PatomlibTestFont=D:/AtomChat-Main/platforms/1.20.1-forge/src/main/resources/assets/atomchat/font/bundled.otf' --console=plain
```

最终 BUILD SUCCESSFUL。Core 为 UP-TO-DATE，已有 XML 为 **9 项**全绿（含本轮新增 CropMath 1 项）；UI compileTestJava/test 实际执行，XML 为 **19 项**全绿。所有结果均 0 failures / 0 errors / 0 skipped。本轮新增 UI 行为共 **13 项**，另有独立隔离输出同样全绿；因此本轮新增合计 14 项，不把原有套件全部算成本轮新增。

隔离命令也实际执行通过，用于并行 test-first 期间避免未完成的兄弟测试阻止本轮编译：

```text
java -classpath D:/AtomLib/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :ui:test -I ui/src/test/interaction-shell.init.gradle --tests com.atom.lib.interaction.* --tests com.atom.lib.shell.* -PatomlibTestFont=D:/AtomChat-Main/platforms/1.20.1-forge/src/main/resources/assets/atomchat/font/bundled.otf --console=plain
```

XML 证据：`build/standalone/_core/test-results/test/`、`build/standalone/_ui/test-results/test/`、`build/standalone/_ui/test-results/interaction-shell/`。此前一次完整 UI 74 项出现过范围外 VirtualListWindowTest/OverlayContentViewTest 的两项失败；没有越界修改，最终标准命令复测时已经全绿，不能用旧失败冒充当前状态。

本轮没有重新运行 Minecraft、产物打包/许可证递归扫描、三平台构建、生产专服、客户端 GL 或真实键鼠/输入法验收；这些不被上述 CPU/行为计数覆盖。没有提交、推送、部署或发布。
