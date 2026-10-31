# Runtime：宿主资源、字体与纯 CPU 绘制

本轮实现 T3 的纯 runtime 资源生命周期与 CPU Canvas 作用域，以及 T4 的宿主字体、统一纯文本布局、位图与 SVG 存储接口。**这不代表完整 T3/T4 已验收**：没有 MC GL 桥、加载器启动、GPU 状态恢复或通用资产迁移。所有新增实现独立编写，使用 Apache-2.0；没有读取、复制 Tuui ARR 反编译实现。方向 F（2026-10-31）起库内附带子集化 Noto Sans SC 默认字体（见下），此外仍不复制任何字体文件。

## 稳定接口与接入

所有包均以 `com.atom.lib` 为前缀。

| 类型 | 契约 |
| --- | --- |
| `AtomLibRuntime` | `open()` 或 `open(namespace)` 显式加载 Skija native，返回宿主独立的 `RuntimeHandle`。加载这些类不会自动加载 native。 |
| `RuntimeHandle` | `namespace()`、`isClosed()`、`requireOpen()`、`own(AutoCloseable)`、幂等 `close()`。关闭释放宿主资源，不卸载进程级 DLL。 |
| `font.FontId` | 注册表内的字体身份。不同字体、字重或样式文件应注册不同 FontId。 |
| `font.FontSource` | `read() throws IOException` 提供可重复读取的字体字节；`file(Path)` 读取宿主路径，`bytes(byte[])` 保存防御性副本，`resource(String)` 读取 classpath 资源（库自带默认字体走这条路）。没有自动选择系统字体或绑定 AC 路径。 |
| `font.FontRegistry` | `register`、`setFallbacks`、`registerDefaults`、`resolve`、`openFont`、`reload`、`generation`、`close`。构造需传已打开的 RuntimeHandle，注册表由该 handle 管理。 |
| `text.TextRequest` | 文本、FontId、字号、ARGB 颜色、maxLines 和 ellipsis；四参数构造默认不限行且不加省略号。 |
| `text.TextService` | `layout(TextRequest, Constraints) -> TextLayout`；`draw(Canvas, TextLayout, x, y)` 绘制同一布局。构造参数可设置缓存条数与 UTF-16 字符预算；`close()` 清空缓存。 |
| `text.TextLayout` | 不可变 `size()`、`lines()`、`truncated()`；`hitTest(x,y)`、`selection(from,to)` 读取该布局中的相同几何。没有可由外部关闭的 native 对象。 |
| `resource.ResourceScope` | 宿主 namespace、generation、`accept(ticket, resource)`、`release(resource)`、`reload()`、幂等 `close()`。 |
| `image.ImageStore` | `put`、`complete(id,ticket,bytes)`、`acceptDecoded(id,ticket,Image)`、`draw`、`reload`、`generation`、`size`、`close`；默认 LRU 上限 128 项。 |
| `image.SvgStore` | 与 ImageStore 相同的装载/代次/关闭接口，接收 SVGDOM 的所有权；`draw(id,canvas,bounds,Integer tint)`，null 保留原颜色。 |
| `render.RenderSession` | 借用宿主 Canvas，`paint(CustomPainter)`，幂等 `close()`。不创建 GPU context 或 MC framebuffer。 |

字体与文本最小接入：

```java
try (RuntimeHandle runtime = AtomLibRuntime.open("example");
     FontRegistry fonts = new FontRegistry(runtime);
     TextService text = new TextService(fonts)) {
    FontId body = new FontId("body-regular");
    fonts.register(body, FontSource.file(hostSelectedFont));
    // 粗体请另注册 body-bold，并在对应 TextRequest 中使用该 FontId。
    TextLayout layout = text.layout(
            new TextRequest("宿主提供字体", body, 18, 0xff202020),
            new Constraints(Constraints.Axis.atMost(320), Constraints.Axis.undefined()));
    text.draw(borrowedCanvas, layout, 12, 12);
    int offset = layout.hitTest(pointerX - 12, pointerY - 12);
}
```

