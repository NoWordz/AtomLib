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
    implementation 'com.atom.lib:atomlib-1.20.1-forge:0.1.0-local'
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

AtomLib ships no font. Register bytes you are allowed to ship, or a system
file:

~~~java
FontId body = new FontId("myui.body");
fonts.register(body, FontSource.file(Path.of("C:/Windows/Fonts/msyh.ttc")));
// or: fonts.register(body, FontSource.bytes(myBundledTtfBytes));
fonts.setFallbacks(List.of(/* ids that cover CJK and symbols */));
~~~

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

## 5. Route input

InputDispatcher needs one thing from you: which node is under the pointer.

~~~java
HitTester hit = (x, y) -> {
    for (NodeId id : frame.paintOrder()) {
        if (frame.box(id).hit().contains(x, y) && isInteractive(id)) return id;
    }
    return null;
};
InputDispatcher input = new InputDispatcher(hit);
input.setTargets(Map.of(buttonId, myButtonTarget));

// from Screen#mouseClicked / mouseReleased / mouseDragged, already GUI-scaled
input.dispatch(new PointerEvent(PointerEvent.Type.PRESS, mouseX, mouseY, button));
~~~

Minecraft gives screen callbacks coordinates in GUI-scaled space, the same
space your viewport uses, so no extra scale conversion is needed. Work out the
screen's pixelsPerUnit once per frame from the window size and GUI scale.

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
