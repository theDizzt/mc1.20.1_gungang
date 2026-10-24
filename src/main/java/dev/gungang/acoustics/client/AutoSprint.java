package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Moving forward sprints without the sprint key. TaCZ lowers the gun while sprinting, so any fire or aim input
 * drops the sprint first; vanilla still ends it on collision, sneaking, items in use or released forward input.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class AutoSprint {
    private static List<KeyMapping> taczKeys;
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||event.getEntity()!=player||!AcousticConfig.AUTO_SPRINT.get())return;
        if(firing(mc)) {if(player.isSprinting())player.setSprinting(false);return;}
        var input=event.getInput();
        if(player.isSprinting()||input.forwardImpulse<.8f||input.shiftKeyDown||UtilityHud.rooted()||player.isUsingItem()||player.isPassenger()
                ||player.isFallFlying()||player.horizontalCollision||player.hasEffect(MobEffects.BLINDNESS)
                ||player.isInWater()&&!player.isUnderWater()
                ||player.getFoodData().getFoodLevel()<=6&&!player.getAbilities().mayfly)return;
        player.setSprinting(true);
    }
    private static boolean firing(Minecraft mc) {
        if(mc.options.keyAttack.isDown()||mc.options.keyUse.isDown()||OffhandMacro.ownsGunInput())return true;
        if(taczKeys==null)taczKeys=java.util.Arrays.stream(mc.options.keyMappings)
                .filter(k->k.getName().startsWith("key.tacz.")&&(k.getName().contains("shoot")||k.getName().contains("aim"))).toList();
        for(var key:taczKeys)if(key.isDown())return true;
        return false;
    }
    private AutoSprint() { }
}
