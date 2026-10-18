package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.MapEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class MapSnowAccumulationMixin {
    @Redirect(method = "tickChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean gungang$preventMapSnow(Biome biome, LevelReader level, BlockPos pos) {
        return !MapEnvironment.preventSnowAccumulation(level) && biome.shouldSnow(level, pos);
    }
}
