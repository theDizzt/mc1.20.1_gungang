package dev.gungang.acoustics.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

/** Send impulses to the controlled player immediately, not only entity trackers. */
public final class PlayerImpulse {
    public static void apply(ServerPlayer player,Vec3 motion){player.setDeltaMovement(motion);sync(player);}
    public static void sync(ServerPlayer player){
        player.hasImpulse=true;player.hurtMarked=true;
        if(player.connection!=null)player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    private PlayerImpulse(){}
}
