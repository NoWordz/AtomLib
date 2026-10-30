# 三平台打包与 Maven 消费约定

本文维护稳定打包约定。当前完成度与未解决问题见 `docs/STATUS.md`；验证记录见 `docs/verification-2026-10-30.md`。构建通过不等于加载器实机验收。

## 工程与坐标

| 独立工程 | Java / wrapper | 加载器构建链 | Yoga |
|---|---|---|---|
| `platforms/1.20.1-forge` | 17 / 8.8 | Forge 47.4.10 / ForgeGradle 6.0.54 | 3.3.1 |
| `platforms/1.21.1-fabric` | 21 / 8.8 | Loom 1.7.4 / Fabric Loader 0.16.9 | 3.3.3 |
| `platforms/1.21.1-neoforge` | 21 / 9.2.1 | NeoForge 21.1.235 / MDG 2.0.141 | 3.3.3 |

- 三端统一版本 `0.1.0-local`；平台元组在 `versions/targets.json`，Skija/types 版本读取主代理维护的 `versions/dependencies.properties`。
- 本地坐标为 `com.atom.lib:atomlib-<目标>:0.1.0-local`，发布目录为 `D:/AtomLib/build/test-maven`。没有配置外部仓库发布、商店发布或部署。
- 三个工程分别复用 core/runtime/ui 的源码工程，公共字节码 Java 17。输出隔离到各平台 `build/modules/<模块>`。玩家 jar 只合入 core/runtime/ui；devtools 已移出依赖、settings 及合包输入。
- 两个示例位于 `examples/a`、`examples/b`，各有三个独立 settings/wrapper 工程。只声明对应 AtomLib 平台坐标；共享的示例源码只在各自 example 目录。没有 includeBuild、源码 project 依赖、mavenLocal 或 AC 源码引用。
- Wrapper 从 AC 对应工程复制，保留脚本许可头，每个副本附 `gradle/WRAPPER-NOTICE.txt`，声明上游 Gradle Apache-2.0 来源。

## 发行内容与依赖导出

每个目标一个主 jar，另有 sources/Javadoc。合包前的 slim/remap/JarJar 中间包放在 build/intermediates。正式主包由 `distributionJar` 生成。

每个主包的七个嵌套依赖为：

1. skija-shared 0.143.18。
2. types 0.2.0。
3. skija-windows-x64 0.143.18。
4. skija-windows-arm64 0.143.18。
5. 对应目标的 lwjgl-yoga Java 包。
6. 同版本 lwjgl-yoga 的 natives-windows。
7. 同版本 lwjgl-yoga 的 natives-windows-arm64。

不带字体、AC 类、devtools 类或 lwjgl-core。MC 自带的 lwjgl-core 保持原版本。平台 Maven 的 compile API 导出 shared/types/Yoga Java 包；四份 native 以 runtime 依赖导出，保证消费者开发类路径可用。排除外部依赖的隐式传递，避免带入 lwjgl-core。

Forge/NF 使用 JarJar，Fabric 使用 Loom include。Forge 的 ContainedJarIdentifier 只有 group/artifact，不能区分 Yoga classifier：最终 metadata 为两个 classifier 增加独立 artifact 选择身份，并保留版本与正确路径。

Yoga 两个 Windows native 原包的 JPMS 名均为 `org.lwjgl.yoga.natives`。Forge/NF 对这两个纯资源 jar 移除 module-info，生成分别为 `org.lwjgl.yoga.natives.windows.x64`、`org.lwjgl.yoga.natives.windows.arm64` 的 Automatic-Module-Name，保留原资源、DLL 字节及路径，并标 FMLModType=LIBRARY。Fabric 沿用 Loom 注入的嵌套模组元数据。Skija 两个 Windows 原包本身已有不同模块名，无需同样处理。

实际 native 路径：Skija 为 `io/github/humbleui/skija/windows/{x64,arm64}/skija.dll`；Yoga 为 `windows/{x64,arm64}/org/lwjgl/yoga/lwjgl_yoga.dll`。PE Machine 分别校验 0x8664、0xAA64。

MavenPublication 和 java component 的 apiElements/runtimeElements 必须共同导出 distributionJar。仅 setArtifacts 会导致 GenerateModuleMetadata 报组件原产物被移除。不要退回 slim jar 或关闭 Gradle metadata 来绕过组件产物一致性检查。

## 本地验证入口

先设置 JAVA17_HOME / JAVA21_HOME，并显式提供宿主测试字体：

```text
python tools/run_packaging.py all --jobs 1 --test-font <host-font-file>
python tools/test_verify_artifacts.py
python tools/test_verify_consumers.py
python tools/verify_ui_examples.py
python tools/package_maven_input.py
```

生产 jar、Java API/POM、PE 架构、登记/模块名唯一性、递归类和资源扫描都必须通过。
六个独立消费者只使用本地 Maven 输入，不能靠源码替换或额外补依赖伪装通过。

Common 入口不能在字段、签名或初始化中引用 client/native。Forge/NF 使用 side=CLIENT
依赖；Fabric 由客户端 bootstrap 检查版本，不能通过全端 depends 强迫专服安装。
这些静态约束不代替缺库客户端、专服误装及实际加载验证。客户端退出时还需在 GL context
销毁前释放 bridge；当前尚未完成的平台生命周期接线见 STATUS。

CI 接收明确固定的输入 URL/归档 SHA，并核对 AC 锁内每个 jar 的 SHA；未配置输入应明确失败。
当前 Windows-only 玩家支持范围不因 CI 主机系统而改变。Linux CI 的测试 native 配置尚待修正。
