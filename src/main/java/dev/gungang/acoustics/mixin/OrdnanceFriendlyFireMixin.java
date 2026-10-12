package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.OrdnanceDamage;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class OrdnanceFriendlyFireMixin {
    @Inject(method="canHarmPlayer",at=@At("HEAD"),cancellable=true)
    private void areaDamage(Player other,CallbackInfoReturnable<Boolean> ci) {
        if(OrdnanceDamage.active())ci.setReturnValue(true);
    }
}
