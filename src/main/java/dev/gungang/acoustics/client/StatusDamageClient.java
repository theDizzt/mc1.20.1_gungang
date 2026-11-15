package dev.gungang.acoustics.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Status damage still to come per entity id in the client's dimension (server {@code StatusForecast}). */
public final class StatusDamageClient {
    private static Map<Integer,Float> pending=Map.of(),heals=Map.of();
    private static Map<Integer,Float> statusEnds=Map.of(),healEnds=Map.of();
    private static Map<Integer,Integer> effects=Map.of();
    private static Object level;
    public static void receive(int[] ids,float[] amounts,float[] healing,float[] health,int[] masks){
        checkLevel();
        var next=new HashMap<Integer,Float>();var nextHeals=new HashMap<Integer,Float>();
        var nextStatusEnds=new HashMap<Integer,Float>();var nextHealEnds=new HashMap<Integer,Float>();
        for(int i=0;i<Math.min(ids.length,amounts.length);i++)if(amounts[i]>0)next.put(ids[i],amounts[i]);
        for(int i=0;i<Math.min(ids.length,healing.length);i++)if(healing[i]>0)nextHeals.put(ids[i],healing[i]);
        for(int i=0;i<Math.min(ids.length,health.length);i++){
            if(!Float.isFinite(health[i]))continue;
            if(next.containsKey(ids[i]))nextStatusEnds.put(ids[i],Math.max(0,health[i]-next.get(ids[i])));
            if(nextHeals.containsKey(ids[i]))nextHealEnds.put(ids[i],health[i]+nextHeals.get(ids[i]));
        }
        statusEnds=nextStatusEnds;healEnds=nextHealEnds;
        var nextEffects=new HashMap<Integer,Integer>();
        for(int i=0;i<Math.min(ids.length,masks.length);i++)if(masks[i]!=0)nextEffects.put(ids[i],masks[i]);
        effects=nextEffects;
        pending=next;heals=nextHeals;level=Minecraft.getInstance().level;
    }
    /** Wall-clock end (ms) of a heal pack / recovery potion heal per entity id; only these heals draw the green ghost. */
    private static final Map<Integer,Long> MARKS=new HashMap<>();
    public static void mark(int entity,int ticks){
        checkLevel();
        long now=net.minecraft.Util.getMillis();
        MARKS.values().removeIf(end->end<now);
        if(ticks<=0)MARKS.remove(entity); // the heal was cut short (death, reset)
        else MARKS.merge(entity,now+ticks*50L,Math::max);
    }
    /** Whether a heal pack or recovery potion heal is running (or just landed) on this entity. */
    public static boolean gadgetHealing(LivingEntity entity){
        checkLevel();
        if(entity.level()!=Minecraft.getInstance().level||!entity.isAlive())return false;
        Long end=MARKS.get(entity.getId());
        return end!=null&&end>=net.minecraft.Util.getMillis()||heals.getOrDefault(entity.getId(),0f)>0&&entity.level()==level;
    }
    /** Recovery potion health still to come (server {@code StatusForecast}), never past maximum health. */
    public static float pendingHeal(LivingEntity entity){
        if(entity.level()!=level||!entity.isAlive())return 0;
        return Math.max(0,Math.min(entity.getMaxHealth()-entity.getHealth(),heals.getOrDefault(entity.getId(),0f)));
    }
    /** Endpoints use health from the same server snapshot, never a separately arriving entity update. */
    static float statusEnd(LivingEntity entity){
        checkLevel();
        return entity.level()==level?statusEnds.getOrDefault(entity.getId(),entity.getHealth()-pending(entity)):entity.getHealth();
    }
    static float healEnd(LivingEntity entity){
        checkLevel();
        return entity.level()==level?Math.min(entity.getMaxHealth(),healEnds.getOrDefault(entity.getId(),entity.getHealth()+pendingHeal(entity))):entity.getHealth();
    }
    private static void checkLevel(){
        var current=Minecraft.getInstance().level;
        if(level!=current){pending=Map.of();heals=Map.of();statusEnds=Map.of();healEnds=Map.of();effects=Map.of();MARKS.clear();level=current;}
    }
    static int effects(LivingEntity entity){
        checkLevel();
        if(!entity.isAlive()||entity.level()!=level)return 0;
        int mask=effects.getOrDefault(entity.getId(),0);
        if(entity.isOnFire()&&!entity.fireImmune())mask|=dev.gungang.acoustics.server.StatusBarEffects.BURN;
        if(entity.hasEffect(net.minecraft.world.effect.MobEffects.POISON)||entity.hasEffect(dev.gungang.acoustics.server.GimmickBlocks.ACID_POISON.get()))mask|=dev.gungang.acoustics.server.StatusBarEffects.POISON;
        if(entity.hasEffect(dev.gungang.acoustics.server.GimmickBlocks.BLEEDING.get()))mask|=dev.gungang.acoustics.server.StatusBarEffects.BLEED;
        if(entity==Minecraft.getInstance().player){
            if(ArcShockClient.shocked()||UtilityHud.status("shock"))mask|=dev.gungang.acoustics.server.StatusBarEffects.SHOCK;
            if(UtilityHud.status("exposure"))mask|=dev.gungang.acoustics.server.StatusBarEffects.EXPOSURE;
        }
        return mask;
    }
    public static float pending(LivingEntity entity){
        if(entity.level()!=level||!entity.isAlive())return 0;
        return Math.min(entity.getHealth(),pending.getOrDefault(entity.getId(),0f));
    }
    private StatusDamageClient(){}
}
