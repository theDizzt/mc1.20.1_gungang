package dev.gungang.acoustics.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Status damage still to come per entity id in the client's dimension (server {@code StatusForecast}). */
public final class StatusDamageClient {
    private static Map<Integer,Float> pending=Map.of();
    private static Object level;
    public static void receive(int[] ids,float[] amounts){
        var next=new HashMap<Integer,Float>();
        for(int i=0;i<Math.min(ids.length,amounts.length);i++)next.put(ids[i],amounts[i]);
        pending=next;level=Minecraft.getInstance().level;
    }
    public static float pending(LivingEntity entity){
        if(entity.level()!=level||!entity.isAlive())return 0;
        return Math.min(entity.getHealth(),pending.getOrDefault(entity.getId(),0f));
    }
    private StatusDamageClient(){}
}
