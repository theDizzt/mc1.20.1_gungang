package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hunger is not a mechanic here (hunger healing is disabled and the shooter HUD hides the bar), but vanilla still
 * forbids sprinting at 6 food or less. Auto-sprint drains it over a long life, so food stays full.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class SprintStamina {
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||event.player.level().isClientSide||event.player.tickCount%20!=0)return;
        var food=event.player.getFoodData();
        if(food.getFoodLevel()<20)food.setFoodLevel(20);
    }
    private SprintStamina() { }
}
