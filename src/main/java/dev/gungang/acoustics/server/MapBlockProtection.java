package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Map blocks never break. Only GunGang's own breakable blocks ({@link GimmickBlock}: cracked walls, glass and planks,
 * duds, gas barrels, nozzles ...) take damage, through their own hit logic. Bullets and the compound bow are kept
 * from breaking blocks by their mixins; this covers the rest:
 * <ul>
 * <li>explosions from any source (TaCZ explosive ammo, lrtactical grenades, vanilla) blow up no blocks, whatever
 * their gun pack or config says; gimmicks in the list stay so they still get their blast hit;</li>
 * <li>fire burns no blocks away ({@code FireBurnMixin});</li>
 * <li>only creative players break blocks by hand, and farmland is not trampled.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class MapBlockProtection {
    /** Last, so every other listener (gimmick blasts, spawn gates, fallout) still sees the explosion. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void explosion(ExplosionEvent.Detonate event){
        var level=event.getLevel();
        event.getAffectedBlocks().removeIf(pos->!(level.getBlockState(pos).getBlock() instanceof GimmickBlock));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breaking(BlockEvent.BreakEvent event){
        var player=event.getPlayer();
        if(player==null||!player.isCreative())event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void trample(BlockEvent.FarmlandTrampleEvent event){event.setCanceled(true);}
    private MapBlockProtection(){}
}
