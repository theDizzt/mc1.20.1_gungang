package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Coordinates are ray-traced on the server and sent only to the owner's logical team. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class TeamPoints {
    static final TeamPointState STATE=new TeamPointState();
    private static final Map<UUID,String> sent=new HashMap<>();
    private static final Map<UUID,Integer> nextRequest=new HashMap<>();
    private static boolean available(){return MatchManager.active()&&MatchManager.freeForAll()==null;}
    static void refresh(MinecraftServer server) {
        var roster=new LinkedHashMap<UUID,String>();
        if(available()) {
            for(var p:server.getPlayerList().getPlayers())roster.put(p.getUUID(),LobbyManager.team(p.getUUID()));
            for(var id:BotManager.lobbyIds())roster.put(id,BotManager.team(id));
        }
        STATE.roster(roster);
    }
    public static void request(ServerPlayer player) {
        if(player==null||!available()||!player.isAlive()||player.isSpectator()||LobbyManager.respawning(player.getUUID())
                ||!TeamPointState.validTeam(LobbyManager.team(player.getUUID())))return;
        var server=player.getServer();int now=server.getTickCount();
        if(now<nextRequest.getOrDefault(player.getUUID(),0))return;
        nextRequest.put(player.getUUID(),now+5);refresh(server);
        var eye=player.getEyePosition();var hit=player.level().clip(new ClipContext(eye,eye.add(player.getLookAngle().scale(96)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.BLOCK)return;
        var point=hit.getLocation().add(net.minecraft.world.phys.Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.05));
        STATE.mark(player.getUUID(),player.level().dimension().location().toString(),point.x,point.y,point.z);
        sync(server);
    }
    public static void clear(MinecraftServer server) {
        STATE.clear();nextRequest.clear();sent.clear();if(server!=null){refresh(server);sync(server);}
    }
    static TeamPointState.Point latest(String team,String dimension,long after) {
        return STATE.view(team,0).points().stream().filter(p->p.sequence()>after&&p.dimension().equals(dimension))
                .max(Comparator.comparingLong(TeamPointState.Point::sequence)).orElse(null);
    }
    private static void sync(MinecraftServer server) {
        var payloads=new HashMap<String,String>();var online=new HashSet<UUID>();
        for(var player:server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());String team=available()?LobbyManager.team(player.getUUID()):"";
            String json=payloads.computeIfAbsent(team,t->ServerFiles.GSON.toJson(STATE.view(t,LobbyManager.options().color(t))));
            if(GungangNetwork.installed(player)&&!json.equals(sent.get(player.getUUID()))) {
                GungangNetwork.send(player,new GungangNetwork.TeamPointView(json));sent.put(player.getUUID(),json);
            }
        }
        sent.keySet().retainAll(online);nextRequest.keySet().retainAll(online);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();refresh(server);sync(server);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){STATE.reset();sent.clear();nextRequest.clear();}
    private TeamPoints() { }
}
