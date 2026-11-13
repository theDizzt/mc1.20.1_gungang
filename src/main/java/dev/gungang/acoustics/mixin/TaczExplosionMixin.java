package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.ElementalDamage;
import dev.gungang.acoustics.server.TaczExplosionDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets="com.tacz.guns.util.block.ProjectileExplosion",remap=false,priority=2000)
public abstract class TaczExplosionMixin {
    @Shadow @Final private Level level;

    // Snapshot before TaCZ Tweaks (priority 1000) wraps hurt with its player-only modifier.
    // Keep the original explosion loop, visibility, protection events and other mods' wrappers.
    // @ModifyArg, not @ModifyArgs: the latter needs a runtime-generated Args class that Forge's module class loader
    // cannot always load, which broke TaCZ explosions mid-way (no damage, effects or explosion packet).
    @ModifyArg(method={"explode()V","m_46061_()V"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",remap=true),index=1,remap=false,require=1)
    private float gungang$uniformDamage(DamageSource source,float amount){
        if(level.isClientSide)return amount;
        amount=(float)(amount*ElementalDamage.explosionMultiplier(source.getDirectEntity()));
        ((TaczExplosionDamage)source).gungang$explosionHit(amount);
        return amount;
    }
}
