# 原版物品图标 M2 候选

状态：代码、CPU 图像槽、三端离线编译/验包和纯 GL 候选已通过主代理限定范围复核，详见 [review.md](review.md)；Minecraft 游戏验证与性能验收尚未完成。不得将此状态写成真实原版模型显示通过。M1 已复核成果和候选均保留。

## 实现

- UI 新增 ControlIcon 和 ActionButton 图像槽。正尺寸/null 预留，null/0 清除；同尺寸绑定不会改布局；与旧字符串图标或 Disclosure 角色冲突时拒绝。像素、命中和 clip 读取同一 PresentationFrame。
- ItemIconAtlas 拥有自有 RGBA8 颜色纹理、深度附件和 FBO。每帧清空并画当前可见项，只按增长策略复用容量；图像借用包装逐帧重建。ItemIconFrame 限制单个活动借用，关闭/失效后拒绝句柄，线程和 GLFW context 不匹配时拒绝。
- 三端 VanillaItemPass 分别走已核实的 GuiGraphics/DrawContext GUI ItemStack 模型路径。进入前刷新原版共享批次；设置自有正交投影、深度、光照和图块 scissor，提交后才借共享 DirectContext 导入。原版 pass 运行在 Skija pass 外，图像在正常控件层采样。数量/耐久条/tooltip 不在范围内。
- GL 状态由 GlStateScope 保存；RenderSystem 全局矩阵、shader 纹理/颜色/光照及 GlStateManager/shader Java 缓存由内部 ItemRenderState 保存。其反射读取基于本机已解析的私有签名，恢复器有独立合成状态测试；实际 MC RenderSystem 行为尚未实跑。不能只凭该测试宣称整合包兼容。
- 原生资源管理器注册不持有服务实例的 reload 回调，失效先关闭图像借用，再释放存储，最后共享桥 context；shutdown 沿用既有三端钩子。真实资源 reload 与客户端 shutdown 未运行。
- CreatorTrial 新增薄 TrialScreen、TrialClient 和专用 mixin 包。F8 只从标题页或无其它业务 Screen 时打开；先取本帧布局的可见图像槽，再准备原生请求并绘制，最后释放帧句柄。

## 小型 API 调整

生产 Fabric JAR 的原生 ItemStack 描述符为 intermediary，而要求的普通 implementation 消费工程使用 named 开发映射。直接构造 ItemIconRequest 的真实编译报错是缺 net.minecraft.class_1799。新增 fromNativeStack(key, Object, pixelSize) 工厂，在平台内校验真实原生 ItemStack 并复制，保留原有原生类型构造器。消费者通过该工厂编译；没有将 ItemStack 换成 ID/贴图，也没有让 Loom 处理 Lib sources。红灯见 CreatorTrial 的 consumer-first.log，绿灯见 consumer-second.log。

## 验证与证据

- root-final.log：core21/runtime30/UI97，0失败/错误/跳过。core/runtime 为缓存任务，UI 实际执行。
- 三端各38项 hiddenGL，包括7项新增图标/缓存恢复测试；实际命令含 --offline --no-daemon --console=plain 和 -PatomlibRenderEvidence=D:/AtomLib/build/modal-item-icons-evidence/<target>。各端最终日志为 build/modal-item-icons-m2-baseline/<target>-hidden-final-confirmed.log。
- 三端验包与六示例消费者通过；最终离线工具日志 build/modal-item-icons-m2-baseline/packaging-final.log。
- CreatorTrial build trialChecks：保留8场景并新增入口/专用mixin元数据检查，共9组 JavaExec 场景，非JUnit。CPU图片输出在 evidence/modal-item-icons-candidate/output；图片预留空图标槽，不放伪造MC像素。
- 实际运行期红绿：原生 pass 污染 UNPACK PBO 断言先红，加入归一化后绿。两个精确字节变异分别移除图像槽 clip、翻转导入原点，均被像素断言拒绝并原字节恢复。
- 早期两次写入命令的工作目录错误已定位并将4个源文件/1个测试文件移回正确目录；对应初次 UP-TO-DATE/找不到测试日志不计有效验证。一次GPU图片编码返回null，改为真实GL读回保存PNG；方向/颜色采样断言当时已通过。

机器可读摘要、完整SHA与性能原始数据索引：D:/AtomLib/build/modal-item-icons-candidate/results.json。新 source.zip/input.zip/Maven 均位于同一 candidate；M1 candidate、主代理review目录和原 execution baseline 保持原字节。

## 实测边界

纯GL图案PNG名称带 not-minecraft，只验证纹理管线。性能使用RTX5060 Laptop、16个32px合成图案、1冷+32热样本；CPU区段为prepare+close，GPUquery仅清除图案。CPU热调用成本在毫秒级且偏高，未做性能验收或优化，不能用GPU小区段数字宣称图标渲染快。各端精确统计见results。

Minecraft实际物品显示、时钟/指南针/附魔实际观感、游戏输入/重开、真实资源包reload、加载器启动、ARM64、Iris/特殊物品渲染器/其它GPU均NOT_RUN。未启动runClient/ComputerUse或下载游戏客户端/assets，未提交、推送、发布、部署或升版。公开beta3不变。
