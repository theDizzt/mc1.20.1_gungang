package dev.gungang.acoustics.server;

import java.util.*;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Out-of-combat recovery: item config health_regeneration (percentPerSecond of max health per second, given every intervalSeconds, after delaySeconds). */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class HealthRegeneration {
    private static int delay() {return ExtraGadgetConfig.ticks(ExtraGadgetConfig.regeneration.delaySeconds());}
    private static int period() {return Math.max(1,(int)Math.round(ExtraGadgetConfig.regeneration.intervalSeconds()*20));}
    /** The per-second share for one interval: 2 %/s every 10 ticks heals 1 % each time. */
    private static float amount(net.minecraft.world.entity.LivingEntity entity) {return (float)(entity.getMaxHealth()*ExtraGadgetConfig.regeneration.percentPerSecond()/100*period()/20);}
    private static RegenerationClock clock(UUID id,long now) {var clock=CLOCKS.computeIfAbsent(id,k->new RegenerationClock(now,delay()));clock.delay(delay());clock.period(period());return clock;}
    private static final Map<UUID,RegenerationClock> CLOCKS=new HashMap<>();
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingHurtEvent event) {
        if(!event.isCanceled()&&event.getAmount()>0&&event.getEntity() instanceof ServerPlayer p)
            clock(p.getUUID(),p.server.getTickCount()).damage(p.server.getTickCount());
        else if(!event.isCanceled()&&event.getAmount()>0&&event.getEntity() instanceof BotEntity bot&&bot.getServer()!=null)
            clock(bot.getUUID(),bot.getServer().getTickCount()).damage(bot.getServer().getTickCount());
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        long now=p.server.getTickCount();
        var clock=clock(p.getUUID(),now);
        if(!p.isAlive()||p.isSpectator()||p.isCreative()||LobbyManager.respawning(p.getUUID())||hider(p)) {clock.damage(now);return;}
        if(clock.tick(now,p.getHealth(),p.getMaxHealth())&&amount(p)>0)p.heal(amount(p));
    }
    /** Prop hunt hiders never recover on their own; only disguise healing (hot spring water, nozzle streams) heals them. */
    private static boolean hider(ServerPlayer p) {var hunt=MatchManager.propHunt();return hunt!=null&&hunt.hider(p.getUUID());}
    /** The bot recovers out of combat on the same clock as players. */
    @SubscribeEvent public static void bot(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if(!(event.getEntity() instanceof BotEntity bot)||bot.level().isClientSide||bot.getServer()==null||!BotManager.live(bot))return;
        long now=bot.getServer().getTickCount();
        var clock=clock(bot.getUUID(),now);
        if(clock.tick(now,bot.getHealth(),bot.getMaxHealth())&&amount(bot)>0)bot.heal(amount(bot));
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {CLOCKS.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {CLOCKS.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {CLOCKS.clear();}
    private HealthRegeneration() { }
}
