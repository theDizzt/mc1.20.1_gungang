package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.PropEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Particles a disguise spawns from its block code move with the drawn model, which sits off the block grid. */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class PropParticleMixin {
    @ModifyVariable(method="addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",at=@At("HEAD"),ordinal=0,argsOnly=true)
    private double gungang$propX(double x){return x+PropEffects.particleOffset().x;}
    @ModifyVariable(method="addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",at=@At("HEAD"),ordinal=1,argsOnly=true)
    private double gungang$propY(double y){return y+PropEffects.particleOffset().y;}
    @ModifyVariable(method="addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",at=@At("HEAD"),ordinal=2,argsOnly=true)
    private double gungang$propZ(double z){return z+PropEffects.particleOffset().z;}
}
