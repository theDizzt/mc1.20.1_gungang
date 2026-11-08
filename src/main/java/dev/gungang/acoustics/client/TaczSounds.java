package dev.gungang.acoustics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Plays a TaCZ gun-pack sound (tacz_sounds folder) through TaCZ's own sound player, e.g. its default kill sound. */
public final class TaczSounds {
    private static java.lang.reflect.Method play;
    private static boolean unavailable;
    public static void play(String sound){
        var mc=Minecraft.getInstance();
        if(mc.player==null||unavailable)return;
        var id=ResourceLocation.tryParse(sound);if(id==null)return;
        try{
            if(play==null)play=Class.forName("com.tacz.guns.client.sound.SoundPlayManager")
                    .getMethod("playClientSound",net.minecraft.world.entity.Entity.class,ResourceLocation.class,float.class,float.class,int.class);
            play.invoke(null,mc.player,id,1f,1f,16);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError missing){
            unavailable=true;
            com.mojang.logging.LogUtils.getLogger().warn("GunGang: TaCZ sound player unavailable",missing);
        }
    }
    private TaczSounds(){}
}
