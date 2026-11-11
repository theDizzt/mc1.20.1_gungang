package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The damage a body's running status ailments will still deal if nothing stops them: bleeding, burning, wither,
 * poison (never below 1 health) and acid poisoning, at the per-tick damage GunGang applies to each. Health bars show
 * this share of the remaining health in purple. Players and bots are sent to every player in the same dimension.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class StatusForecast {
    static final int PERIOD=4;
    private static final Map<UUID,Integer> SENT=new HashMap<>();

    public static float pending(LivingEntity body){
        if(!body.isAlive())return 0;
        double total=Bleeding.pending(body);
        int fire=body.getRemainingFireTicks();
        if(fire>0&&!body.fireImmune()){
            double per=AdrenalineDamage.burnDamage(body);
            if(per<0)per=ElementalDamage.markDamage(body,ElementalDamage.Element.BURN);
            if(per<0)per=1;
            total+=(fire+19)/20*per;
        }
        var wither=body.getEffect(MobEffects.WITHER);
        if(wither!=null){
            double per=ElementalDamage.markDamage(body,ElementalDamage.Element.WITHER);
            total+=wither.getDuration()/Math.max(1,40>>wither.getAmplifier())*(per<0?1:per);
        }
        var poison=body.getEffect(MobEffects.POISON);
        if(poison!=null){
            double per=ElementalDamage.markDamage(body,ElementalDamage.Element.POISON);
            total+=Math.min(poison.getDuration()/Math.max(1,25>>poison.getAmplifier())*(per<0?1:per),Math.max(0,body.getHealth()-1));
        }
        var acid=body.getEffect(GimmickBlocks.ACID_POISON.get());
        if(acid!=null)total+=acid.getDuration()/AcidPoisonEffect.interval(acid.getAmplifier())*8.0;
        return (float)Math.min(total,body.getHealth());
    }

    /** Recovery potion health still to come, capped at what the body is missing (health bars draw it green). */
    static float pendingHeal(LivingEntity body){
        if(!body.isAlive())return 0;
        float total;
        if(body instanceof BotEntity){var agent=BotManager.agent(body);total=agent==null?0:agent.gadgets.pendingHeal(body.level().getGameTime());}
        else total=GadgetManager.pendingHeal(body.getUUID());
        return Math.max(0,Math.min(total,body.getMaxHealth()-body.getHealth()));
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.getServer().getTickCount()%PERIOD!=0)return;
        for(ServerLevel level:event.getServer().getAllLevels()){
            if(level.players().isEmpty())continue;
            var ids=new ArrayList<Integer>();var amounts=new ArrayList<Float>();var heals=new ArrayList<Float>();
            var bodies=new ArrayList<LivingEntity>(level.players());
            for(var entity:level.getAllEntities())if(entity instanceof BotEntity bot)bodies.add(bot);
            for(var body:bodies){
                float amount=Math.round(pending(body)*2)/2f,heal=Math.round(pendingHeal(body)*2)/2f;
                if(amount>0||heal>0){ids.add(body.getId());amounts.add(amount);heals.add(heal);}
            }
            int hash=Objects.hash(ids,amounts,heals);
            for(var player:level.players()){
                Integer last=SENT.get(player.getUUID());
                if(last!=null&&last==hash)continue;
                SENT.put(player.getUUID(),hash);
                if(GungangNetwork.installed(player))GungangNetwork.send(player,new GungangNetwork.StatusDamage(ids.stream().mapToInt(Integer::intValue).toArray(),toArray(amounts),toArray(heals)));
            }
        }
    }
    private static float[] toArray(List<Float> values){var out=new float[values.size()];for(int i=0;i<out.length;i++)out[i]=values.get(i);return out;}
    @SubscribeEvent public static void changed(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event){SENT.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){SENT.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){SENT.clear();}
    private StatusForecast(){}
}
