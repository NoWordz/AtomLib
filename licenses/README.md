# 许可原文归档

这是 T0 取得的许可证据集，不表示每个候选依赖均已打入 AtomLib。新库完整 Apache-2.0 在根 `LICENSE`；允许搬入的 AC 内容保持 `AtomChat-MIT.txt`，不改写原版权。每份外部原文的官方 URL、获取日期、字节数与 SHA-256 在 `sources.json`。

Skija 的独立 NOTICE 已原样保留。Lucide 完整许可同时含 ISC 与 Feather 衍生部分的 MIT，另保留实际图形对照版本 Feather 4.29.2 MIT；不能删掉其中版权行。CC0 文本来自 Creative Commons 自有仓库（站点直链访问失败后使用官方仓库），未自行拼接简版许可。

Yoga／LWJGL 是 T1 候选许可证据，尚未决定发行后端。Skia 本体 BSD 原文不覆盖 native 内全部依赖；当前 native 发行准入排除，具体缺口见 `../docs/provenance.md` 第 7 节。不能仅把这个目录打进包就声称补齐了尚未核实的第三方声明。

未来打包者负责根据实际依赖保留所需原文与 NOTICE，核对上游文件头、修改说明和成品内覆盖。T0 未写 Gradle 资源合并规则，未运行产品构建。目录中未选中的候选许可不是产品使用该组件的声明。
