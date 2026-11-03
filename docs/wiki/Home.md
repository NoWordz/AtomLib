# AtomLib 文档

AtomLib 为 Minecraft 模组宿主提供 Skija 绘制、Yoga 布局和可选控件。宿主仍负责自己的页面、业务状态和生命周期。

**当前源码：0.3.0 开发候选** · [公开下载](https://github.com/NoWordz/AtomLib/releases) · [问题反馈](https://github.com/NoWordz/AtomLib/issues) · [English](English.md)

## 你想做什么？

| 我想…… | 阅读 |
| --- | --- |
| 安装依赖、接入一个界面 | [快速开始](quickstart.md) · [开发环境](getting-started.md) |
| 理解库与宿主如何分工 | [架构与生命周期](architecture.md) |
| 使用按钮、滑条、弹窗与输入 | [控件与宿主接线](controls.md) |
| 处理字体、换行、图片与资源释放 | [文本与运行时](runtime.md) |
| 使用主题与默认皮肤 | [AtomDefault 与外观](design-package.md) |
| 接入背景模糊或原版物品图标 | [背景模糊](backdrop.md) · [物品图标](item-icons.md) |
| 构建、检查产物或排查问题 | [开发与排查](development.md) |

## 三条先知道

1. 公共发行版仍是 `0.1.0-beta` beta3；本文同时记录尚未发布的 0.3.0 能力，不要把二者当成同一制品。
2. 当前客户端包内置 Windows x64 和 ARM64 原生依赖；ARM64 尚未游戏内验证，Linux 和 macOS 未打包。
3. 这是界面底层与可选控件，不是完整聊天、富文本或 IME 框架。宿主仍要转发输入并按合同管理资源。
