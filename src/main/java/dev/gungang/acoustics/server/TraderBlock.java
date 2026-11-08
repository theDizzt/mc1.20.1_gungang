package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * No wandering traders (or their llamas) in the arenas: the server turns doTraderSpawning off at start, and a trader
 * the spawner still tries to place is cancelled. Spawn eggs and /summon keep working.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class TraderBlock {
    @SubscribeEvent public static void started(ServerStartedEvent event){
        var server=event.getServer();
        var rule=server.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING);
        if(rule.get())rule.set(false,server);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void spawn(MobSpawnEvent.FinalizeSpawn event){
        var mob=event.getEntity();
        if((mob instanceof WanderingTrader||mob instanceof TraderLlama)&&event.getSpawnType()!=MobSpawnType.SPAWN_EGG&&event.getSpawnType()!=MobSpawnType.COMMAND)
            event.setSpawnCancelled(true);
    }
    private TraderBlock(){}
}
