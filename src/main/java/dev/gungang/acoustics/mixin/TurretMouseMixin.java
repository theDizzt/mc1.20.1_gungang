package dev.gungang.acoustics.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.MouseHandler.class)
public abstract class TurretMouseMixin {
    @Inject(method="onPress",at=@At("HEAD"),cancellable=true)
    private void gungang$service(long window,int button,int action,int modifiers,CallbackInfo ci){
        if(window==net.minecraft.client.Minecraft.getInstance().getWindow().getWindow()&&(dev.gungang.acoustics.client.LobbyClient.lobbyMouse(button,action)||dev.gungang.acoustics.client.TurretClient.mouse(button,action)))ci.cancel();
    }
}
