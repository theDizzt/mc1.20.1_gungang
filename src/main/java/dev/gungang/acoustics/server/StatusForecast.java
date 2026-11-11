package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
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
    private record Snapshot(List<Integer> ids,List<Float> amounts,List<Float> heals){}
    private static final Map<UUID,Snapshot> SENT=new HashMap<>();
    private static final Map<ServerLevel,Set<UUID>> BOTS=new WeakHashMap<>();

    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(event.getEntity() instanceof BotEntity&&event.getLevel() instanceof ServerLevel level){
            var tracked=BOTS.get(level);
            if(tracked!=null)tracked.add(event.getEntity().getUUID());
        }
    }
    @SubscribeEvent public static void left(net.minecraftforge.event.entity.EntityLeaveLevelEvent event){
        if(event.getEntity() instanceof BotEntity&&event.getLevel() instanceof ServerLevel level){
            var tracked=BOTS.get(level);
            if(tracked!=null)tracked.remove(event.getEntity().getUUID());
        }
    }
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.level.LevelEvent.Unload event){
        if(event.getLevel() instanceof ServerLevel level)BOTS.remove(level);
    }
    private static void addBots(ServerLevel level,List<LivingEntity> bodies){
        // Include all loaded bots, even ones outside BotManager's active match roster.
        // Bootstrap once per level; UUIDs avoid retaining entities or their worlds.
        var tracked=BOTS.computeIfAbsent(level,l->{
            var ids=new LinkedHashSet<UUID>();
            for(var entity:l.getAllEntities())if(entity instanceof BotEntity)ids.add(entity.getUUID());
            return ids;
        });
        for(var iterator=tracked.iterator();iterator.hasNext();){
            var entity=level.getEntity(iterator.next());
            if(entity instanceof BotEntity bot&&!bot.isRemoved())bodies.add(bot);
            else iterator.remove();
        }
    }

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
            addBots(level,bodies);
            for(var body:bodies){
                float amount=Math.round(pending(body)*2)/2f,heal=Math.round(pendingHeal(body)*2)/2f;
                if(amount>0||heal>0){ids.add(body.getId());amounts.add(amount);heals.add(heal);}
            }
            var snapshot=new Snapshot(List.copyOf(ids),List.copyOf(amounts),List.copyOf(heals));
            GungangNetwork.StatusDamage packet=null;
            for(var player:level.players()){
                if(!GungangNetwork.installed(player)||snapshot.equals(SENT.get(player.getUUID())))continue;
                // The encoder only reads these arrays: construct once for all recipients in this dimension.
                if(packet==null)packet=new GungangNetwork.StatusDamage(ids.stream().mapToInt(Integer::intValue).toArray(),toArray(amounts),toArray(heals));
                GungangNetwork.send(player,packet);
                SENT.put(player.getUUID(),snapshot);
            }
        }
    }
    private static float[] toArray(List<Float> values){var out=new float[values.size()];for(int i=0;i<out.length;i++)out[i]=values.get(i);return out;}
    @SubscribeEvent public static void changed(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event){SENT.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){SENT.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){SENT.clear();BOTS.clear();}
    private StatusForecast(){}
}
