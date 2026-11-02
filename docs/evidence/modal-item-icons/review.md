# M2 主代理独立复核（2026-11-02）

结论：M2_CODE_CPU_GL_REVIEWED_MC_NOT_RUN_PERF_NOT_ACCEPTED。在本次代码审查、CPU 和纯 GL 范围内未发现新增的确定缺陷；原版物品实际显示及性能可用性仍未验收，不称完整游戏实用性通过。

## 审查与实际重跑

已读取 ItemIconAtlas/Frame、ItemRenderState、三端原生请求/离屏服务/VanillaItemPass、桥生命周期和 reload 接线、控件图像槽及皮肤，以及 CreatorTrial Screen、客户端、专用 mixin 和本帧图标绑定路径。原生类型工厂仅解决二进制消费者编译时的映射描述符边界，不能据此推断开发客户端或正式游戏运行通过。

主代理实际执行：

- 根 JDK17：`:ui:test --rerun-tasks --offline --no-daemon -PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf --console=plain`，UI 97 项实际执行，失败、错误、跳过均为零。未重跑 core/runtime 测试。
- Fabric 平台 JDK21：`hiddenGlTest --tests "com.atom.lib.client.render.item.*" --rerun-tasks --offline --no-daemon --console=plain -PatomlibRenderEvidence=D:/AtomLib/build/modal-item-icons-main-review/m2-fabric-gl`，6 项图标纯 GL 测试和 1 项合成 Java 状态恢复测试通过。后者没有运行真实 RenderSystem。
- CreatorTrial JDK21：`build trialChecks --offline --no-daemon --console=plain`，9 组 JavaExec 场景实际执行通过；其中入口检查是元数据/编译证据，未在游戏中触发 F8。Lib sources 重映射跳过。

完整日志：`build/modal-item-icons-main-review/{m2-ui-executed.log,m2-fabric-gl.log,m2-consumer-validation.log}`。本轮没有重复三端全套 38 项 GL、三端打包或六消费者；其结果属于子代理执行记录。既有裁剪/原点负控日志已查阅，本轮未再变异生产文件。

## 制品核对

重新计算三端二进制及 sources JAR、source.zip、input.zip、CreatorTrial JAR 与锁摘要，均与交付记录一致。三份 sources JAR 各核对了 111 个可匹配的共享及平台 Java 源文件，全部与当前工作区逐字节相同。消费者包确含 Screen、客户端和独立 mixin 六份 class，mixin 元数据带 refmap。详细结果为 `build/modal-item-icons-main-review/m2-artifact-review.json`。

独立重算保护清单中 627 个文件，全部保持原字节；包含 M1 候选/证据及旧复核目录。新验证输出使用新的主代理子目录，未覆盖子代理 GL 图案与性能原始记录。

## 性能与尚未覆盖

子代理热样本约 9.43/10.41/9.78ms 的 CPU 区段包含 prepare+close，GPU query 只覆盖合成清除图案，不包含 Minecraft 模型，二者不能互相替代。代码在 prepare、纹理导入和关闭期间多次进入完整 GL 状态保护；这是待拆分计时的候选成本，未经分段剖析，不作为已确认的唯一根因。

本次独立纯 GL 测试也生成时间数据，但与编译/CPU 回归并行，不用于替换子代理性能基线或宣布优化。尚未完成真实物品模型/附魔/动态上下文、真实 RenderSystem 恢复、资源 reload、F8 输入、加载器启动、其它 GPU/ARM64/Iris/特殊渲染器及最终观感验证。不自动启动 Minecraft 或下载游戏资源；不提交、部署、发布或升版。
