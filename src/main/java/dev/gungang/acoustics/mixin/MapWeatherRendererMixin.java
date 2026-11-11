package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.WeatherPassthrough;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Rain/snow streaks, splashes and rain sounds pass through barriers and death zone blocks (WeatherPassthrough). */
@Mixin(LevelRenderer.class)
public abstract class MapWeatherRendererMixin {
    @Redirect(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int gungang$weatherHeight(Level level, Heightmap.Types type, int x, int z) {
        return WeatherPassthrough.height(level, type, x, z, level.getHeight(type, x, z), level.getMinBuildHeight());
    }

    @Redirect(method = "tickRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos gungang$splashHeight(LevelReader level, Heightmap.Types type, BlockPos pos) {
        return WeatherPassthrough.pos(level, type, level.getHeightmapPos(type, pos), level.getMinBuildHeight());
    }
}
