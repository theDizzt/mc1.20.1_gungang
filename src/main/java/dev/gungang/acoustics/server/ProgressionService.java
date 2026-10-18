package dev.gungang.acoustics.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.gungang.acoustics.GungangAcoustics;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class ProgressionService {
    private static PlayerDatabase database;
    private static XpTable table=XpTable.defaults();
    private static final Set<String> FIELDS=Set.of("xp","matches","wins","kills","deaths","damage","playSeconds");
    public static void configure(MinecraftServer server) throws java.io.IOException {
        Path file=ServerFiles.root().resolve("xp.json");ConfigLayout.writeNew(file,ServerFiles.GSON.toJson(XpTable.defaults()));
        if(Files.size(file)>65536)throw new java.io.IOException("xp.json is too large");
        XpTable candidate=ServerFiles.GSON.fromJson(Files.readString(file),XpTable.class);
        if(candidate==null)throw new java.io.IOException("Empty xp.json");
        if(database==null)database=new PlayerDatabase(ServerFiles.root().resolve("player-db.json"));
        table=candidate;
        for(var p:server.getPlayerList().getPlayers())sync(p);
    }
    public static void sync(ServerPlayer player) {
        if(database==null)return;
        var record=database.get(player.getUUID().toString(),player.getGameProfile().getName());
        GungangNetwork.send(player,new GungangNetwork.Progression(ServerFiles.GSON.toJson(table.progress(record.experience()))));
    }
    static PlayerRecord record(ServerPlayer player) {
        if(database==null)throw new IllegalStateException("플레이어 DB가 로드되지 않았습니다.");
        return database.get(player.getUUID().toString(),player.getGameProfile().getName());
    }
    static void store(PlayerRecord record) throws java.io.IOException {
        if(database==null)throw new IllegalStateException("플레이어 DB가 로드되지 않았습니다.");
        database.update(record);
    }
    /** Preset applied at match start, or null when none is set or the database is unavailable. */
    static PlayerRecord.Preset defaultPreset(ServerPlayer player) {return database==null?null:record(player).defaultLoadout();}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||database==null)return;
        try {database.update(database.get(p.getUUID().toString(),p.getGameProfile().getName()).named(p.getGameProfile().getName()));sync(p);}
        catch(Exception e) {com.mojang.logging.LogUtils.getLogger().error("[GunGang] Player database login failed",e);}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {database=null;table=XpTable.defaults();}
    public static void finish(MinecraftServer server,MatchSnapshot match) {
        if(database==null) {com.mojang.logging.LogUtils.getLogger().error("[GunGang] Player database unavailable; match statistics not saved");return;}
        try {
            var rewards=database.match(match.players(),match.winner(),LobbyManager.options().experience());
            for(var p:server.getPlayerList().getPlayers()) {
                Integer xp=rewards.get(p.getUUID().toString());
                if(xp!=null) {sync(p);p.sendSystemMessage(Component.literal("[GunGang] 경기 전적 저장 · 경험치 +"+xp));}
            }
        } catch(Exception e) {com.mojang.logging.LogUtils.getLogger().error("[GunGang] Could not commit match statistics to player-db.json",e);}
    }
    private static PlayerRecord resolve(CommandSourceStack source,String target) throws Exception {
        if(database==null)throw new IllegalStateException("플레이어 DB가 로드되지 않았습니다.");
        if(target==null) {var p=source.getPlayerOrException();return database.get(p.getUUID().toString(),p.getGameProfile().getName());}
        var player=source.getServer().getPlayerList().getPlayerByName(target);
        if(player!=null)return database.get(player.getUUID().toString(),player.getGameProfile().getName());
        return database.all().stream().filter(r->r.id().equalsIgnoreCase(target)||r.name().equalsIgnoreCase(target)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("접속 중이거나 DB에 기록된 플레이어 이름/UUID를 입력하세요."));
    }
    private static void show(CommandSourceStack source,String target,boolean full) throws Exception {
        var row=resolve(source,target);var progress=table.progress(row.experience());
        String xp="Lv. "+progress.level()+" · 경험치 "+(progress.level()==91?"MAX":progress.current()+"/"+progress.required());
        String stats=full?String.format(Locale.ROOT,"\n통산 %d경기 · %d승 · %d킬 / %d데스 · 딜량 %.1f · 경기 플레이 %d시간 %d분 %d초",
                row.matches(),row.wins(),row.kills(),row.deaths(),row.damage(),row.playSeconds()/3600,row.playSeconds()/60%60,row.playSeconds()%60):"";
        // Explicit queries must remain visible even when sendCommandFeedback is disabled.
        var message=Component.literal(row.name()+" · "+xp+stats);
        if(source.getEntity() instanceof ServerPlayer player)player.sendSystemMessage(message);
        else source.sendSystemMessage(message);
    }
    private static void modify(CommandSourceStack source,String target,String field,String value,boolean add) throws Exception {
        if(!FIELDS.contains(field))throw new IllegalArgumentException("필드: xp, matches, wins, kills, deaths, damage, playSeconds");
        var old=resolve(source,target);
        if(add)value=Long.toString(Math.addExact(old.experience(),Long.parseLong(value)));
        var updated=old.set(field,value);database.update(updated);
        var player=source.getServer().getPlayerList().getPlayer(UUID.fromString(updated.id()));if(player!=null)sync(player);
        source.sendSuccess(()->Component.literal(updated.name()+"의 "+field+" 값을 저장했습니다."),true);
    }
    private interface Work {void run() throws Exception;}
    private static int run(CommandSourceStack source,Work work) {
        try {work.run();return 1;}catch(Exception e) {source.sendFailure(Component.literal("전적 처리 실패: "+e.getMessage()));return 0;}
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> playerArgument() {
        return Commands.argument("player",StringArgumentType.word()).suggests((c,b)->{
            Set<String> names=new TreeSet<>();if(database!=null)database.all().forEach(r->names.add(r.name()));
            c.getSource().getServer().getPlayerList().getPlayers().forEach(p->names.add(p.getGameProfile().getName()));
            return SharedSuggestionProvider.suggest(names,b);
        });
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root=Commands.literal("gungang");
        var stats=Commands.literal("stats").executes(c->run(c.getSource(),()->show(c.getSource(),null,true)))
                .then(playerArgument().executes(c->run(c.getSource(),()->show(c.getSource(),StringArgumentType.getString(c,"player"),true))));
        stats.then(Commands.literal("set").requires(s->s.hasPermission(2)).then(playerArgument().then(
                Commands.argument("field",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(FIELDS,b)).then(
                        Commands.argument("value",StringArgumentType.word()).executes(c->run(c.getSource(),()->modify(c.getSource(),StringArgumentType.getString(c,"player"),
                                StringArgumentType.getString(c,"field"),StringArgumentType.getString(c,"value"),false)))))));
        root.then(stats);
        var xp=Commands.literal("xp").executes(c->run(c.getSource(),()->show(c.getSource(),null,false)))
                .then(playerArgument().executes(c->run(c.getSource(),()->show(c.getSource(),StringArgumentType.getString(c,"player"),false))));
        for(String action:List.of("set","add"))xp.then(Commands.literal(action).requires(s->s.hasPermission(2)).then(playerArgument().then(
                Commands.argument("value",StringArgumentType.word()).executes(c->run(c.getSource(),()->modify(c.getSource(),StringArgumentType.getString(c,"player"),"xp",StringArgumentType.getString(c,"value"),action.equals("add")))))));
        root.then(xp);
        root.then(Commands.literal("level").executes(c->run(c.getSource(),()->show(c.getSource(),null,false)))
                .then(playerArgument().executes(c->run(c.getSource(),()->show(c.getSource(),StringArgumentType.getString(c,"player"),false)))));
        event.getDispatcher().register(root);
    }
    private ProgressionService() { }
}
