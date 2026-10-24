package dev.gungang.acoustics.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-provided bot skins, one texture per file: skin (bots may share one); a skin that has not arrived falls back to Steve. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class BotSkinCache {
    private static final Map<String,ResourceLocation> TEXTURES=new HashMap<>();
    public static void receive(GungangNetwork.BotSkin packet){
        if(!packet.skin().startsWith(BotSkinFiles.PREFIX))return;
        release(packet.skin());
        if(packet.png().length==0||TEXTURES.size()>=BotManager.MAX*2)return;
        try{
            int[] size=MapPreviewFiles.dimensions(packet.png());
            if(size[0]!=64||size[1]!=64)throw new java.io.IOException("Bot skin must be 64x64");
            NativeImage image=NativeImage.read(new java.io.ByteArrayInputStream(packet.png()));
            TEXTURES.put(packet.skin(),Minecraft.getInstance().getTextureManager().register("gungang_bot_skin",new DynamicTexture(image)));
        }catch(Exception failure){LogUtils.getLogger().warn("[GunGang] Could not load bot skin {}",packet.skin(),failure);}
    }
    static ResourceLocation texture(String value){return value==null?null:TEXTURES.get(value);}
    private static void release(String skin){
        var old=TEXTURES.remove(skin);
        if(old!=null)Minecraft.getInstance().getTextureManager().release(old);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){
        for(var texture:TEXTURES.values())Minecraft.getInstance().getTextureManager().release(texture);
        TEXTURES.clear();
    }
    private BotSkinCache(){}
}
