package dev.gungang.acoustics.mixin;
import dev.gungang.acoustics.server.MatchManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class BombSpectatorCameraMixin {
    @Inject(method="setCamera",at=@At("HEAD"),cancellable=true)
    private void gungang$teamCamera(Entity target,CallbackInfo ci){
        var bomb=MatchManager.bomb();
        if(bomb!=null&&!bomb.allowsCamera((ServerPlayer)(Object)this,target))ci.cancel();
    }
}
