# Skia backdrop 模糊 vs 面板式 raw GL 模糊：机制、性能、兼容性、观感

2026-11-01，AtomChat HUD 横幅闪烁三轮排查的完整教训（Gnetum 实锤 + 显示链路 alpha 参与成像实锤）。本文供 AtomLib 侧做 `UiCards.drawNotificationBackdrop` 与后续磨砂面决策使用；排查正本见 AtomChat `docs/STATUS.md` 2026-11-01 三节与 `reference_skija-blur-in-mc.md` 记忆。

## 两种实现

| | A：Skia backdrop filter | B：面板式 raw GL |
|---|---|---|
| 代表 | `UiCards.drawNotificationBackdrop`（`canvas.saveLayer(SaveLayerRec(bounds, paint, blur))`，第三参=backdrop） | `AtomChat PanelBlurRenderer`（`glBlitFramebuffer` 抓主帧缓冲区域 → kawase 多 pass → 圆角 mask 画回） |
| 读取背景 | Skia 拷贝当前层内容，**按 premul 语义解释** | raw GL 采样，**只取 RGB，输出 alpha 来自圆角 mask** |
| 写入 | 正常 Skia 绘制（写 dst alpha） | 画回时 RGB+自定 alpha，不依赖 dst alpha |

## 兼容性（决定性维度）

A 的致命前提：**背景内容必须是合法 premul（每个像素 R,G,B ≤ A）**。两类破绽：

1. **光影包**：Iris 的 final pass 只写 RGB（Bliss 2.1.2 `dimensions/final.fsh:118` 只写 `gl_FragColor.rgb`），主帧缓冲 alpha 逐帧逐材质未定义 → 模糊输出随垃圾 alpha 波动 = 闪烁。注意：不是所有环境都触发——CCB fabric（Iris 1.8.0 + Bliss）实测 alpha 合法、不闪；触发与环境相关，无法客户端预判。
2. **宿主显示链路**：有玩家环境把主帧缓冲 alpha 参与最终成像（未定位到具体读者）。实测：对主帧缓冲 alpha 的**任何写入**——全视口或区域级 `colorMask(仅A)+clear(0,0,0,1)`——都会把写入区域显示成纯黑。教训升格为铁律：**HUD 上绝不写主帧缓冲 alpha，也绝不按 premul 读它**。
3. **HUD 管线劫持 mod**：Gnetum 把 HUD 分帧缓存（3 pass、每帧只画 1 个）画进自己的 FBO。Skia backdrop 的读与写都落进"当前绑定"，而 AtomLib 桥的 Skia 输出钉死主帧缓冲 → 分裂脑（底板进 Gnetum 缓存、卡片进主帧缓冲、按 pass 节奏交替=闪烁）。raw GL 预画若同样在 HUD 层里做也会中招——已用 `GnetumHudCompat`（反射钉 per-element 缓存开关 OFF，fail-open）解决，但说明：**在 HUD 层里做任何"读主帧缓冲"的操作，都要考虑被管线劫持 mod 改写时序的情况**。

B 对 1/2 免疫（不读 dst alpha、只写采样自 RGB 的内容）；对 3 同样脆弱（在 HUD 层内调用时），需同样的 Gnetum 兼容（AtomChat 已做：横幅层被钉出缓存，逐帧 uncached 绘制）。

## 性能

- A：一次 saveLayer+一次 blur pass，Skia 内部完成，小矩形开销极低；额外成本是每帧一个离屏层（受 `resetGLAll()` 影响，资源每帧重建）。
- B：每帧 blit + 5 pass kawase + 一次画回，固定 6 次小矩形绘制 + 2 次纹理拷贝；AtomChat 实测面板整屏尺寸 p95 < 0.5ms（1080p，含提交等待）。小面积（横幅条）远低于此。B 有"每 2 帧刷新+静止降频"的门可再省，但 **HUD 半透明小面积场景下复用上帧纹理会被感知为闪烁**（背景在动），要么每帧刷新要么不用 B。
- 结论：A 略省；B 的成本在小矩形下不构成问题，但不要套降频门到动的半透明场景。

## 观感

- A：Skia 高斯，半径小（s(6)），柔和、与 Skia 内容同色域，边缘由 CLAMP 处理；适合"卡片自身带一点磨砂"的贴身质感。
- B：Kawase 5 pass（spacing 2→8），模糊更"化开"，圆角 mask 精确；适合"从宿主画面抠一块做底板"。横幅栈实测观感与面板同材质，一致性好。
- A 在 alpha 合法环境下观感无差；差异主要在模糊半径感与边缘。

## 决策规则（AtomLib 侧照此选）

1. **背景内容是 Skia 本帧自画的**（浮层叠在自己 UI 上，如面板内的横幅、卡片浮层）→ 用 A，便宜且安全。
2. **背景是宿主/世界/光影输出的**（HUD 悬浮元素、跨进程回读）→ 用 B，且：不写主帧缓冲 alpha；调用点必须考虑 HUD 劫持 mod（Gnetum 类）的时序；不要对会动的半透明区域用隔帧缓存。
3. 无法保证 2 的环境检测时，退路是"无磨砂、半透明 fill + 可读性地板"（notificationFill 的 50% 地板设计就是这条退路）。
4. 永远不要用"归一主帧缓冲 alpha"来救 A——已实测两次（全视口/区域级）在该用户环境全部变黑。

## AtomLib 现状与建议

`UiCards.drawNotificationBackdrop`（A 实现）当前唯一调用方是 AtomChat：面板内横幅（场景 1，安全）继续用它；HUD 横幅已改 B（`NotificationHudOverlay` pre-pass 调 PanelBlurRenderer，`drawBanner(backdropPreDrawn)` 跳过 A）。若 AtomLib 后续把"宿主背景磨砂"做成通用能力，建议按 B 的契约封装（调用方给设备矩形与圆角，库内 raw GL 完成，明确声明不读写 dst alpha），并把 A 的 javadoc 标注"背景必须为 Skia 本帧自画内容"的承载约束。
