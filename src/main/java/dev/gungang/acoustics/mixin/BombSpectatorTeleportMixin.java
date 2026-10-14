package dev.gungang.acoustics.mixin;
import dev.gungang.acoustics.server.MatchManager;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BombSpectatorTeleportMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleTeleportToEntityPacket",at=@At("HEAD"),cancellable=true)
    private void gungang$noSpectatorTeleport(ServerboundTeleportToEntityPacket packet,CallbackInfo ci){
        // This handler also enters on the network thread; defer authority checks to the server thread.
        if(!player.server.isSameThread())return;
        var bomb=MatchManager.bomb();if(bomb!=null&&bomb.dead(player.getUUID()))ci.cancel();
    }
}
