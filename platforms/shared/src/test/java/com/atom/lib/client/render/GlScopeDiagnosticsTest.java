// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;

final class GlScopeDiagnosticsTest extends HiddenGlFixture {
    @TempDir Path directory;

    @jdk.jfr.Name("com.atomchat.PanelSegments")
    @jdk.jfr.StackTrace(false)
    static final class PanelEvent extends jdk.jfr.Event {
        long frameId=1;
        String surface="hidden-gl-fixture";
        long startedAtNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS) long panelNanos;
        boolean success=true;
    }

    @Test void writesRealJfrForOfflinePanelPairing() throws Exception {
        Path root=Path.of(System.getProperty("atomlib.backdropEvidence",directory.toString()));
        java.nio.file.Files.createDirectories(root);
        try(Recording recording=new Recording()) {
            recording.enable("com.atomlib.GlStateCapture");
            recording.enable("com.atomchat.PanelSegments");recording.start();
            try(GlStateScope before=GlStateScope.capture()) { }
            PanelEvent panel=new PanelEvent();panel.startedAtNanos=System.nanoTime();
            try(GlStateScope outer=GlStateScope.capture()) {
                try(GlStateScope inner=GlStateScope.capture()) { }
            }
            panel.panelNanos=System.nanoTime()-panel.startedAtNanos;panel.commit();
            try(GlStateScope after=GlStateScope.capture()) { }
            recording.stop();recording.dump(root.resolve("scope-pairing.jfr"));
        }
    }

    @Test void disabledRecordingDoesNotEmitCaptureEvents() throws Exception {
        Path file=directory.resolve("disabled.jfr");
        try(Recording recording=new Recording()) {
            recording.start();
            try(GlStateScope scope=GlStateScope.capture()) { }
            recording.stop();recording.dump(file);
        }
        assertTrue(RecordingFile.readAllEvents(file).stream()
            .noneMatch(e->e.getEventType().getName().equals("com.atomlib.GlStateCapture")));
    }

    @Test void failedConstructionIsRecordedWithoutChangingScopeDepth() throws Exception {
        Path file=directory.resolve("failed.jfr");
        long window=org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        var capabilities=org.lwjgl.opengl.GL.getCapabilities();
        try(Recording recording=new Recording()) {
            recording.enable("com.atomlib.GlStateCapture");recording.start();
            org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(0);
            org.lwjgl.opengl.GL.setCapabilities(null);
            try {
                assertThrows(IllegalStateException.class,GlStateScope::capture);
            } finally {
                org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(window);
                org.lwjgl.opengl.GL.setCapabilities(capabilities);
            }
            try(GlStateScope scope=GlStateScope.capture()) { }
            recording.stop();recording.dump(file);
        }
        var events=RecordingFile.readAllEvents(file).stream()
            .filter(e->e.getEventType().getName().equals("com.atomlib.GlStateCapture"))
            .sorted(java.util.Comparator.comparingLong(e->e.getLong("startedAtNanos"))).toList();
        assertEquals(2,events.size());
        assertFalse(events.get(0).getBoolean("success"));
        assertTrue(events.get(1).getBoolean("success"));
        assertEquals(0,events.get(1).getInt("depth"));
    }

    @Test void recordsEveryCaptureIncludingNestedAndExceptionalBodies() throws Exception {
        Path file=directory.resolve("scopes.jfr");
        try(Recording recording=new Recording()) {
            recording.enable("com.atomlib.GlStateCapture").withThreshold(java.time.Duration.ZERO);
            recording.start();
            glViewport(1,2,30,31);
            try(GlStateScope outer=GlStateScope.capture()) {
                glViewport(3,4,20,21);
                assertThrows(IllegalArgumentException.class,()->{
                    try(GlStateScope inner=GlStateScope.capture()) {
                        glViewport(5,6,10,11);
                        throw new IllegalArgumentException("painter");
                    }
                });
                int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);
                assertArrayEquals(new int[]{3,4,20,21},viewport);
            }
            try(GlStateScope sibling=GlStateScope.capture()) { }
            int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);
            assertArrayEquals(new int[]{1,2,30,31},viewport);
            recording.stop();recording.dump(file);
        }
        var events=RecordingFile.readAllEvents(file).stream()
            .filter(e->e.getEventType().getName().equals("com.atomlib.GlStateCapture"))
            .sorted(java.util.Comparator.comparingLong(e->e.getLong("startedAtNanos"))).toList();
        assertEquals(3,events.size(),"all calls, rather than outer scopes only");
        assertEquals(java.util.List.of(0,1,0),events.stream().map(e->e.getInt("depth")).toList());
        for(var event:events) {
            assertTrue(event.getBoolean("success"));
            assertTrue(event.getLong("captureNanos")>0);
            assertEquals(glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),event.getInt("textureUnits"));
            assertEquals(10,event.getInt("textureTargets"));
            assertEquals(Thread.currentThread().getId(),event.getThread().getJavaThreadId());
        }
        assertEquals(GL_NO_ERROR,glGetError());
    }
}
