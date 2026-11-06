# 面板性能修复执行记录

用户已批准：依据 103433.jfr 的实测定位，一轮静态分析并修复 issue 中能确认的开销，只保留一个实现子代理，不要求用户再次录制。主代理负责并行的 UiSession/PresentationFrame 实现与最终集成验证。

目标：降低单次 Skia capture 的查询成本，并消除已确认的重复布局、塑形和线性查询开销。

## 代码范围与顺序

1. 子代理：GlStateScope / SkijaRenderBridge，公共完整快照保持契约，Skia 专用范围按原生源码和真实 GL 恢复测试确定，JFR 数量字段反映新范围。
2. 子代理：TextService 塑形缓存与 Shaper 生命周期、Yoga revision 脏标记，包含字体换代/异常/关闭回归。
3. 主代理：UiSession 控件索引、PresentationFrame 同帧缩放几何复用，保留焦点、取消和模态语义。
4. 子代理：图标反射/重复复制/flush、背景 uniform/容量/校验，及可确认的次要绘制开销；保持动态内容、效果与生命周期。
5. 集成：根套件 → Fabric 隐藏 GL → 三端 build/verifyArtifact 和其余 GL → 制品内容检查 → 无游戏进程时备份部署。主代理独立审子代理实现，子代理以 code-reviewer 角色审主代理两文件，不另开并发代理。

## 已执行

- [x] 263 份现有源码/配置按字节归档到 `build/perf-fix-20261105/before-source.zip`，含原有未提交实验和诊断，SHA 清单单独保存。
- [x] 主代理新增 `PresentationFrameTest.controlsAtOneScaleSharePaintingGeometryWithoutSharingAcrossFrames`，原实现行为红灯：同缩放创建不同几何对象。
- [x] 实现每帧按缩放缓存绘制几何；控件按 NodeId 索引；显示成员检查使用 box Map；替换列表使用 Set，避免重复线性查找。
- [x] `:ui:test` 完整套件通过，日志 `build/perf-fix-20261105/ui-green-suite.log`；红灯 `ui-red.log`。
- [x] Yoga 新 lambda 同 revision 不再标脏，同布局复用 snapshot；缓存命中仍更新最新 callback，约束或 revision 变化重新测量。新增测试先红后绿，UI 完整套件通过，日志 `yoga-red.log` / `yoga-green-suite.log`。此项由主代理接手，避免子代理同文件写入。
- [x] 子代理其余实现、证据与定向测试，交付 `docs/evidence/perf-fix-20261105-implementation.md`。
- [x] 独立复核抓到 Yoga 失败事务恢复 P1，补真实红灯并修正 nativeStateValid 状态，再经原 reviewer 复核关闭。
- [x] 修正后整套验证重跑、三端构建/原生/制品检查通过，12:31 三端备份部署并独立读回验哈希。最终记录见 `docs/evidence/render-performance-fix-20261105.md`。

## 执行约束

不启动游戏、不提交/推送/发布，不改 AtomChat。原始 JFR、既有实验及旧候选保留。新证据统一隔离在 `build/perf-fix-20261105/`，隐藏 GL 显式传 `atomlibRenderEvidence`，根套件显式传 `atomlibTestFont`。主代理不把静态/自动测试结果写成游戏 FPS 改善。
