package com.atom.lib.client.render.item;
import com.atom.lib.AtomLibRuntime;
import com.atom.lib.client.render.*;
import com.atom.lib.geometry.Rect;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL33C.*;
class ItemIconEmptyPathRegressionTest extends HiddenGlFixture {
 @Test void emptyFramesPreserveIdentityLifecycleStateAndLaterNonemptyFrame() {
  var target=framebuffer(16,16);
  try(var runtime=AtomLibRuntime.open("empty-path-regression");var bridge=new SkijaRenderBridge(runtime);var atlas=new ItemIconAtlas(bridge)) {
   glBindFramebuffer(GL_FRAMEBUFFER,target.framebuffer());glViewport(2,3,8,9);glActiveTexture(GL_TEXTURE0+3);
   var noItems=atlas.prepare(List.of(),10,(t,w,h)->fail("empty pass"));
   assertEquals(target.framebuffer(),glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));assertEquals(GL_TEXTURE0+3,glGetInteger(GL_ACTIVE_TEXTURE));
   assertThrows(IllegalStateException.class,()->atlas.prepare(List.of(),11,(t,w,h)->{}));noItems.close();
   assertThrows(IllegalArgumentException.class,()->atlas.prepare(List.of(),10,(t,w,h)->{}));
   var empty=atlas.prepare(List.of(new ItemIconAtlas.Entry("e",16,true)),11,(t,w,h)->fail("all empty pass"));
   var icon=empty.icon("e");bridge.draw(target,c->{c.clear(0xff00ff00);icon.paint(c,new Rect(0,0,16,16),1);});
   assertArrayEquals(new int[]{0,255,0,255},pixel(target,8,8));empty.close();
   assertThrows(IllegalStateException.class,()->icon.paint(null,new Rect(0,0,1,1),1));
   try(var f=atlas.prepare(List.of(new ItemIconAtlas.Entry("a",16,false)),12,(t,w,h)->{glClearColor(1,0,0,1);glClear(GL_COLOR_BUFFER_BIT);})){bridge.draw(target,c->f.icon("a").paint(c,new Rect(0,0,16,16),1));assertArrayEquals(new int[]{255,0,0,255},pixel(target,8,8));}
   assertEquals(target.framebuffer(),glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
  }
 }
 @Test void emptyBatchRestoresHostTextureUboAndPixelTransfer() {
  int texture=glGenTextures(), pbo=glGenBuffers(), ubo=glGenBuffers();
  try(var runtime=AtomLibRuntime.open("empty-state");var bridge=new SkijaRenderBridge(runtime);var atlas=new ItemIconAtlas(bridge)) {
   glActiveTexture(GL_TEXTURE0+3);glBindTexture(GL_TEXTURE_2D,texture);
   glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pbo);glBufferData(GL_PIXEL_UNPACK_BUFFER,1024,GL_STREAM_DRAW);glPixelStorei(GL_UNPACK_ROW_LENGTH,31);
   glBindBuffer(GL_UNIFORM_BUFFER,ubo);glBufferData(GL_UNIFORM_BUFFER,1024,GL_DYNAMIC_DRAW);glBindBufferBase(GL_UNIFORM_BUFFER,2,ubo);
   try(var f=atlas.prepare(List.of(new ItemIconAtlas.Entry("e",16,true)),1,(t,w,h)->fail("empty pass"))) {
    assertEquals(GL_TEXTURE0+3,glGetInteger(GL_ACTIVE_TEXTURE));assertEquals(texture,glGetInteger(GL_TEXTURE_BINDING_2D));
    assertEquals(pbo,glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));assertEquals(31,glGetInteger(GL_UNPACK_ROW_LENGTH));assertEquals(ubo,glGetIntegeri(GL_UNIFORM_BUFFER_BINDING,2));
   }
   assertEquals(texture,glGetInteger(GL_TEXTURE_BINDING_2D));assertEquals(pbo,glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));assertEquals(ubo,glGetIntegeri(GL_UNIFORM_BUFFER_BINDING,2));
  } finally {glBindBufferBase(GL_UNIFORM_BUFFER,2,0);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);glPixelStorei(GL_UNPACK_ROW_LENGTH,0);glBindTexture(GL_TEXTURE_2D,0);glDeleteBuffers(pbo);glDeleteBuffers(ubo);glDeleteTextures(texture);}
 }
}
