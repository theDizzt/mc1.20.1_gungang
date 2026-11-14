package dev.gungang.acoustics.server;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Send impulses to the controlled player immediately, not only entity trackers. */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class PlayerImpulse {
    /** Players knocked back this tick whose motion still has to reach their own client. */
    private static final Set<ServerPlayer> KNOCKED=new LinkedHashSet<>();
    public static void apply(ServerPlayer player,Vec3 motion){player.setDeltaMovement(motion);sync(player);}
    public static void sync(ServerPlayer player){
        player.hasImpulse=true;player.hurtMarked=true;
        if(player.connection!=null)player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    /**
     * A knockback: every pellet of a shotgun blast knocks back, so the motion is sent once, with its final value. The
     * entity tracker sends it during the tick (hurtMarked); anything still marked at the end of the tick is sent here.
     */
    public static void knocked(ServerPlayer player){player.hasImpulse=true;player.hurtMarked=true;KNOCKED.add(player);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||KNOCKED.isEmpty())return;
        for(var player:KNOCKED)if(player.hurtMarked&&!player.isRemoved()&&player.connection!=null)player.connection.send(new ClientboundSetEntityMotionPacket(player));
        KNOCKED.clear();
    }
    private PlayerImpulse(){}
}