上例的 Path、Canvas、指针坐标均由宿主提供，不会保存为库的全局状态。资源的 try-with-resources 关闭顺序应让 TextService 先于 FontRegistry、RuntimeHandle；TextService 只持有 Java 布局缓存，宿主负责显式关闭它。FontRegistry、ImageStore、SvgStore 已登记到 handle；手动提前 close 后再关闭 handle 是幂等的。

## 字体所有权与重载

- Registry 独占其创建的 Typeface。解析过程中临时 Data 使用 try-with-resources 关闭；默认 FontMgr 是 Skija 的借用单例，库不关闭它，也不把它返回给宿主。
- `openFont(id,size,grapheme)` 每次返回新的、由调用者关闭的 Font，**不会缓存或重复返回可由外部关闭、修改的 Font**。Font 保留自己的 native Typeface 引用，因此已返回的 Font 不因 registry reload 而悬空；调用者仍须关闭它。
- 不同宿主使用不同 registry，即使 FontId 同名也不会共享来源、回退序列或代次。字体变更、回退变更及成功重载增加 generation。新字体解析失败不替换旧项；全量重载中任一来源失败，已构造的新 Typeface 释放，旧注册表及 generation 保留。
- `registerDefaults()` 把库自带的子集化 Noto Sans SC（`com/atom/lib/fonts/bundled-default.otf` 保留变量 wght 轴 + `bundled-default-bold.otf` 静态粗体，OFL，字符集契约 `tools/default-font-charset.txt`）注册到保留 id `atomlib/default`、`atomlib/default-bold`，并**追加**到现有回退链尾部；宿主字体仍按 primary → 宿主回退 → 默认字体的顺序生效。某个默认面加载失败只记 warning 并跳过（对齐 AC FontManager），不拖垮文本渲染。默认包刻意不含 Emoji、成句韩文与 GBK 扩展；需要更多覆盖时用 `tools/subset_default_font.py` 重新子集化。
- `setFallbacks(List<FontId>)` 仅接收已注册的字体。回退按**完整 grapheme**选择一个可覆盖该簇的字体，优先 primary。全部字体都缺少字形时保留 primary 的缺字行为，不捏造 Emoji 或字体资产。
- 字重不由颜色、字号或一个隐式 bold 标记猜测；用不同 FontId 绑定实际字重文件。当前没有 TTC face-index、可变字体轴配置、自动系统字体发现或样式匹配 API。
- TextService 在字体锁内布局、绘制并校验代次。旧布局在字体重载后仍可读取历史几何，但不能再次绘制；宿主应重新 layout。Yoga dirty 通知属于上层接线，应比较 generation 后触发，runtime 没有修改 Yoga 树。

## 文本支持范围与限制

当前是 **Plain LTR** 文本服务，适用于普通从左到右的 UI 标签与说明文字。

