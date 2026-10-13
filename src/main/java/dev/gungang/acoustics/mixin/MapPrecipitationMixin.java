package dev.gungang.acoustics.mixin;
import dev.gungang.acoustics.client.MapEnvironmentClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Biome.class)
public abstract class MapPrecipitationMixin {
    @Inject(method="hasPrecipitation",at=@At("RETURN"),cancellable=true)
    private void gungang$wet(CallbackInfoReturnable<Boolean> ci){
        if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())return;
        String weather=MapEnvironmentClient.weather();if(weather.equals("rain")||weather.equals("snow"))ci.setReturnValue(true);
    }
    @Inject(method="getPrecipitationAt",at=@At("RETURN"),cancellable=true)
    private void gungang$weather(BlockPos pos,CallbackInfoReturnable<Biome.Precipitation> ci){
        if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())return;
        switch(MapEnvironmentClient.weather()){case "snow"->ci.setReturnValue(Biome.Precipitation.SNOW);case "rain"->ci.setReturnValue(Biome.Precipitation.RAIN);}
    }
}
