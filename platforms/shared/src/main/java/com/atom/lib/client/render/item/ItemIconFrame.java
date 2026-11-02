package com.atom.lib.client.render.item;
import com.atom.lib.controls.ControlIcon;
import com.atom.lib.geometry.Rect;
import io.github.humbleui.skija.*;
import java.util.*;
/** One synchronous frame of borrowed icons. Close after the bridge's drawFrame returns. */
public final class ItemIconFrame implements AutoCloseable {
    final ItemIconAtlas owner; final long generation; final Image image;
    private final Map<String,ItemIconAtlas.Tile> tiles;
    boolean closed;
    ItemIconFrame(ItemIconAtlas owner,long generation,Image image,List<ItemIconAtlas.Tile> tiles){this.owner=owner;this.generation=generation;this.image=image;var map=new LinkedHashMap<String,ItemIconAtlas.Tile>();for(var tile:tiles)map.put(tile.key(),tile);this.tiles=Map.copyOf(map);}
    public ControlIcon icon(String key){check();var tile=tiles.get(key);if(tile==null)throw new IllegalArgumentException("Unknown item icon key: "+key);
        return (canvas,bounds,opacity)->{check();if(tile.empty())return;if(!Float.isFinite(opacity)||opacity<0||opacity>1)throw new IllegalArgumentException("Invalid icon opacity");
            try(var paint=new Paint().setAlphaf(opacity)){canvas.drawImageRect(image,io.github.humbleui.types.Rect.makeXYWH(tile.x(),tile.y(),tile.size(),tile.size()),io.github.humbleui.types.Rect.makeXYWH(bounds.x(),bounds.y(),bounds.width(),bounds.height()),paint,true);}
        };
    }
    private void check(){owner.checkHandle();if(closed||generation!=owner.generation())throw new IllegalStateException("Item icon frame is expired");}
    public void close(){if(closed)return;owner.release(this);}
}
