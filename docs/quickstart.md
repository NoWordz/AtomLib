# Quickstart: a screen in about ten minutes

This is the shortest path from an empty mod to a painted Skija screen. The
compiled reference is examples/a (design package) and examples/b (raw Skija);
this page is the wiring those examples assume.

## 1. Install the jar

Put one platform jar in mods/ next to your own mod:

| Minecraft | Loader | Jar |
|---|---|---|
| 1.20.1 | Forge | atomlib-1.20.1-forge-VERSION.jar |
| 1.21.1 | Fabric | atomlib-1.21.1-fabric-VERSION.jar |
| 1.21.1 | NeoForge | atomlib-1.21.1-neoforge-VERSION.jar |

Declare it in development against the same coordinate:

~~~groovy
repositories { maven { url = uri('/absolute/path/to/AtomLib/build/test-maven') } }
dependencies {
    // Fabric: modImplementation; Forge and NeoForge: implementation
    implementation 'com.atom.lib:atomlib-1.20.1-forge:0.1.0-beta'
}
~~~

Do not compile or bundle a second Skija or LWJGL Yoga into your own artifact.
AtomLib already provides both, including the Windows x64/ARM64 natives.

## 2. Open the runtime once

~~~java
RuntimeHandle runtime = AtomLibRuntime.open("myui");
FontRegistry fonts = new FontRegistry(runtime);
TextService text = new TextService(fonts);
YogaLayoutEngine layout = new YogaLayoutEngine();
~~~

AtomLib ships a default font: a subsetted Noto Sans SC (GB2312-complete hanzi,
kana, Latin-1 supplement, punctuation and kaomoji symbols; charset contract in
`tools/default-font-charset.txt`). Call `fonts.registerDefaults()` once and
text renders CJK out of the box. Host-supplied fonts keep priority: register
your own as the primary id, and the bundled faces join the fallback chain
behind whatever you set.

~~~java
FontId body = new FontId("myui.body");
fonts.register(body, FontSource.file(Path.of("C:/Windows/Fonts/msyh.ttc")));
// or: fonts.register(body, FontSource.bytes(myBundledTtfBytes));
fonts.registerDefaults(); // appended behind your own fallbacks
fonts.setFallbacks(List.of(/* ids that cover CJK and symbols */));
~~~

Emoji, Hangul words and GBK extensions are deliberately not in the default
package; hosts needing more coverage rerun the subsetting script
(`tools/subset_default_font.py`).

## 3. Lay the page out

Yoga only knows boxes; your data never reaches it.

~~~java
Viewport viewport = new Viewport(panelWidth, panelHeight, pixelRatio);
LayoutSnapshot measured = layout.layout(tree, viewport);
PresentationFrame frame = PresentationFrame.create(
        tree, measured, viewport, Map.of(), new PresentationFrame.Offset(0, 0));
~~~

PresentationFrame gives border, content and hit rectangles plus clipping for
each NodeId, in paint order.

## 4. Paint inside the borrowed frame

~~~java
MinecraftRenderBridge.get().drawFrame((canvas, borrowed) -> {
    for (NodeId id : frame.paintOrder()) {
        LayoutBox box = frame.box(id);
        // UiCards.drawCard(canvas, ...) or your own canvas.drawRect(...)
    }
    TextLayout line = text.layout(
            new TextRequest("Hello", body, 16F, 0xFF000000, 2, true),
            new Constraints(Constraints.Axis.atMost(boxWidth), Constraints.Axis.undefined()));
    text.draw(canvas, line, x, y);
});
~~~

Only Skija drawing belongs in that callback. Vanilla GuiGraphics work goes
through vanillaPass, and GL state the host needs back is restored when the
callback exits.

## 5. Wire input

`ScreenInputAdapter` turns screen callbacks into `InputDispatcher` calls, so
you forward one line per callback instead of mapping events yourself. It has
no Minecraft types and is identical on every loader.

~~~java
HitTester hit = (x, y) -> {
    for (NodeId id : frame.paintOrder()) {
        if (frame.box(id).hit().contains(x, y) && isInteractive(id)) return id;
    }
    return null;
};
InputDispatcher dispatcher = new InputDispatcher(hit);
dispatcher.setTargets(Map.of(buttonId, myButtonTarget));
ScreenInputAdapter input = new ScreenInputAdapter(dispatcher)
        .onScroll((x, y, dx, dy) -> { myScrollView.scrollBy(dx, dy); return true; });
~~~

Then forward from your Screen — one line per callback. Both mappings name the
callbacks the same; the only version difference is the scroll signature
(three arguments on 1.20.1, horizontal plus vertical on 1.21.1):

~~~java
// 1.20.1 Forge (Mojmap) — mouseScrolled has one delta; 1.21.1 Fabric/NeoForge
// has mouseScrolled(double x, double y, double horizontalAmount, double verticalAmount)
@Override public boolean mouseClicked(double x, double y, int button) {
    return input.press(x, y, button) || super.mouseClicked(x, y, button);
}
@Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    return input.drag(x, y, button, dx, dy) || super.mouseDragged(x, y, button, dx, dy);
}
@Override public boolean mouseReleased(double x, double y, int button) {
    return super.mouseReleased(x, y, button) || input.release(x, y, button);
}
@Override public boolean mouseScrolled(double x, double y, double delta) { // 1.20.1
    return input.scroll(x, y, 0, delta) || super.mouseScrolled(x, y, delta);
}
@Override public void removed() { // same name on Fabric (Yarn) and Forge (Mojmap)
    input.removed(); // ends the active gesture with a cancel, never a release
    super.removed();
}
~~~

Notes:

- Coordinates stay in Minecraft's GUI-scaled screen space, the same space
  your viewport and hit rectangles use; no conversion is needed. If your
  unit space differs, `input.scale(multiplier)` multiplies every coordinate
  before the dispatcher sees it (it returns the adapter, so keep the result).
- Scroll is not a `PointerEvent` type. The dispatcher never sees wheel
  events; your `onScroll` handler decides, and it can consult
  `dispatcher.captureOwner()` if it wants to respect an active drag.
- While a gesture is captured, the dispatcher owns drag and release for the
  captured node — it never re-runs the hit test, and a press that opened a
  modal or removed the target is cancelled rather than committed.
- The adapter's press/drag/release return true only when the dispatcher
  consumed the event; false falls through to `super` so vanilla widgets and
  keyboard focus handling keep working.

## 6. Lifecycle

Create the runtime, fonts, text service and Yoga engine on the render thread
and keep them for the session. Yoga is single-threaded: layout, paint and
close() must happen on that thread. Do not close the shared render bridge when
a screen closes; AtomLib releases it on client shutdown.

## Optional: the AtomChat look

Import com.atom.lib.design.atomchat for UiTokens, SkiaDraw, UiCards and
AppIcons. Without a host the tokens use AtomChat's shipped defaults; call
Design.host(DesignHost) to feed your own accent, corner radius, card tint and
panel colours. See [design-package.md](design-package.md).
