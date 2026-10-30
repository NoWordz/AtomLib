# Yoga 实现选择：LWJGL 绑定

2026-10-29，实施 T1。采用 LWJGL Yoga：Forge 1.20.1 使用 3.3.1，Fabric／NeoForge 1.21.1 使用 3.3.3。公共适配器只用两版本 API 的交集，不升级 MC 的 LWJGL，不同时发行两个布局后端。

AE 的唯一已发布版本 `org.appliedenergistics.yoga:yoga:1.0.0` 被排除：官方 Maven module 元数据声明 Java 21，实际 YogaNode.class 的 major version 为 65，Java 17 的 Gradle 依赖解析拒绝它。这是公共 Java 17 兼容门的失败，不是“Yoga 不能用于 MC”。不额外维护 Java port 的源码回移版本。

AE JAR SHA-256：`bacd086489858ca9fcd438721c75373ce8deab5e9fead23425a42a16fe1dc9a7`。该候选仅用于比较，不进入生产依赖。其 README 指向 Yoga 提交 `6455a848a76f433bdb48b2640b7f4644774c76fd`；不能把这个提交当作 LWJGL 两个 native 的 Yoga 源版本。

## 实际验证

Windows x64：AE／Java21、LWJGL3.3.1／Java17、3.3.3／Java17、3.3.1／Java21、3.3.3／Java21，五个独立进程组合各 6 项契约测试通过，无 skip。命令：

```powershell
.\gradlew.bat :probes:yoga:test -PprobeJava=17 --no-daemon --console=plain
.\gradlew.bat :probes:yoga:native331Test :probes:yoga:native333Test -PprobeJava=21 --no-daemon --console=plain
```

测试覆盖：row／column、grow/shrink/basis、最小／最大／百分比宽度、padding、wrap、显式边距、hidden、像素取整、测量约束、文字指标改变 dirty、零宽、无界约束、两树交替、移除节点、重复创建销毁。中英文与 Emoji 样本在这里使用 code point 模拟指标，只验证外部测量回调契约；实际 Skija 文字与 grapheme 的验证位于 runtime 测试，不能混称完整 Unicode 排版。

12／100／1000 节点样本分别预热后跑 100 次 stable 和 dirty 布局。原始耗时与 Java 线程分配保存在 [results.json](../evidence/yoga/results.json)。它们是短桌面样本，不是 MC 性能预算、持续内存测试或 ARM64 结果；JNI 的 native 分配不包含在 Java allocatedBytes 中。稳定树复用 Yoga 缓存，业务布局引擎进一步复用不可变快照。

生产 `YogaLayoutEngine` 的测试独立于探针。独立审查发现并修复重入释放、像素密度缓存、父级 gap 残留和 measure 模式切换缓存问题，见 [foundation 审查](../reviews/foundation.md)。生产适配器暂时拒绝 `wrap && gap > 0`：旧绑定没有原生 gap，单纯加末项 margin 无法正确表达换行行末和行间距。单行 Row／Column 的间距可用；不静默返回错误布局。

## 范围与来源

上游：[AE 源码](https://github.com/AppliedEnergistics/yoga)、[AE 发行元数据](https://repo.maven.apache.org/maven2/org/appliedenergistics/yoga/yoga/1.0.0/yoga-1.0.0.module)、[LWJGL 3.3.1](https://github.com/LWJGL/lwjgl3/tree/3.3.1/modules/lwjgl/yoga)、[LWJGL 3.3.3](https://github.com/LWJGL/lwjgl3/tree/3.3.3/modules/lwjgl/yoga)、[Yoga 外部测量契约](https://www.yogalayout.dev/docs/advanced/external-layout-systems)。本实现依据固定 API 与 AtomLib 行为契约独立编写，没有读取或复制 Tuui 反编译代码。

依赖版本在 `versions/dependencies.properties` 与 `versions/targets.json`；实际 artifact 哈希在 `versions/artifacts.sha256.json`。Windows ARM64 仅检查双 native 的资源路径和 PE 架构，未实机运行。MC 加载器、GPU、Iris/Bliss、客户端与专服启动不能由桌面 Yoga 测试替代。