- 使用 Skija `Shaper.makeShapeDontWrapOrReorder()` 对连续同字体 run 进行 shaping；测量和绘制采用相同文本、FontId、字号及代次。native Font、TextLine、Shaper、Paint 均在局部作用域释放，TextLayout 不持有 native 生命周期。
- 使用 Java 17 正则 `Pattern` 的 `\X` extended grapheme 边界换行；覆盖中文无空格、组合符、代理对、ZWJ Emoji、区域旗帜序列，避免按 char 切碎这些簇。此能力受运行 JDK 的 Unicode 数据版本约束，不保证所有更新版本 Unicode 序列。
- **这是 grapheme wrap，不是完整 UAX #14 断行算法，也没有完整 CJK 禁则。中文逗号、句号等标点可能被推到下一行行首；这属于断行品质限制，不是 glyph 被截断。** 目前也不优先按英文单词或语言词典断行。
- 拒绝孤立 UTF-16 surrogate、RTL 强方向字符、显式双向排版控制字符和不支持的控制字符，避免返回虚假的命中/选区坐标；没有 bidi 段落引擎。没有富文本、IME、编辑状态、光标导航、完整脚本/语言段落处理或彩色 Emoji 外观保证。
- 支持 CR、LF、CRLF 显式换行及尾随换行的空光标行。空文本没有行，natural size 为零；exactly 约束仍返回指定尺寸。
- 支持宽高约束、maxLines 与末尾省略。不可再拆的单个 grapheme 或省略符可能宽于可用宽度，此时保留完整簇并由布局 viewport 裁剪；不声称零宽槽也能完整显示。高度不足一行时不绘制部分行。
- hit 与 selection 坐标相对 TextLayout 左上角，偏移采用 UTF-16 单位，正常命中落在 grapheme 边界。selection 为半开区间；端点落在簇中时选区扩展到整个簇，几何限制在 viewport 内，不高亮合成省略符。超出行范围的 hit 取最近行/光标边界。
- draw 借用 Canvas、不关闭它，保存/恢复 save 栈并按 layout viewport 裁剪。外部或过期 generation 的布局明确拒绝绘制。
- 缓存 key 包含完整 TextRequest、Constraints 和字体 generation，不是只按字号。默认最多 256 项、262144 个 UTF-16 单位；超字符预算的请求不进入缓存，单次请求超过 32768 单位明确拒绝。生成旧代次缓存会清理。

这些限制需要后续专门的文本品质任务处理，不能因 gallery 可绘制就宣称拥有完整排版引擎。

## 资源、异步与线程边界

ResourceScope 的同步部分只管理所有权与代次；真正调用资源 close 在 scope monitor 外完成，避免清理回调阻塞其他代次判断。资源按登记的逆序清理，普通 Exception 不阻止余项释放，错误汇总为 IllegalStateException。close 标记与所有权分离是幂等的。reload 先更新 generation，再释放旧资源。

`accept(ticket,resource)` 转移资源所有权；如果 scope 已关闭或票据过期，返回 false 并立即释放传入资源。release 用于缓存驱逐，只释放仍属于当前 scope 的对象一次。票据只对**产生它的 scope/store 实例**有效，不能跨宿主混用相同数值。共享同一个 native 对象到多个 scope/store 不受支持，所有权转移后调用者不得继续使用或关闭它。

ImageStore/SvgStore 同样使用自己的 namespace、generation 和 ResourceScope。异步调用方必须在发起工作前获取 ticket；完成后调用 `complete(id,ticket,bytes)`，或在宿主已完成解码时调用 `acceptDecoded`。后者在参数校验通过后接管 native 对象，过期、store 关闭或 runtime 关闭时也负责释放它。字节装载会先拒绝已经过期的任务，并在解码后再次检查，覆盖解码期间发生 reload/close 的竞态。

图像 store 的 map、绘制和代次切换在实例锁内串行，native 资源不会在该 store 自身的 draw 中途由该 store 释放。位图支持居中 contain/cover 与圆角 clip；SVG 支持 viewport、viewBox 渲染与统一 alpha-mask 着色，SVGDOM 的 container size 是可变的，因此同实例绘制串行化。无内置资产文件、资源包路径解析、自动下载、动画图片、SVG 外链策略、SVG 文本与 FontRegistry 集成或按 native 字节数计算的缓存预算；当前图形缓存以条数为界。

**这些保证不等于任意线程/GPU 安全。** 字体注册、文本布局和本轮测试中的离屏 Raster 图像/SVG 可在受控 CPU 线程执行；宿主仍负责对象所有权、任务调度，以及不在关闭期间自行使用已转移的对象。若将 GPU Image 或 GPU Canvas 交给这些接口，创建、绘制、reload、驱逐和关闭必须由宿主安排在正确 GPU/context 线程；ResourceScope 不会自动迁移线程或调度 GL 删除。不要从任意网络/工作线程关闭拥有 GPU 资源的 runtime。

