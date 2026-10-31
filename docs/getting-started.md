# Getting started

Add the AtomLib platform JAR to the host's `mods/` folder, then declare its
published coordinate in development. Maven publication is not configured in
this repository; `build/test-maven` is the explicit local input used by tests.

```groovy
repositories { maven { url = uri('/absolute/path/to/AtomLib/build/test-maven') }
             mavenCentral() }
dependencies {
    // Fabric: modImplementation; Forge/NeoForge: implementation
    implementation 'com.atom.lib:atomlib-1.20.1-forge:0.1.0-local'
}
```

Open the runtime only from client code. Register a font supplied by the host;
AtomLib does not include one.

```java
try (var runtime = AtomLibRuntime.open("example");
     var fonts = new FontRegistry(runtime);
     var text = new TextService(fonts);
     var layout = new YogaLayoutEngine()) {
    var body = new FontId("host.body");
    fonts.register(body, FontSource.file(hostFontPath));

    LayoutTree tree = buildHostTree(text, body);
    var frame = PresentationFrame.create(
        tree, layout.layout(tree, viewport), viewport,
        Map.of(), new PresentationFrame.Offset(0, 0));
}
```

`YogaLayoutEngine` is single-threaded: create, lay out, paint and close it on
the same thread. `LayoutTree` uses stable `NodeId` identities; changing a
node's children or measurement callback updates only the affected native
subtree. `PresentationFrame` is immutable and supplies border, content and hit
rectangles plus clipping for painting and hit testing.

For Minecraft rendering, call `MinecraftRenderBridge.get()` from the render
thread. Flush pending `GuiGraphics`/`DrawContext` work before `drawFrame`, and
run vanilla content in `vanillaPass` between Skija passes. In CPU previews use
`RenderSession` and `Surface.makeRasterN32Premul` directly.

The two integration examples in `examples/a` and `examples/b` show the two
supported paths. Example A uses the optional design package (`UiTokens`,
`SkiaDraw`, `UiCards`). Example B builds a Yoga layout and paints it with a raw
Skija `Canvas`. There is no component or shell layer to install or configure.

The common Java bytecode targets 17. Forge runs on 17; Fabric and NeoForge run
on 21. Hosts must keep Minecraft's existing LWJGL version and must not compile
or bundle a second Skija runtime into their own artifact.
