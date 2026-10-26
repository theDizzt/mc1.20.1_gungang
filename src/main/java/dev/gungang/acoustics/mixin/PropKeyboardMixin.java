package dev.gungang.acoustics.mixin;
import dev.gungang.acoustics.client.PropHuntClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.KeyboardHandler.class)
public abstract class PropKeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void gungang$propKey(long window,int key,int scan,int action,int modifiers,CallbackInfo ci){
        if(window==net.minecraft.client.Minecraft.getInstance().getWindow().getWindow()&&(dev.gungang.acoustics.client.LobbyClient.lobbyKey(key,scan,action)||PropHuntClient.key(key,scan,action)||dev.gungang.acoustics.client.TurretClient.key(key,scan,action)||dev.gungang.acoustics.client.BombWorldHud.key(key,scan,action)))ci.cancel();
    }
}
