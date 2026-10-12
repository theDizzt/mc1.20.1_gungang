package dev.gungang.acoustics.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class PreviewCache {
    public record MapImage(ResourceLocation texture,int width,int height,String error) { }
    private static final Map<String,MapImage> MAPS=new LinkedHashMap<>();
    private static final Map<String,ItemStack> WEAPONS=new HashMap<>();
    private static final Map<String,Long> REQUESTS=new HashMap<>();
    private static void request(String key,String action,String id,String group){
        long now=System.nanoTime();if(now-REQUESTS.getOrDefault(key,0L)<1_000_000_000L)return;
        REQUESTS.put(key,now);LobbyClient.action(action,id,group);
    }
    public static MapImage image(LobbySnapshot.MapView map){
        if(map.previewImage()==null||map.previewImage().isBlank())return new MapImage(null,0,0,"등록된 미리보기가 없습니다");
        if(!MAPS.containsKey(map.id()))request("map:"+map.id(),"mapPreview",map.id(),"");
        return MAPS.getOrDefault(map.id(),new MapImage(null,0,0,"전장 이미지 불러오는 중…"));
    }
    public static ItemStack item(String id,boolean primary){
        String group=primary?"primary":"secondary",key=group+":"+id;
        if(!WEAPONS.containsKey(key))request(key,"weaponPreview",id,group);
        return WEAPONS.get(key);
    }
    public static void weapon(GungangNetwork.WeaponPreview packet){if(WEAPONS.size()<128||WEAPONS.containsKey(packet.id()))WEAPONS.put(packet.id(),packet.stack());}
    public static void map(GungangNetwork.MapPreview packet){
        var mc=Minecraft.getInstance();MapImage value;
        try {
            if(packet.png().length==0)value=new MapImage(null,0,0,packet.error());
            else {
                MapPreviewFiles.dimensions(packet.png());
                NativeImage image=NativeImage.read(new java.io.ByteArrayInputStream(packet.png()));
                int w=image.getWidth(),h=image.getHeight();
                var texture=mc.getTextureManager().register("gungang_map_preview",new DynamicTexture(image));
                value=new MapImage(texture,w,h,"");
            }
        }catch(Exception failure){value=new MapImage(null,0,0,"이미지를 표시할 수 없습니다");}
        var previous=MAPS.put(packet.id(),value);if(previous!=null&&previous.texture()!=null)mc.getTextureManager().release(previous.texture());
        while(MAPS.size()>8){String oldest=MAPS.keySet().iterator().next();var old=MAPS.remove(oldest);if(old.texture()!=null)mc.getTextureManager().release(old.texture());}
    }
    public static void clear(){
        for(var value:MAPS.values())if(value.texture()!=null)Minecraft.getInstance().getTextureManager().release(value.texture());
        MAPS.clear();WEAPONS.clear();REQUESTS.clear();
    }
}
