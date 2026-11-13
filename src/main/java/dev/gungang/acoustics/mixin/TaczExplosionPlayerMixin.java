package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.TaczExplosionDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class TaczExplosionPlayerMixin {
    // Restore the shared explosion value if TaCZ Tweaks changed it for players only.
    // Player.hurt still performs all normal immunity, shield, armor and Forge event checks.
    @ModifyVariable(method="hurt",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float gungang$uniformExplosion(float amount,DamageSource source,float original){
        return ((TaczExplosionDamage)source).gungang$explosionAmount(amount);
    }
}
