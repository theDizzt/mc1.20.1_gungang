package dev.gungang.acoustics.client;

import com.google.gson.reflect.TypeToken;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.ServerFiles;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Levels of online players for the Tab lists; empty on servers without GunGang, which leaves vanilla Tab untouched. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class PlayerLevels {
    private static Map<String,Integer> levels=Map.of();
    public static void receive(String json) {
        Map<String,Integer> parsed=ServerFiles.GSON.fromJson(json,new TypeToken<Map<String,Integer>>(){}.getType());
        levels=parsed==null?Map.of():Map.copyOf(parsed);
    }
    public static boolean active() {return !levels.isEmpty();}
    public static Integer level(Object id) {return id==null?null:levels.get(id.toString());}
    /** Emblem in a size x size cell; rows without a level (bots) keep the gap so names stay aligned. */
    static void draw(GuiGraphics g,Object id,int x,int y,int size) {
        Integer level=level(id);if(level!=null)LevelEmblem.draw(g,level,x,y,size);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {levels=Map.of();}
    private PlayerLevels() { }
}
