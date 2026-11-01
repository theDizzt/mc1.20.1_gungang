package dev.gungang.acoustics.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Peaceful healing lives in Player.aiStep, separately from FoodData and potions. */
@Mixin(Player.class)
public abstract class PeacefulRegenerationMixin {
    // Mohist/CraftBukkit rewrites the peaceful heal to heal(F,RegainReason), so hook the
    // naturalRegeneration check guarding it instead; both vanilla and Mohist keep that call.
    // HealthRegeneration is the sole passive recovery clock, and food is pinned elsewhere,
    // so skipping the whole peaceful block is safe. Explicit heal calls are untouched.
    @Redirect(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z",ordinal=0))
    private boolean gungang$replacePeacefulRecovery(GameRules rules,GameRules.Key<GameRules.BooleanValue> key){
        return false;
    }
}
