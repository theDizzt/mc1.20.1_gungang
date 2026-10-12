package dev.gungang.acoustics.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(FoodData.class)
public abstract class FoodRegenerationMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;isHurt()Z"))
    private boolean customRegeneration(Player player) {
        // Disable only hunger-based healing; potions and other direct healing still work.
        return player.level().isClientSide&&player.isHurt();
    }
}