RenderSession 在构造线程上 paint，拒绝同线程上的递归 Skija pass，包括另一 session。异常与多余 save 会恢复到入口 save count，并清除递归标记；session 关闭不关闭借用 Canvas。CustomPainter 不得 close Canvas，也不得 restore 到回调入口以下。对恶意过度 restore 会在可检测时抛错，但原始 Canvas 已被弹出的外层矩阵/clip 无法凭空重建，因此不承诺恢复这种违反借用契约的调用。

没有实现 MC framebuffer 身份/尺寸管理、GL state scope、Blaze3D 缓存同步、原版物品/tooltip round-trip、模糊、Iris/Bliss 或 Screen/HUD 接线；这些均需独立平台桥和游戏验证。没有运行游戏、部署、提交或发布。

## 文件清单

生产源码均位于 `runtime/src/main/java/com/atom/lib/`：

- `AtomLibRuntime.java`、`RuntimeHandle.java`。
- `resource/ResourceScope.java`。
- `font/FontId.java`、`font/FontSource.java`、`font/FontRegistry.java`。
- `text/TextRequest.java`、`text/TextLayout.java`、`text/TextService.java`。
- `image/ImageFit.java`、`image/ImageStore.java`、`image/SvgStore.java`。
- `render/CustomPainter.java`、`render/RenderSession.java`。

测试位于 `runtime/src/test/java/com/atom/lib/runtime/`：

- `ResourceScopeTest.java`（5 项）。
- `FontIsolationTest.java`（3 项）。
- `TextLayoutTest.java`（7 项）。
- `ImageReloadTest.java`（4 项）。
- `RenderScopeTest.java`（2 项）。
- `RuntimeLoadingTest.java`（1 项），以及其独立 JVM helper `NativeLoadingProbe.java`。

另仅新增本文 `docs/runtime.md`。本轮没有修改 root/build.gradle、runtime/build.gradle 或其他模块。

## 实际验证

2026-10-29，Windows x64，JDK 17.0.18，Skija 0.143.18/types 0.2.0。首次以尚不存在的接口编译测试观察红灯；尾随换行丢失空光标行、孤立 surrogate 输入边界均有断言失败观察与修正；同时修正并覆盖了零保留字符省略的源偏移。本次最终实际执行：

```powershell
& 'C:/Program Files/Java/jdk-17.0.18/bin/java.exe' -classpath 'D:/AtomLib/gradle/wrapper/gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain :runtime:test '-PatomlibTestFont=D:/AtomChat-Main/platforms/1.20.1-forge/src/main/resources/assets/atomchat/font/bundled.otf' --console=plain
```

结果 **BUILD SUCCESSFUL：22 项，0 failures、0 errors、0 skipped**。最终执行中 runtime compileJava、compileTestJava、test 均实际执行；没有使用 --rerun-tasks 或全量重跑其他模块。root 的属性转发由主代理提供，库中不硬编码该字体路径。未传参数时字体测试明确失败，不用静默 skip 掩盖覆盖缺口。

测试包含真实 Skija native/CPU Surface：文字实际墨迹与 viewport 外零溢出像素、位图 contain/cover/圆角和 SVG 着色像素、转移的 Image/SVGDOM 在过期/驱逐/重载/宿主关闭后的 isClosed、调用者 Font 隔离与重载后有效性、失败重载保留原字体、资源清理并发与异常、跨 session 递归及 Canvas save 栈恢复。独立 JVM 证明加载 runtime 类不触发 native、open 才加载、close 不卸载 native。

JUnit XML：`build/standalone/_runtime/test-results/test/TEST-*.xml`。本轮未验证 Windows ARM64 实机、Linux、MC GL、加载器启动、双宿主游戏集成、完整字重/字体库覆盖、全部语言 shaping、资源包覆写或最终产物递归字体魔数扫描。Gallery 的中文行首标点现象按上述 grapheme-wrap 限制记录，不以其为完整文本品质验收通过。
