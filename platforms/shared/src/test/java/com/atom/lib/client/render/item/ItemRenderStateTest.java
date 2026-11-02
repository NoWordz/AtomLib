package com.atom.lib.client.render.item;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import static org.junit.jupiter.api.Assertions.*;
class ItemRenderStateTest {
    static class Globals {private static final float[] shaderColor={1,2,3,4};private static Matrix4f projectionMatrix=new Matrix4f();private static Object shader=new Object();}
    static class Cache {private static int activeTexture=3;private static final State[] TEXTURES={new State()};static class State{private int binding=7;private boolean enabled=true;}}
    static class Shader {private static int lastProgramId=42;private static Shader lastAppliedShader=new Shader();}
    @Test void restoresPrivateCachesArraysReferencesAndMutableMatricesOnException(){
        var shader=Globals.shader;var matrix=Globals.projectionMatrix;var applied=Shader.lastAppliedShader;
        assertThrows(IllegalStateException.class,()->{try(var state=new ItemRenderState(Globals.class,Cache.class,Shader.class)){
            Globals.shaderColor[0]=9;Globals.projectionMatrix.translate(4,5,6);Globals.projectionMatrix=new Matrix4f();Globals.shader=new Object();Cache.activeTexture=8;Cache.TEXTURES[0].binding=91;Cache.TEXTURES[0].enabled=false;Shader.lastProgramId=19;Shader.lastAppliedShader=new Shader();throw new IllegalStateException("native pass");
        }});
        assertEquals(1,Globals.shaderColor[0]);assertSame(matrix,Globals.projectionMatrix);assertEquals(new Matrix4f(),matrix);assertSame(shader,Globals.shader);assertEquals(3,Cache.activeTexture);assertEquals(7,Cache.TEXTURES[0].binding);assertTrue(Cache.TEXTURES[0].enabled);assertEquals(42,Shader.lastProgramId);assertSame(applied,Shader.lastAppliedShader);
    }
}
