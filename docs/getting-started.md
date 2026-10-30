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
     var text = new TextService(fonts)) {
    var body = new FontId("host.body");
    fonts.register(body, FontSource.file(hostFontPath));
    var host = new HostContext(fonts, body, text, theme, feedback);
    // Create a UiView when your screen/frame begins and close it before the
    // screen's view-local state is discarded.
}
```

For Minecraft rendering, call `MinecraftRenderBridge.get()` from the render
thread. Flush pending `GuiGraphics`/`DrawContext` work before `drawFrame`, and
run vanilla content in `vanillaPass` between Skija passes. In CPU previews use
`RenderSession` and `Surface.makeRasterN32Premul` directly.

The three integration examples in `examples/a` and `examples/b` cover semantic
components, the optional shell, and raw Skija/Yoga. `devtools:preview` writes a
real component PNG using the same production `UiView` and components.

The common Java bytecode targets 17. Forge runs on 17; Fabric and NeoForge run
on 21. Hosts must keep Minecraft's existing LWJGL version and must not compile
or bundle a second Skija runtime into their own artifact.
