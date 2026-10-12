package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.DeployableEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MineOutlineMixin {
    @Inject(method="shouldEntityAppearGlowing",at=@At("HEAD"),cancellable=true)
    private void mineOutline(Entity entity,CallbackInfoReturnable<Boolean> callback) {
        if(entity instanceof DeployableEntity mine&&!mine.wall())
            callback.setReturnValue(mine.mineOutlineVisibleTo(Minecraft.getInstance().player));
    }
}
