package com.atom.lib.client.render.item;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.DiffuseLighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.VertexSorter;
import org.joml.Matrix4f;
import com.atom.lib.client.render.SkijaRenderBridge;
import static org.lwjgl.opengl.GL33C.*;

/** Native GUI pass; caller owns and binds the offscreen color+depth target. */
final class VanillaItemPass implements AutoCloseable {
    private final DrawContext graphics;
    private final ItemRenderState state;
    private final int height;
    static void flushHost(){RenderSystem.assertOnRenderThread();var mc=MinecraftClient.getInstance();mc.getBufferBuilders().getEntityVertexConsumers().draw();}
    VanillaItemPass(int width,int height){
        RenderSystem.assertOnRenderThread();SkijaRenderBridge.assertOutsideSkijaPass();this.height=height;
        var mc=MinecraftClient.getInstance();
        var buffers=mc.getBufferBuilders().getEntityVertexConsumers();
        state=new ItemRenderState(RenderSystem.class,GlStateManager.class,ShaderProgram.class);
        var matrices=RenderSystem.getModelViewStack();matrices.pushMatrix();
        try{
            matrices.identity();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,width,height,0,1000,21000),VertexSorter.BY_Z);
            matrices.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();
            RenderSystem.resetTextureMatrix();RenderSystem.disableColorLogicOp();RenderSystem.disablePolygonOffset();
            glDisable(GL_RASTERIZER_DISCARD);glDisable(GL_STENCIL_TEST);glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE);glDisable(GL_SAMPLE_COVERAGE);glPolygonMode(GL_FRONT_AND_BACK,GL_FILL);glDepthRange(0,1);
            for(int i=0;i<glGetInteger(GL_MAX_CLIP_DISTANCES);i++)glDisable(GL_CLIP_DISTANCE0+i);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.colorMask(true,true,true,true);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableScissor();DiffuseLighting.enableGuiDepthLighting();
            graphics=new DrawContext(mc,buffers);
        }catch(RuntimeException|Error e){matrices.popMatrix();state.close();throw e;}
    }
    void draw(ItemStack stack,int x,int y,int pixelSize){
        RenderSystem.enableScissor(x,height-y-pixelSize,pixelSize,pixelSize);
        var matrices=graphics.getMatrices();matrices.push();
        try{matrices.translate(x,y,0);matrices.scale(pixelSize/16f,pixelSize/16f,1);graphics.drawItem(stack,0,0);graphics.draw();}
        finally{matrices.pop();}
    }
    public void close(){try{graphics.draw();}finally{RenderSystem.getModelViewStack().popMatrix();state.close();}}
}
