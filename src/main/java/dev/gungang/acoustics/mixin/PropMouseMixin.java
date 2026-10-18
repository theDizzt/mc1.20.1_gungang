package dev.gungang.acoustics.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.world.entity.Entity.class)
public abstract class PropMouseMixin {
    @Inject(method="turn",at=@At("HEAD"),cancellable=true)
    private void gungang$lockLook(double yaw,double pitch,CallbackInfo ci){
        if((Object)this==net.minecraft.client.Minecraft.getInstance().player&&dev.gungang.acoustics.client.PropHuntClient.lockLook())ci.cancel();
    }
}
