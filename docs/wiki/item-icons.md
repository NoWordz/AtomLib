# 原版物品图标

`MinecraftRenderBridge.get().itemIcons()` 是桥拥有的原版 GUI 物品离屏服务。公共 `core/runtime/ui` 不引用 Minecraft 类型；平台 `ItemIconRequest` 接收本平台的原生 ItemStack，构造和 getter 均复制以保留数量、NBT/组件。空 stack 产生透明装饰。不要关闭桥拥有的服务，只关闭每次返回的 `ItemIconFrame`。

## 一帧的接线

1. 稳定创建 ActionButton，并 `button.icon(null, 24)` 预留逻辑尺寸。非空的旧字符串 icon 或 DISCLOSURE 角色与图像槽冲突时明确拒绝。`icon(null, 0)` 清除槽位；同尺寸替换句柄不改变量测，尺寸变化后重新布局。
2. 先产生本帧 PresentationFrame。按本次图像槽的 content 与 clip 交集选可见请求，不能用上帧命中快照。物理尺寸通过 `MinecraftItemIcons.pixels(logicalSize * frame.renderScale(id), pixelsPerUnit)` 换算。
3. 刷新 DrawContext/GuiGraphics 宿主批次，在 Skija pass 外调用 `prepare(requests, frameId)`。服务也会提交原版共享 GUI/entity buffers。frameId 应来自单调客户端时间，不能每次打开 Screen 从 1 重新编号。
4. 把 `frame.icon(key)` 绑定到原按钮的同尺寸槽位，在一次桥 draw/drawFrame 中绘制该 PresentationFrame。图像属于正常 Skija 控件层，继承裁剪、缩放、透明度和模态遮罩。
5. draw 返回后关闭 ItemIconFrame，清除业务控件上的旧句柄但保留预留尺寸。未关闭上一帧时再次 prepare 会拒绝。过期句柄会报错，不采样失效纹理。

生产 Fabric JAR 是 intermediary 映射，而普通 `implementation` 消费者的开发 ItemStack 是 named 映射。直接调用生产 JAR 的原生类型构造器会因 `net.minecraft.class_1799` 缺失而无法编译。此类二进制消费者使用 `ItemIconRequest.fromNativeStack(key, nativeStack, pixelSize)`；该工厂在平台内校验真实 ItemStack 类型并复制，保留原生类型构造器给正常映射的接线。它不接受物品 ID 或贴图替身，也不需要 Loom 处理 Lib sources。

完整可编译的消费者为 `D:/AtomLib-CreatorTrial/src/main/java/dev/creatortrial/TrialScreen.java`。这个 Screen 的 F8 入口只允许在标题页或没有其它业务 Screen 时打开，客户端和 mixin 使用独立包。

## 存储、状态与生命周期

离屏目标由库拥有 RGBA8 颜色和独立深度附件，按批量最大图块尺寸及容量增长复用；每帧清空并绘制当前可见项，不缓存物品内容。图块有透明保护边，Skija 借用图像使用 BOTTOM_LEFT 原点、PREMUL alpha 和严格源矩形。纹理复用前提交旧 Skija 工作；每帧重新建立 Image 包装，先释放图像借用，再删除自有 FBO/附件，最后关闭桥的共享 context。

实际原生绘制走各平台 GuiGraphics/DrawContext 的 GUI ItemStack 路径，包含原版模型选择和附魔绘制；不画数量、耐久条、tooltip。批次在导入纹理前完整提交。原生 pass 外层保存实际 GL 状态，同时 `ItemRenderState` 保存 RenderSystem 全局矩阵、shader/纹理/颜色/光照值，以及 GlStateManager 和 shader 的 Java 缓存。该内部恢复器通过已核实的 @DontObfuscate RenderSystem 字段和缓存嵌套类型反射读取；无法访问时明确失败，避免留下半恢复状态。MatrixStack 通过原生 push/pop 保留。异常带平台及物品 key。

服务首次初始化向原生可重载资源管理器注册不持有实例的失效回调；reload、bridge.invalidate 和 shutdown 都使帧句柄失效。必须在原渲染线程及同一 GLFW context 调用，不能在 Skija pass 内 prepare/invalidate/close，不能持有 DirectContext 作为第二个 owner。

## 已验证范围

CPU 位图测试只验证图像槽合成、裁剪、缩放与模态层级。hiddenGL 的红蓝/透明图案只验证 FBO、深度、纹理导入、方向、预乘透明边、复用、异常恢复、失效和过期句柄。图像文件名明确标记 not-minecraft。三端游戏原生物品模型实际显示、实际资源重载和 RenderSystem 恢复仍为 NOT_RUN；原生路径仅做本地签名/源码核实和离线编译。

自定义模组物品渲染器、修改渲染目标的特殊 RenderType、跨 context 和其它图形模组未做兼容验收，不静默改为贴图或假图标。性能 JSON 中 CPU 指 prepare+close，GPU 区段仅是合成 GL 图案清除；它们不是 MC 物品性能，且没有据此设定达标阈值。
