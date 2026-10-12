package dev.gungang.acoustics.server;

import java.util.*;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class HealthRegeneration {
    private static final Map<UUID,RegenerationClock> CLOCKS=new HashMap<>();
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event) {
        if(!event.isCanceled()&&event.getAmount()>0&&event.getEntity() instanceof ServerPlayer p)
            CLOCKS.computeIfAbsent(p.getUUID(),id->new RegenerationClock(p.server.getTickCount())).damage(p.server.getTickCount());
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        long now=p.server.getTickCount();
        var clock=CLOCKS.computeIfAbsent(p.getUUID(),id->new RegenerationClock(now));
        if(!p.isAlive()||p.isSpectator()||p.isCreative()||LobbyManager.respawning(p.getUUID())) {clock.damage(now);return;}
        if(clock.tick(now,p.getHealth()<p.getMaxHealth()))p.heal(1);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {CLOCKS.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {CLOCKS.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {CLOCKS.clear();}
    private HealthRegeneration() { }
}
