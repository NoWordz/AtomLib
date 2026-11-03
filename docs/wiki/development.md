# 开发与排查

## 获取与构建

当前仓库的 `gradle.properties` 定义 `0.3.0`，它仍是本地候选。公共 beta3 的 API 和产物不能等同于当前工作树；尚无公共 Maven 仓库。用目标版本和加载器分别构建本地 Maven 输入，具体坐标与安装步骤见 [getting started](getting-started.md)。两个独立宿主样例位于仓库的 `examples/a` 和 `examples/b`。

在仓库根目录使用 JDK 17，并提供本机存在的测试字体：

```powershell
.\gradlew.bat :core:test :runtime:test :ui:test --no-daemon "-PatomlibTestFont=C:/path/to/font.ttf"
python tools/test_verify_artifacts.py
python tools/test_verify_consumers.py
python tools/verify_ui_examples.py
python tools/run_packaging.py all --jobs 1
```

目标平台打包分别使用 `platforms/1.20.1-forge`、`platforms/1.21.1-fabric`、`platforms/1.21.1-neoforge`；后两者需要 JDK 21。`--offline` 仅适用于依赖已在本地缓存时。更多步骤见[打包说明](../packaging.md)。

## 排查

- **启动时缺依赖或不兼容**：先核对 Minecraft、加载器、JVM 架构、库包及宿主编译版本。版本号范围宽不代表实际 API 一定兼容。
- **字符缺失或换行不同**：调用 `registerDefaults()`，或由宿主补充字体；内置字体是子集，不含所有 Emoji 与扩展字符。`WORD` 与 `CJK_UI` 是不同策略。
- **文字位置与点击不一致**：绘制和输入使用同一 `PresentationFrame`，转发完整输入生命周期；检查宿主是否重复应用像素比例。
- **模糊或物品图标异常**：先核对渲染线程、桥回调范围、资源的帧生命周期及支持的 framebuffer；离屏用例不保证与其他渲染模组兼容。

实际游戏、ARM64、长期资源使用和部分渲染模组组合仍需验证。来源及第三方声明见[来源记录](../provenance.md)，当前验证边界见[项目状态](../STATUS.md)。
