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
        return version.equals("5") || version.equals(NetworkRegistry.ABSENT) || version.equals(NetworkRegistry.ACCEPTVANILLA);
    }
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GungangAcoustics.ID, "server"), () -> "5", GungangNetwork::accepts, GungangNetwork::accepts);
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
    public record Gadget(String preview,double range,java.util.List<ArenaMap.Area> spawnAreas,boolean flagExists) {
        public Gadget(String preview,double range){this(preview,range,java.util.List.of(),false);}
        public Gadget(String preview,ServerPlayer player){this(preview,new Gadget(preview).range(),preview.equals("command_flag")?LobbyManager.placementSpawnAreas(player.level()):java.util.List.of(),preview.equals("command_flag")&&CommandFlags.hasFlag(player));}
        public Gadget(String preview) {this(preview,preview.equals("command_flag")?ExtraGadgetConfig.commandFlag.placementRange():preview.equals("turret")?Turrets.config.placementRange():preview.equals("orbital_strike")?ExtraGadgetConfig.orbital.range():preview.equals("perimeter_barrage")?ExtraGadgetConfig.barrage.range():preview.equals("barrier")?GadgetConfig.barrier.range():GadgetConfig.mine.range());}
    }
    public record Action(String action, String value, String target) { }
    /** 178: a hider's lectern book text (up to PropHuntMatch.LECTERN_LIMIT characters). */
    public record PropText(String text) { }
    /** 241: a painted canvas for the placed easel at pos (256 palette hex digits). */
    public record EaselPaint(net.minecraft.core.BlockPos pos,String pixels) { }
    public record Hud(boolean enabled) { }
    public record PropHunt(String json) {}
    /** 랜덤 무기: the viewer's weapon choice and the time to the next change ({@link WeaponRotation.View}). */
    public record WeaponRoll(String json) {}
    public record Emblems() { }
    public record BotSkin(String skin,byte[] png) { }
    public record Levels(String json) { }
    public record Nickname(boolean required,String error) { }
    public record SetNickname(String value) { }
    public record TeamPointRequest() { }
    public record TeamPointView(String json) { }
    public record NozzleBurst(net.minecraft.core.BlockPos pos,boolean acid,double height,int count) { }
    public record ThermalBurst(net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 end,boolean scald) { }
    public record ConveyorView(net.minecraft.core.BlockPos pos,int speed) { }
    public record ConveyorSet(net.minecraft.core.BlockPos pos,int speed,boolean reverse) { }
    public record SecretBlockSync(boolean full,long[] positions,byte[] modes) { }
    public record RapierStep(int entity,float yaw,float distance) { }
    /** Every online player's chosen effect colours ({@link EffectColors}), as JSON {uuid: {key: [first, second]}}. */
    public record EffectColorSync(String json) { }
    /** A 74 Hermes' Boots dash: the client replays the fixed-step motion and draws the owner's glow trail. */
    public record HermesDash(int entity,float yaw,float pitch,float distance,int ticks,float trailSeconds) { }
    /** One LAS-5 shot: shooter entity id (-1: none), bullet start and the server-side hit point. */
    public record Las5Shot(int owner,double fx,double fy,double fz,double tx,double ty,double tz) { }
    public record M95Shot(int owner,double fx,double fy,double fz,double tx,double ty,double tz) { }
    public record M95EchoPacket(java.util.List<M95Echo.Target> targets) { }
    public record M95Observed(java.util.List<java.util.UUID> targets) { }
    /** A TaCZ gun-pack sound for the receiving player only (e.g. tacz:kill for a twin-blades kill). */
    public record ShockLook(int ticks,float yaw,float pitch) { }
    public record KillSound(String sound) { }
    /** Pending status-ailment damage per entity id in the receiver's dimension (purple share of health bars). */
    public record StatusDamage(int[] ids,float[] amounts,float[] heals) { }
    /** A heal pack or recovery potion is healing this entity for the next {@code ticks}: its health bar draws the green ghost. */
    public record HealMark(int entity,int ticks) { }
    /** Health damage the receiving player just dealt (one hit), for the combo counter beside the health bar. */
    public record ComboHit(float amount) { }
    public record AssaultMotion(boolean active,double x,double y,double z) { }
    public record AnchorPull(java.util.UUID anchor,boolean active,double x,double y,double z) { }
    public record Emote(int entity,int slot,boolean prop) { }
    public record PropList() { }
    /** 247+: the live weapon patch screen (server view as JSON) and its requests ({@code open}/{@code apply}). */
    public record WeaponPatchView(String json) { }
    public record WeaponPatchRequest(String action, String json) { }
    /** Longest lobby action value: the game options JSON, with room for every mode's section and a long excluded-block list. */
    static final int ACTION_VALUE=8192;
    public static void register() {
        // 36: opera used 34 for this, which Nickname already holds here.
        CHANNEL.registerMessage(36,AnchorPull.class,(p,b)->{b.writeUUID(p.anchor);b.writeBoolean(p.active);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);},
                b->new AnchorPull(b.readUUID(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.VoidAnchorClient.receive(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(34,Nickname.class,(p,b)->{b.writeBoolean(p.required);b.writeUtf(p.error,256);},b->new Nickname(b.readBoolean(),b.readUtf(256)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.NicknameScreen.receive(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(35,SetNickname.class,(p,b)->b.writeUtf(p.value,64),b->new SetNickname(b.readUtf(64)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->ProgressionService.nickname(ctx.getSender(),p.value));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(33,AssaultMotion.class,(p,b)->{b.writeBoolean(p.active);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);},
                b->new AssaultMotion(b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.AirAssaultClient.receive(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(32,WeaponRoll.class,(p,b)->b.writeUtf(p.json,262144),b->new WeaponRoll(b.readUtf(262144)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.WeaponRollClient.receive(p.json)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(29,WeaponPatchView.class,(p,b)->b.writeUtf(p.json,262144),b->new WeaponPatchView(b.readUtf(262144)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.WeaponPatchScreen.receive(p.json)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(30,WeaponPatchRequest.class,(p,b)->{b.writeUtf(p.action,16);b.writeUtf(p.json,32768);},b->new WeaponPatchRequest(b.readUtf(16),b.readUtf(32768)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->WeaponPatch.receive(ctx.getSender(),p.action,p.json));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(28,PropList.class,(p,b)->{},b->new PropList(),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->dev.gungang.acoustics.client.PropListScreen::open));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(27,Emote.class,(p,b)->{b.writeVarInt(p.entity);b.writeByte(p.slot);b.writeBoolean(p.prop);},b->new Emote(b.readVarInt(),b.readByte(),b.readBoolean()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.EmoteClient.receive(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(37,Las5Shot.class,(p,b)->{b.writeVarInt(p.owner+1);b.writeDouble(p.fx);b.writeDouble(p.fy);b.writeDouble(p.fz);b.writeDouble(p.tx);b.writeDouble(p.ty);b.writeDouble(p.tz);},
                b->new Las5Shot(b.readVarInt()-1,b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.Las5Beam.receive(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(39,StatusDamage.class,(p,b)->{b.writeVarIntArray(p.ids);b.writeVarInt(p.amounts.length);for(float f:p.amounts)b.writeFloat(f);b.writeVarInt(p.heals.length);for(float f:p.heals)b.writeFloat(f);},
                b->{var ids=b.readVarIntArray(256);int n=Math.min(256,b.readVarInt());var amounts=new float[n];for(int i=0;i<n;i++)amounts[i]=b.readFloat();
                    int h=Math.min(256,b.readVarInt());var heals=new float[h];for(int i=0;i<h;i++)heals[i]=b.readFloat();return new StatusDamage(ids,amounts,heals);},(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.StatusDamageClient.receive(p.ids(),p.amounts(),p.heals())));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(42,M95Shot.class,(p,b)->{b.writeVarInt(p.owner+1);b.writeDouble(p.fx);b.writeDouble(p.fy);b.writeDouble(p.fz);b.writeDouble(p.tx);b.writeDouble(p.ty);b.writeDouble(p.tz);},
                b->new M95Shot(b.readVarInt()-1,b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.Las5Beam.receive(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(45,EffectColorSync.class,(p,b)->b.writeUtf(p.json,262144),b->new EffectColorSync(b.readUtf(262144)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->EffectColors.receive(p.json())));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(47,ShockLook.class,(p,b)->{b.writeVarInt(p.ticks);b.writeFloat(p.yaw);b.writeFloat(p.pitch);},
                b->new ShockLook(b.readVarInt(),b.readFloat(),b.readFloat()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.ArcShockClient.receive(p.ticks,p.yaw,p.pitch)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(46,HermesDash.class,(p,b)->{b.writeVarInt(p.entity);b.writeFloat(p.yaw);b.writeFloat(p.pitch);b.writeFloat(p.distance);b.writeVarInt(p.ticks);b.writeFloat(p.trailSeconds);},
                b->new HermesDash(b.readVarInt(),b.readFloat(),b.readFloat(),b.readFloat(),b.readVarInt(),b.readFloat()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.HermesTrail.receive(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(44,M95Observed.class,(p,b)->b.writeCollection(p.targets,(buf,id)->buf.writeUUID(id)),b->{
            int size=b.readVarInt();if(size<0||size>512)throw new IllegalArgumentException("Too many observed targets");
            var ids=new java.util.ArrayList<java.util.UUID>(size);for(int i=0;i<size;i++)ids.add(b.readUUID());return new M95Observed(ids);
        },(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->M95Echo.observed(ctx.getSender(),p.targets));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(43,M95EchoPacket.class,(p,b)->b.writeCollection(p.targets,(buf,t)->{buf.writeDouble(t.x());buf.writeDouble(t.y());buf.writeDouble(t.z());buf.writeUUID(t.id());}),
                b->new M95EchoPacket(b.readList(buf->new M95Echo.Target(buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readUUID()))),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.M95EchoHud.receive(p.targets)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(41,ComboHit.class,(p,b)->b.writeFloat(p.amount),b->new ComboHit(b.readFloat()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.ComboHud.hit(p.amount())));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(40,HealMark.class,(p,b)->{b.writeVarInt(p.entity);b.writeVarInt(p.ticks);},b->new HealMark(b.readVarInt(),b.readVarInt()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.StatusDamageClient.mark(p.entity(),p.ticks())));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(38,KillSound.class,(p,b)->b.writeUtf(p.sound,128),b->new KillSound(b.readUtf(128)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.TaczSounds.play(p.sound())));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(25,RapierStep.class,(p,b)->{b.writeVarInt(p.entity);b.writeFloat(p.yaw);b.writeFloat(p.distance);},b->new RapierStep(b.readVarInt(),b.readFloat(),b.readFloat()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.RapierTrail.receive(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(24,SecretBlockSync.class,(p,b)->{b.writeBoolean(p.full);b.writeVarInt(p.positions.length);for(int i=0;i<p.positions.length;i++){b.writeLong(p.positions[i]);b.writeByte(p.modes[i]);}},b->{
            boolean full=b.readBoolean();int n=b.readVarInt();long[] pos=new long[n];byte[] modes=new byte[n];for(int i=0;i<n;i++){pos[i]=b.readLong();modes[i]=b.readByte();}return new SecretBlockSync(full,pos,modes);
        },(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.SecretBlocksClient.receive(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(23,ThermalBurst.class,(p,b)->{b.writeDouble(p.start.x);b.writeDouble(p.start.y);b.writeDouble(p.start.z);b.writeDouble(p.end.x);b.writeDouble(p.end.y);b.writeDouble(p.end.z);b.writeBoolean(p.scald);},
                b->new ThermalBurst(new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBoolean()),(p,context)->{
                    var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.ThermalParticles.receive(p)));ctx.setPacketHandled(true);
                },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(21,ConveyorView.class,(p,b)->{b.writeBlockPos(p.pos);b.writeVarInt(p.speed);},b->new ConveyorView(b.readBlockPos(),b.readVarInt()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.ConveyorScreen.open(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(22,ConveyorSet.class,(p,b)->{b.writeBlockPos(p.pos);b.writeVarInt(p.speed);b.writeBoolean(p.reverse);},b->new ConveyorSet(b.readBlockPos(),b.readVarInt(),b.readBoolean()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->ConveyorWrench.apply(ctx.getSender(),p.pos,p.speed,p.reverse));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(20,NozzleBurst.class,(p,b)->{b.writeBlockPos(p.pos);b.writeBoolean(p.acid);b.writeDouble(p.height);b.writeVarInt(p.count);},
                b->new NozzleBurst(b.readBlockPos(),b.readBoolean(),b.readDouble(),b.readVarInt()),(p,context)->{
                    var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.NozzleParticles.receive(p)));ctx.setPacketHandled(true);
                },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(31,EaselPaint.class,(p,b)->{b.writeBlockPos(p.pos);b.writeUtf(p.pixels,512);},b->new EaselPaint(b.readBlockPos(),b.readUtf(512)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->{
                var sender=ctx.getSender();if(sender==null||sender.isSpectator()||sender.distanceToSqr(p.pos.getCenter())>64)return;
                if(sender.level().getBlockEntity(p.pos) instanceof ToyBlocks.EaselEntity easel){easel.paint(p.pixels);sender.level().playSound(null,p.pos,net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,net.minecraft.sounds.SoundSource.BLOCKS,.8f,1.3f);}
            });ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(26,PropText.class,(p,b)->b.writeUtf(p.text,4096),b->new PropText(b.readUtf(4096)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->{var match=MatchManager.propHunt();if(ctx.getSender()!=null&&match!=null)match.propText(ctx.getSender(),p.text);});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(18,TeamPointRequest.class,(p,b)->{},b->new TeamPointRequest(),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->TeamPoints.request(ctx.getSender()));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(19,TeamPointView.class,(p,b)->b.writeUtf(p.json,262144),b->new TeamPointView(b.readUtf(262144)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.TeamPointHud.receive(p.json)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(17,Levels.class,(p,b)->b.writeUtf(p.json,262144),b->new Levels(b.readUtf(262144)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.PlayerLevels.receive(p.json)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(16,BotSkin.class,(p,b)->{b.writeUtf(p.skin,200);b.writeByteArray(p.png);},b->new BotSkin(b.readUtf(200),b.readByteArray(BotSkinFiles.MAX_BYTES)),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.BotSkinCache.receive(p)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(15,Emblems.class,(p,b)->{},b->new Emblems(),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->dev.gungang.acoustics.client.LevelEmblemScreen::open));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
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
        CHANNEL.registerMessage(5,Gadget.class,(p,b)->{b.writeUtf(p.preview,32);b.writeDouble(p.range);b.writeUtf(ServerFiles.GSON.toJson(p.spawnAreas),4096);b.writeBoolean(p.flagExists);},b->new Gadget(b.readUtf(32),b.readDouble(),java.util.Arrays.asList(ServerFiles.GSON.fromJson(b.readUtf(4096),ArenaMap.Area[].class)),b.readBoolean()),(p,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.gungang.acoustics.client.GadgetClient.receive(p.preview,p.range,p.spawnAreas,p.flagExists)));ctx.setPacketHandled(true);
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
        // The value carries whole game options as JSON (several hundred characters with per-mode settings), so it is not capped at 64.
        CHANNEL.registerMessage(2, Action.class, (p, b) -> { b.writeUtf(p.action, 16); b.writeUtf(p.value, ACTION_VALUE); b.writeUtf(p.target, 36); },
                b -> new Action(b.readUtf(16), b.readUtf(ACTION_VALUE), b.readUtf(36)), (p, context) -> {
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
