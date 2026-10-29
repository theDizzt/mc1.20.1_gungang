package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Server-authoritative objective and world interactions. Any living attacker plants with E inside A/B, any living defender defuses with E on the bomb. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class BombMatch {
    public record View(int round,int totalRounds,String attacker,String phase,BombRules.Point pointA,BombRules.Point pointB,
                       String plantedSite,int fuseTicks,String dimension,List<String> deadPlayers,BombRules.Site areaA,BombRules.Site areaB,BombRules.Point plantedPosition,List<Mate> mates,String mapId,MapPreviewArea previewArea){
        public View(int round,int totalRounds,String attacker,String phase,BombRules.Point pointA,BombRules.Point pointB,String plantedSite,int fuseTicks,String dimension,List<String> deadPlayers,BombRules.Site areaA,BombRules.Site areaB,BombRules.Point plantedPosition){this(round,totalRounds,attacker,phase,pointA,pointB,plantedSite,fuseTicks,dimension,deadPlayers,areaA,areaB,plantedPosition,List.of(),"",null);}
        /** Same view with only the given team's members, so the TAB minimap never reveals enemies. */
        public View forTeam(String team){return new View(round,totalRounds,attacker,phase,pointA,pointB,plantedSite,fuseTicks,dimension,deadPlayers,areaA,areaB,plantedPosition,mates==null?List.of():mates.stream().filter(m->m.team().equals(team)).toList(),mapId,previewArea);}
        public View(int round,int totalRounds,String attacker,String phase,BombRules.Point pointA,BombRules.Point pointB,String plantedSite,int fuseTicks,String dimension){this(round,totalRounds,attacker,phase,pointA,pointB,plantedSite,fuseTicks,dimension,List.of(),null,null,null);}
    }
    /** A living player for the TAB minimap; each team receives only its own. */
    public record Mate(String team,String id,double x,double z,float yaw){}
    private record Use(boolean defuser,String site,BlockPos position,Vec3 origin,long start){}
    private record Aim(String site,BlockPos position){}
    private final MinecraftServer server;
    private final ServerLevel level;
    private final ArenaMap arena;
    private final BombEquipment.Timing timing;
    public final BombGame game;
    private final Set<UUID> dead=new HashSet<>();
    private final Map<UUID,UUID> watching=new HashMap<>();
    private final Map<UUID,Use> uses=new HashMap<>();
    private String plantedSite="";
    private BlockPos plantedPosition;
    private BlockState replaced;
    public BombMatch(MinecraftServer server,ArenaMap arena){
        this.server=server;this.arena=arena;timing=BombEquipment.timing;
        level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(arena.dimension())));
        game=new BombGame(arena.bomb(),arena.timeLimitSeconds(),level.random.nextBoolean());
        for(var point:List.of(arena.bomb().pointA(),arena.bomb().pointB()))if(point.y()<=level.getMinBuildHeight()||point.y()>=level.getMaxBuildHeight())throw new IllegalArgumentException("Bomb site Y is outside the dimension");
    }
    public boolean dead(UUID id){return dead.contains(id);}
    public boolean activePlay(){return game.phase()==BombGame.Phase.RUNNING;}
    public ArenaMap.Spawn spawn(String team){return team.equals(game.attacker())?arena.bomb().attackSpawn():arena.bomb().defenseSpawn();}
    public ArenaMap.Area area(String team){return team.equals(game.attacker())?arena.bomb().attackArea():arena.bomb().defenseArea();}
    private List<ServerPlayer> alive(String team){return server.getPlayerList().getPlayers().stream().filter(p->LobbyManager.team(p.getUUID()).equals(team)&&p.isAlive()&&!p.isSpectator()&&!dead(p.getUUID())&&p.level()==level).toList();}
    public void announceRound(){
        for(var p:server.getPlayerList().getPlayers()){
            String team=LobbyManager.team(p.getUUID());if(!team.equals("ALPHA")&&!team.equals("BRAVO"))continue;
            boolean attack=team.equals(game.attacker());
            p.sendSystemMessage(Component.literal("ROUND "+game.round()+" · "+(attack?"공격팀":"방어팀")+" · "+arena.bomb().winsRequired()+"선승"+(arena.bomb().rounds()%2==0?" · 동점 시 무승부":"")
                    +(attack?" · A/B 영역에서 E를 "+timing.plantSeconds()+"초 눌러 폭탄 설치":" · 설치된 폭탄을 조준하고 E를 "+timing.defuseSeconds()+"초 눌러 해체")));
        }
    }
    public void eliminate(ServerPlayer p){
        if(dead(p.getUUID()))return;
        dead.add(p.getUUID());cancelUse(p);p.stopUsingItem();removeObjectives(p);
        p.setHealth(p.getMaxHealth());p.removeAllEffects();p.clearFire();p.fallDistance=0;p.setGameMode(GameType.SPECTATOR);
        updateSpectator(p,0);
        p.sendSystemMessage(Component.literal("다음 라운드까지 팀원을 관전합니다. 마우스 휠로 관전 대상을 변경하세요."));
    }
    public boolean allowsCamera(ServerPlayer viewer,net.minecraft.world.entity.Entity target){
        if(!dead(viewer.getUUID())||target==null||target==viewer)return true;
        return target instanceof ServerPlayer player&&player.level()==level&&player.isAlive()&&!player.isSpectator()
                &&!dead(player.getUUID())&&LobbyManager.team(viewer.getUUID()).equals(LobbyManager.team(player.getUUID()));
    }
    public void cycleSpectator(ServerPlayer viewer,int direction){if(dead(viewer.getUUID())&&(direction==1||direction==-1))updateSpectator(viewer,direction);}
    private void updateSpectator(ServerPlayer viewer,int direction){
        String team=LobbyManager.team(viewer.getUUID());if(!team.equals("ALPHA")&&!team.equals("BRAVO"))return;
        var candidates=alive(team).stream().sorted(Comparator.comparing(p->p.getUUID().toString())).toList();
        if(!viewer.isSpectator())viewer.setGameMode(GameType.SPECTATOR);
        if(candidates.isEmpty()){
            watching.remove(viewer.getUUID());if(viewer.getCamera()!=viewer)viewer.setCamera(viewer);
            var s=arena.spectatorSpawn();
            viewer.setDeltaMovement(Vec3.ZERO);
            if(viewer.level()!=level||viewer.position().distanceToSqr(s.x(),s.y(),s.z())>.01)viewer.teleportTo(level,s.x(),s.y(),s.z(),s.yaw(),s.pitch());return;
        }
        UUID current=watching.get(viewer.getUUID());int index=-1;
        for(int i=0;i<candidates.size();i++)if(candidates.get(i).getUUID().equals(current)){index=i;break;}
        if(index<0)index=0;else if(direction!=0)index=Math.floorMod(index+direction,candidates.size());
        var target=candidates.get(index);watching.put(viewer.getUUID(),target.getUUID());
        if(viewer.getCamera()!=target)viewer.setCamera(target);
    }
    public void lateJoin(ServerPlayer p){
        String team=LobbyManager.team(p.getUUID());
        if(team.equals("ALPHA")||team.equals("BRAVO"))eliminate(p);
        else {p.setGameMode(GameType.SPECTATOR);p.setCamera(p);}
    }
    public void tick(){
        for(var id:dead){var viewer=server.getPlayerList().getPlayer(id);if(viewer!=null)updateSpectator(viewer,0);}
        // E is held on the client; each server tick advances (or cancels) the plant/defuse.
        for(var id:List.copyOf(uses.keySet())){
            var use=uses.get(id);if(use==null)continue;
            var p=server.getPlayerList().getPlayer(id);if(p==null){uses.remove(id);continue;}
            continueUse(p,use.defuser);
        }
        var before=game.phase();
        if(before==BombGame.Phase.PREPARING){
            for(var p:alive(game.attacker()))if(!inside(p,arena.bomb().attackArea()))LobbyManager.bombTeleport(p);
        }
        game.tick(alive(game.attacker()).size(),alive(game.defender()).size());
        if(game.phase()!=before){
            if(game.phase()==BombGame.Phase.RUNNING)LobbyManager.startBombRound();
            else if(game.phase()==BombGame.Phase.ROUND_END)roundEnded();
            else if(game.phase()==BombGame.Phase.PREPARING){cleanupBomb();dead.clear();watching.clear();uses.clear();LobbyManager.prepareBombRound();announceRound();}
            else if(game.phase()==BombGame.Phase.FINISHED)LobbyManager.finish(game.winner());
        }
    }
    private static boolean inside(ServerPlayer p,ArenaMap.Area area){var b=p.getBoundingBox();return area.contains(b.minX,b.minY,b.minZ)&&area.contains(b.maxX,b.maxY,b.maxZ);}
    private void roundEnded(){
        GimmickEpoch.reset(server);
        BombDrops.clear(server);
        uses.clear();
        if(game.reason().equals("EXPLODED")){
            var pos=plantedPosition;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,1,0,0,0,0);
            level.playSound(null,pos,net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.BLOCKS,4,1);
        }
        cleanupBomb();
        for(var p:server.getPlayerList().getPlayers()){
            p.stopUsingItem();removeObjectives(p);
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0,60,0));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("ROUND "+game.round()+" · "+switch(game.reason()){case "EXPLODED"->"폭탄 폭발";case "DEFUSED"->"폭탄 해체";case "TIMEOUT"->"설치 저지";default->"상대 팀 전멸";})));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("Team "+game.roundWinner()+" 승리").withStyle(s->s.withColor(LobbyManager.options().color(game.roundWinner())&0xFFFFFF))));
        }
    }
    private boolean eligible(ServerPlayer p,boolean tool){
        return activePlay()&&p.level()==level&&p.isAlive()&&!p.isSpectator()&&!dead(p.getUUID())
                &&LobbyManager.team(p.getUUID()).equals(tool?game.defender():game.attacker())&&(tool?game.planted():!game.planted());
    }
    /** E pressed (down) or released by a client; the role follows the player's side this round. */
    public void key(ServerPlayer p,boolean down){
        if(!down){cancelUse(p);return;}
        if(uses.containsKey(p.getUUID()))return;
        beginUse(p,LobbyManager.team(p.getUUID()).equals(game.defender()));
    }
    private Aim aimed(ServerPlayer p,boolean tool){
        if(!tool)return plantAt(p);
        var hit=p.pick(4.5,1,false);if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK)return null;
        return plantedPosition!=null&&block.getBlockPos().equals(plantedPosition)&&level.getBlockState(plantedPosition).is(GimmickBlocks.BOMB.get())?new Aim(plantedSite,plantedPosition):null;
    }
    /** Planting uses the floor under the player's feet, not the crosshair. */
    private Aim plantAt(ServerPlayer p){
        if(!p.onGround())return null;
        var place=BombRules.standing(level,p.getOnPos());var placement=place.cell();if(!place.ok()||placement.getY()>=level.getMaxBuildHeight())return null;
        var b=arena.bomb();String site=BombRules.siteAt(b.areaA(),b.areaB(),b.pointA(),b.pointB(),placement.getX(),placement.getY()-1,placement.getZ());
        return site==null?null:new Aim(site,placement);
    }
    public boolean beginUse(ServerPlayer p,boolean tool){
        if(!tool&&game.planted()&&LobbyManager.team(p.getUUID()).equals(game.attacker())){p.displayClientMessage(Component.literal("폭탄이 이미 "+plantedSite+" 지점에 설치되었습니다."),true);return false;}
        if(!eligible(p,tool))return false;
        var aim=aimed(p,tool);
        if(aim==null){p.displayClientMessage(Component.literal(tool?"설치된 폭탄을 조준하세요.":"A 또는 B 설치 영역의 바닥에 서서 사용하세요."),true);return false;}
        uses.put(p.getUUID(),new Use(tool,aim.site,aim.position,p.position(),level.getGameTime()));return true;
    }
    public boolean continueUse(ServerPlayer p,boolean tool){
        var use=uses.get(p.getUUID());
        var aim=aimed(p,tool);
        if(use==null||use.defuser!=tool||!eligible(p,tool)||p.position().distanceToSqr(use.origin)>.1225||aim==null||!aim.site.equals(use.site)||!aim.position.equals(use.position)){
            cancelUse(p);return false;
        }
        int total=tool?timing.defuseTicks():timing.plantTicks();
        if(level.getGameTime()-use.start<total)return true;
        uses.remove(p.getUUID());
        if(tool){if(game.defuse())roundEnded();}
        else {
            if(game.planted())return false; // a teammate finished first on the same tick
            var pos=use.position;replaced=level.getBlockState(pos);
            if(!BombRules.open(replaced)||!level.setBlock(pos,GimmickBlocks.BOMB.get().defaultBlockState(),3))return false;
            if(game.plant(timing.fuseTicks())){
                plantedSite=use.site;plantedPosition=pos;removeObjectives(p);
                // One bomb per round: stop every other plant in progress and take the remaining bombs away.
                uses.values().removeIf(other->!other.defuser);BombDrops.clear(server);
                for(var mate:alive(game.attacker())){
                    removeObjectives(mate);
                    if(mate!=p)mate.displayClientMessage(Component.literal(p.getGameProfile().getName()+" 님이 "+use.site+" 지점에 폭탄을 설치했습니다 · 더 이상 설치할 수 없습니다"),true);
                }
                for(var player:server.getPlayerList().getPlayers())player.sendSystemMessage(Component.literal(use.site+" 지점 폭탄 설치 · "+timing.fuseSeconds()+"초"));
            }
        }
        return false;
    }
    public void cancelUse(ServerPlayer p){uses.remove(p.getUUID());}
    public CombatGadgets.Gauge gauge(ServerPlayer p){
        var use=uses.get(p.getUUID());if(use==null)return null;
        int total=use.defuser?timing.defuseTicks():timing.plantTicks();
        return new CombatGadgets.Gauge(use.defuser?"bomb_defuse":"bomb_plant",Math.max(0,total-(level.getGameTime()-use.start)),total,true);
    }
    public View view(){return new View(game.round(),arena.bomb().rounds(),game.attacker(),game.phase().name(),arena.bomb().pointA(),arena.bomb().pointB(),plantedSite,game.fuseTicks(),arena.dimension(),dead.stream().map(UUID::toString).sorted().toList(),arena.bomb().areaA(),arena.bomb().areaB(),plantedPosition==null?null:new BombRules.Point(plantedPosition.getX(),plantedPosition.getY(),plantedPosition.getZ()),mates(),arena.id(),arena.previewArea());}
    private List<Mate> mates(){
        var mates=new ArrayList<Mate>();
        for(String team:List.of("ALPHA","BRAVO"))for(var p:alive(team))mates.add(new Mate(team,p.getUUID().toString(),p.getX(),p.getZ(),p.getYRot()));
        return mates;
    }
    private static void removeObjectives(ServerPlayer p){
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).getItem() instanceof BombItem)p.getInventory().setItem(i,ItemStack.EMPTY);
        p.inventoryMenu.broadcastChanges();
    }
    private void cleanupBomb(){
        if(plantedPosition!=null){var pos=plantedPosition;if(level.getBlockState(pos).is(GimmickBlocks.BOMB.get()))level.setBlock(pos,replaced==null?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():replaced,3);}
        plantedSite="";plantedPosition=null;replaced=null;
    }
    public void close(){BombDrops.clear(server);cleanupBomb();uses.clear();dead.clear();watching.clear();for(var p:server.getPlayerList().getPlayers()){p.setCamera(p);p.stopUsingItem();removeObjectives(p);}}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void breaking(net.minecraftforge.event.level.BlockEvent.BreakEvent event){
        if(event.getState().is(GimmickBlocks.BOMB.get()))event.setCanceled(true);
    }
}
