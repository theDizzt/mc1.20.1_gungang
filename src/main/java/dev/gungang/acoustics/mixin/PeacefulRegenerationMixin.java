package dev.gungang.acoustics.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Peaceful healing lives in Player.aiStep, separately from FoodData and potions. */
@Mixin(Player.class)
public abstract class PeacefulRegenerationMixin {
    @Redirect(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void gungang$replacePeacefulRecovery(Player player,float amount){
        // HealthRegeneration is the sole passive recovery clock. Do not suppress
        // explicit heal calls from health packs, recovery items or objectives.
    }
}
