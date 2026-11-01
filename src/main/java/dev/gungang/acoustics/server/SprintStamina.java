package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hunger is not a mechanic here: food is pinned at {@link #FOOD} for every player, enough to sprint (vanilla forbids
 * it at 6 or less) but below the 18 vanilla needs for hunger healing, with no saturation. FoodRegenerationMixin also
 * blocks hunger healing; passive recovery is HealthRegeneration's alone.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class SprintStamina {
    public static final int FOOD=17;
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||event.player.level().isClientSide)return;
        var food=event.player.getFoodData();
        if(food.getFoodLevel()!=FOOD)food.setFoodLevel(FOOD);
        if(food.getSaturationLevel()!=0)food.setSaturation(0);
    }
    private SprintStamina() { }
}
