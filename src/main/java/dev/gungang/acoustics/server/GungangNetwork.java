package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class GungangNetwork {
    private static boolean accepts(String version) {
        return version.equals("1") || version.equals(NetworkRegistry.ABSENT) || version.equals(NetworkRegistry.ACCEPTVANILLA);
    }
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GungangAcoustics.ID, "server"), () -> "1", GungangNetwork::accepts, GungangNetwork::accepts);
    public record Acoustics(String json) { }
    public record Lobby(String json) { }
    public record Equipment(String json) { }
    public record Match(String json) { }
    public record Action(String action, String value, String target) { }
    public static void register() {
        CHANNEL.registerMessage(0, Acoustics.class, (p, b) -> b.writeUtf(p.json, 65536), b -> new Acoustics(b.readUtf(65536)),
                (p, context) -> {
                    var ctx = context.get();
                    ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> dev.gungang.acoustics.client.LobbyClient.acoustics(p.json)));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, Match.class, (p,b) -> b.writeUtf(p.json,262144), b -> new Match(b.readUtf(262144)), (p,context) -> {
            var ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.gungang.acoustics.client.MatchHud.receive(p.json)));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Lobby.class, (p, b) -> b.writeUtf(p.json, 262144), b -> new Lobby(b.readUtf(262144)),
                (p, context) -> {
                    var ctx = context.get();
                    ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> dev.gungang.acoustics.client.LobbyClient.receive(p.json)));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Action.class, (p, b) -> { b.writeUtf(p.action, 16); b.writeUtf(p.value, 64); b.writeUtf(p.target, 36); },
                b -> new Action(b.readUtf(16), b.readUtf(64), b.readUtf(36)), (p, context) -> {
                    var ctx = context.get();
                    ctx.enqueueWork(() -> { if (ctx.getSender() != null) LobbyManager.action(ctx.getSender(), p); });
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, Equipment.class, (p, b) -> b.writeUtf(p.json, 524288), b -> new Equipment(b.readUtf(524288)),
                (p, context) -> {
                    var ctx = context.get();
                    ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> dev.gungang.acoustics.client.LobbyClient.equipment(p.json)));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static boolean installed(ServerPlayer player) { return CHANNEL.isRemotePresent(player.connection.connection); }
    public static void send(ServerPlayer player, Object packet) {
        if (installed(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    private GungangNetwork() { }
}
