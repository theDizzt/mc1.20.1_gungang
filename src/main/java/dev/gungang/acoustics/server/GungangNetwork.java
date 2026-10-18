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
        return version.equals("2") || version.equals(NetworkRegistry.ABSENT) || version.equals(NetworkRegistry.ACCEPTVANILLA);
    }
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GungangAcoustics.ID, "server"), () -> "2", GungangNetwork::accepts, GungangNetwork::accepts);
    public record Acoustics(String json) { }
    public record Lobby(String json) { }
    public record Equipment(String json) { }
    public record Match(String json) { }
    public record Progression(String json) { }
    public record SafeZoneConfig(String json) { }
    public record Utility(String json) { }
    public record Scan(String json) { }
    public record Ability(SurvivalGadgets.View state) { }
    public record MapPreview(String id,byte[] png,String error) { }
    public record WeaponPreview(String id,net.minecraft.world.item.ItemStack stack) { }
    public record Gadget(String preview,double range) {
        public Gadget(String preview) {this(preview,preview.equals("orbital_strike")?ExtraGadgetConfig.orbital.range():preview.equals("barrier")?GadgetConfig.barrier.range():GadgetConfig.mine.range());}
    }
    public record Action(String action, String value, String target) { }
    public record Hud(boolean enabled) { }
    public record PropHunt(String json) {}
    public static void register() {
        CHANNEL.registerMessage(14,PropHunt.class,(p,b)->b.writeUtf(p.json,262144),b->new PropHunt(b.readUtf(262144)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.PropHuntClient.receive(p.json)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(13,Hud.class,(p,b)->b.writeBoolean(p.enabled),b->new Hud(b.readBoolean()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.HudVisibility.receive(p.enabled)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(12,Ability.class,(p,b)->{b.writeUUID(p.state.player());b.writeLong(p.state.lifeEnd());b.writeLong(p.state.jetEnd());b.writeInt(p.state.color());},
                b->new Ability(new SurvivalGadgets.View(b.readUUID(),b.readLong(),b.readLong(),b.readInt())),(p,context)->{
                    var ctx=context.get();ctx.enqueueWork(()->SurvivalGadgets.receive(p.state));ctx.setPacketHandled(true);
                },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(10,Utility.class,(p,b)->b.writeUtf(p.json,8192),b->new Utility(b.readUtf(8192)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.UtilityHud.receive(p.json)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(11,Scan.class,(p,b)->b.writeUtf(p.json,262144),b->new Scan(b.readUtf(262144)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.ScannerVision.receive(p.json)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(9,SafeZoneConfig.class,(p,b)->b.writeUtf(p.json,262144),b->new SafeZoneConfig(b.readUtf(262144)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->SafeZones.receive(p.json));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(7,MapPreview.class,(p,b)->{b.writeUtf(p.id,48);b.writeByteArray(p.png);b.writeUtf(p.error,256);},
                b->new MapPreview(b.readUtf(48),b.readByteArray(MapPreviewFiles.MAX_BYTES),b.readUtf(256)),(p,context)->{
                    var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.PreviewCache.map(p)));ctx.setPacketHandled(true);
                },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8,WeaponPreview.class,(p,b)->{b.writeUtf(p.id,64);b.writeItem(p.stack);},
                b->new WeaponPreview(b.readUtf(64),b.readItem()),(p,context)->{
                    var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.PreviewCache.weapon(p)));ctx.setPacketHandled(true);
                },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(6,Progression.class,(p,b)->b.writeUtf(p.json,4096),b->new Progression(b.readUtf(4096)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.ProgressionHud.receive(p.json)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5,Gadget.class,(p,b)->{b.writeUtf(p.preview,32);b.writeDouble(p.range);},b->new Gadget(b.readUtf(32),b.readDouble()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.GadgetClient.receive(p.preview,p.range)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
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
    public static boolean installed(ServerPlayer player) { return player.connection!=null&&player.connection.connection.channel()!=null&&CHANNEL.isRemotePresent(player.connection.connection); }
    public static void send(ServerPlayer player, Object packet) {
        if (installed(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    private GungangNetwork() { }
}
