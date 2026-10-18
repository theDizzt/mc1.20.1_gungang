package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.MapEnvironmentClient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public abstract class MapWeatherRendererMixin {
    @Redirect(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int gungang$weatherHeight(Level level, Heightmap.Types type, int x, int z) {
        int height = level.getHeight(type, x, z);
        String weather = MapEnvironmentClient.weather();
        if (!weather.equals("rain") && !weather.equals("snow")) return height;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, height - 1, z);
        // Only rescan columns whose normal precipitation surface is a barrier.
        if (!level.getBlockState(pos).is(Blocks.BARRIER)) return height;
        for (int y = height - 1; y >= level.getMinBuildHeight(); --y) {
            pos.setY(y);
            var state = level.getBlockState(pos);
            if (!state.is(Blocks.BARRIER) && type.isOpaque().test(state)) return y + 1;
        }
        return level.getMinBuildHeight();
    }
}
