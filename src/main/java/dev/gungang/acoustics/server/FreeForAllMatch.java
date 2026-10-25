package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Team;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class FreeForAllMatch {
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void attack(net.minecraftforge.event.entity.living.LivingAttackEvent e){
        var match=MatchManager.freeForAll();if(match==null)return;
        if((match.participant(e.getEntity().getUUID())&&match.remaining(e.getEntity().getUUID())>0)
            ||(e.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker&&attacker!=e.getEntity()&&blocked(attacker)))
            e.setCanceled(true);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void use(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem e){
        if(blocked(e.getEntity())){e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);e.setCanceled(true);}
    }
    /** After each respawn: invulnerable, unable to attack, and mutually invisible with everyone else. */
    public static final int RESPAWN_PROTECTION_TICKS=300;
    public static final int[] COLORS={0xEF4444,0xF97316,0xFACC15,0xA3E635,0x16A34A,0x06B6D4,0x2563EB,0x312E81,0x7C3AED,0x800040,0xD946EF,0xF9A8D4,0x92400E,0xFFFFFF,0x808080,0x151515};
    final MinecraftServer server;
    final ArenaMap map;
    final FreeForAllScores scores=new FreeForAllScores();
    private final Map<UUID,Integer> colors=new HashMap<>(),hiddenUntil=new HashMap<>();
    private final Map<UUID,Boolean> silence=new HashMap<>(),invisibility=new HashMap<>();
    private final List<Integer> palette=new ArrayList<>();
    private final Map<ArenaMap.Spawn,Integer> used=new HashMap<>();
    private int started=-1;
    public FreeForAllMatch(MinecraftServer server,ArenaMap map){this.server=server;this.map=map;for(int c:COLORS)palette.add(c);Collections.shuffle(palette);}
    public void add(ServerPlayer p){
        scores.add(p.getUUID());colors.computeIfAbsent(p.getUUID(),id->palette.get(colors.size()%palette.size()));
        String id="ffa_"+p.getUUID().toString().replace("-","").substring(0,12);
        var board=server.getScoreboard();var team=board.getPlayerTeam(id);if(team==null)team=board.addPlayerTeam(id);
        var nearest=Arrays.stream(net.minecraft.ChatFormatting.values()).filter(c->c.getColor()!=null).min(Comparator.comparingInt(c->{int rgb=c.getColor(),actual=color(p.getUUID());int r=(rgb>>16&255)-(actual>>16&255),g=(rgb>>8&255)-(actual>>8&255),b=(rgb&255)-(actual&255);return r*r+g*g+b*b;})).orElse(net.minecraft.ChatFormatting.WHITE);
        team.setColor(nearest);
        team.setDisplayName(Component.empty());team.setPlayerPrefix(Component.empty());team.setNameTagVisibility(Team.Visibility.NEVER);
        team.setAllowFriendlyFire(true);team.setSeeFriendlyInvisibles(false);
        board.addPlayerToTeam(p.getScoreboardName(),team);
        protect(p);
    }
    public int color(UUID id){return colors.getOrDefault(id,0xFFFFFF);}
    public void leave(ServerPlayer p){restore(p);}
    public void start(){started=server.getTickCount();}
    public boolean opening(){return started<0||server.getTickCount()-started<600;}
    public boolean participant(UUID id){return colors.containsKey(id);}
    public int remaining(UUID id){if(!participant(id))return 0;return opening()?started<0?600:Math.max(0,600-(server.getTickCount()-started)):Math.max(0,hiddenUntil.getOrDefault(id,0)-server.getTickCount());}
    public void protect(ServerPlayer p){hiddenUntil.put(p.getUUID(),server.getTickCount()+RESPAWN_PROTECTION_TICKS);update(p);}
    private void update(ServerPlayer p){
        int left=remaining(p.getUUID());boolean hidden=left>0&&!p.isSpectator();
        if(hidden){silence.putIfAbsent(p.getUUID(),p.isSilent());invisibility.putIfAbsent(p.getUUID(),p.isInvisible());p.setSilent(true);p.setInvisible(true);p.stopUsingItem();
            p.displayClientMessage(Component.literal((opening()?"시작 무적":"리스폰 무적")+" "+(left+19)/20+"초 남음"),true);
        }else if(restore(p))p.displayClientMessage(Component.empty(),true);
    }
    /** Returns whether the player was still hidden. */
    private boolean restore(ServerPlayer p){boolean was=silence.containsKey(p.getUUID())||invisibility.containsKey(p.getUUID());if(silence.containsKey(p.getUUID()))p.setSilent(silence.remove(p.getUUID()));if(invisibility.containsKey(p.getUUID()))p.setInvisible(invisibility.remove(p.getUUID()));return was;}
    public void tick(){for(var p:server.getPlayerList().getPlayers())if(participant(p.getUUID()))update(p);}
    public void close(){for(var p:server.getPlayerList().getPlayers())restore(p);var board=server.getScoreboard();for(var t:List.copyOf(board.getPlayerTeams()))if(t.getName().startsWith("ffa_"))board.removePlayerTeam(t);}
    public void spawn(ServerPlayer p){
        var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(map.dimension())));
        var candidates=new ArrayList<>(map.freeForAll().spawns());Collections.shuffle(candidates);
        var spawn=candidates.stream().max(Comparator.<ArenaMap.Spawn>comparingDouble(s->{
            double distance=server.getPlayerList().getPlayers().stream().filter(q->q!=p&&participant(q.getUUID())&&!q.isSpectator()&&q.isAlive()&&q.level()==level)
                .mapToDouble(q->q.distanceToSqr(s.x(),s.y(),s.z())).min().orElse(1e9);
            return used.getOrDefault(s,-1000)==server.getTickCount()?-1:distance;
        })).orElseThrow();used.put(spawn,server.getTickCount());
        p.setCamera(p);p.stopRiding();p.teleportTo(level,spawn.x(),spawn.y(),spawn.z(),spawn.yaw(),spawn.pitch());p.fallDistance=0;
    }
    public void openingDeath(ServerPlayer p){p.setHealth(p.getMaxHealth());p.clearFire();spawn(p);}
    public void death(ServerPlayer p,ServerPlayer killer){scores.death(p.getUUID(),killer==null?null:killer.getUUID());}
    public String winner(boolean expired){UUID id=scores.winner(map.freeForAll().winningScore(),expired);return id==null?"":id.toString();}
    public List<MatchSnapshot.Row> rows(List<MatchSnapshot.Row> stats){
        var result=new ArrayList<MatchSnapshot.Row>();
        for(UUID id:scores.ranking())for(var r:stats)if(r.id().equals(id.toString()))result.add(new MatchSnapshot.Row(r.id(),r.name(),r.team(),r.kills(),r.deaths(),r.assists(),r.damage(),r.playSeconds(),scores.points(id),colors.get(id),remaining(id),opening()?600:RESPAWN_PROTECTION_TICKS));
        return result;
    }
    public static boolean blocked(net.minecraft.world.entity.LivingEntity entity){var match=MatchManager.freeForAll();return !entity.level().isClientSide&&match!=null&&(match.remaining(entity.getUUID())>0||LobbyManager.respawning(entity.getUUID()));}
}
