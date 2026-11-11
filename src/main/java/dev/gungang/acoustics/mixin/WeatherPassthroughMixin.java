package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.WeatherPassthrough;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Rain reaches whatever is under barriers and death zone blocks (wet entities, put-out fire ...), on both sides. */
@Mixin(Level.class)
public abstract class WeatherPassthroughMixin {
    @Redirect(method = "isRainingAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos gungang$rainHeight(Level level, Heightmap.Types type, BlockPos pos) {
        // Only blocks above pos matter: the caller checks whether the surface is higher than pos.
        return WeatherPassthrough.pos(level, type, level.getHeightmapPos(type, pos), pos.getY());
    }
}
