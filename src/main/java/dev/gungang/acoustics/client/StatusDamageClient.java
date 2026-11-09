package dev.gungang.acoustics.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Status damage still to come per entity id in the client's dimension (server {@code StatusForecast}). */
public final class StatusDamageClient {
    private static Map<Integer,Float> pending=Map.of(),heals=Map.of();
    private static Object level;
    public static void receive(int[] ids,float[] amounts,float[] healing){
        var next=new HashMap<Integer,Float>();var nextHeals=new HashMap<Integer,Float>();
        for(int i=0;i<Math.min(ids.length,amounts.length);i++)if(amounts[i]>0)next.put(ids[i],amounts[i]);
        for(int i=0;i<Math.min(ids.length,healing.length);i++)if(healing[i]>0)nextHeals.put(ids[i],healing[i]);
        pending=next;heals=nextHeals;level=Minecraft.getInstance().level;
    }
    /** Wall-clock end (ms) of a heal pack / recovery potion heal per entity id; only these heals draw the green ghost. */
    private static final Map<Integer,Long> MARKS=new HashMap<>();
    public static void mark(int entity,int ticks){
        long now=net.minecraft.Util.getMillis();
        MARKS.values().removeIf(end->end<now);
        if(ticks<=0)MARKS.remove(entity); // the heal was cut short (death, reset)
        else MARKS.merge(entity,now+ticks*50L,Math::max);
    }
    /** Whether a heal pack or recovery potion heal is running (or just landed) on this entity. */
    public static boolean gadgetHealing(LivingEntity entity){
        if(entity.level()!=Minecraft.getInstance().level||!entity.isAlive())return false;
        Long end=MARKS.get(entity.getId());
        return end!=null&&end>=net.minecraft.Util.getMillis()||heals.getOrDefault(entity.getId(),0f)>0&&entity.level()==level;
    }
    /** Recovery potion health still to come (server {@code StatusForecast}), never past maximum health. */
    public static float pendingHeal(LivingEntity entity){
        if(entity.level()!=level||!entity.isAlive())return 0;
        return Math.max(0,Math.min(entity.getMaxHealth()-entity.getHealth(),heals.getOrDefault(entity.getId(),0f)));
    }
    public static float pending(LivingEntity entity){
        if(entity.level()!=level||!entity.isAlive())return 0;
        return Math.min(entity.getHealth(),pending.getOrDefault(entity.getId(),0f));
    }
    private StatusDamageClient(){}
}
