package com.atom.lib.client.render.item;

import java.lang.reflect.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Restores Java-side vanilla state/cache values in addition to the caller's GlStateScope.
 * Field names in RenderSystem are stable (@DontObfuscate); cache internals are traversed by type.
 * No GL calls here: restoration of actual bindings belongs to GlStateScope.
 */
public final class ItemRenderState implements AutoCloseable {
    private static final Set<String> MATRIX_FIELDS=Set.of("projectionMatrix","modelViewMatrix","textureMatrix","vertexSorting");
    private static final ClassValue<Field[]> FIELDS=new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> type){return type.getDeclaredFields();}
    };
    private static Field[] fields(Class<?> type){return FIELDS.get(type);}
    private final List<Runnable> restore=new ArrayList<>();
    private boolean closed;
    public ItemRenderState(Class<?> renderSystem,Class<?> glStateManager,Class<?> shader){
        var visited=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        try{
            for(var f:fields(renderSystem))if(Modifier.isStatic(f.getModifiers())&&(f.getName().startsWith("shader")||MATRIX_FIELDS.contains(f.getName())))snapshot(f,null,false,glStateManager,visited);
            for(var f:fields(glStateManager))if(Modifier.isStatic(f.getModifiers()))snapshot(f,null,true,glStateManager,visited);
            for(var f:fields(shader))if(Modifier.isStatic(f.getModifiers())&&!Modifier.isFinal(f.getModifiers())&&(f.getType().isPrimitive()||f.getType()==shader))snapshot(f,null,false,glStateManager,visited);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot snapshot vanilla item render state",e);}
    }
    private void snapshot(Field f,Object owner,boolean cache,Class<?> cacheRoot,Set<Object> visited)throws ReflectiveOperationException{
        boolean constant=Modifier.isFinal(f.getModifiers());
        if(constant&&f.getType().isPrimitive())return;
        if(!f.canAccess(owner)&&!f.trySetAccessible())throw new IllegalStateException("Inaccessible vanilla render state: "+f);
        Object value=f.get(owner);
        if(!constant)restore.add(()->set(f,owner,value));
        if(value instanceof Matrix4f matrix){var copy=new Matrix4f(matrix);restore.add(()->matrix.set(copy));}
        else if(value instanceof Vector3f vector){var copy=new Vector3f(vector);restore.add(()->vector.set(copy));}
        else if(value!=null&&value.getClass().isArray()){
            int n=Array.getLength(value);Object copy=Array.newInstance(value.getClass().getComponentType(),n);System.arraycopy(value,0,copy,0,n);
            restore.add(()->System.arraycopy(copy,0,value,0,n));
            if(cache&&!value.getClass().getComponentType().isPrimitive())for(int i=0;i<n;i++)nested(Array.get(value,i),cacheRoot,visited);
        }else if(cache)nested(value,cacheRoot,visited);
    }
    private void nested(Object value,Class<?> root,Set<Object> visited)throws ReflectiveOperationException{
        if(value==null||!visited.add(value)||value.getClass().getEnclosingClass()!=root)return;
        for(var f:fields(value.getClass()))if(!Modifier.isStatic(f.getModifiers()))snapshot(f,value,true,root,visited);
    }
    private static void set(Field f,Object owner,Object value){try{f.set(owner,value);}catch(IllegalAccessException e){throw new IllegalStateException("Cannot restore vanilla render state: "+f,e);}}
    public void close(){if(closed)return;closed=true;Throwable error=null;for(int i=restore.size()-1;i>=0;i--)try{restore.get(i).run();}catch(RuntimeException|Error e){if(error==null)error=e;else error.addSuppressed(e);}if(error instanceof RuntimeException e)throw e;if(error instanceof Error e)throw e;}
}
