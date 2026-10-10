package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.GunSoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.sound.GunSoundInstance", remap = false)
public abstract class GunSoundSourceMixin implements GunSoundSource {
    @Unique private Entity gungang$source;
    @Unique private ResourceLocation gungang$id;
    @Inject(method = "<init>(Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFLnet/minecraft/world/entity/Entity;ILnet/minecraft/resources/ResourceLocation;ZZ)V",
            at = @At("RETURN"), require = 0)
    private void gungang$capture(SoundEvent sound, SoundSource category, float volume, float pitch,
                                  Entity entity, int distance, ResourceLocation id, boolean mono, boolean relative, CallbackInfo ci) {
        gungang$source = entity;
        gungang$id = id;
    }
    public Entity gungang$shooter() { return gungang$source; }
    public ResourceLocation gungang$soundId() { return gungang$id; }
}
