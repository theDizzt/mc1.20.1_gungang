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

/** Server-authoritative objective ownership and world interactions. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class BombMatch {
    public record View(int round,int totalRounds,String attacker,String phase,BombRules.Point pointA,BombRules.Point pointB,
                       String plantedSite,int fuseTicks,String dimension,List<String> deadPlayers,BombRules.Site areaA,BombRules.Site areaB,BombRules.Point plantedPosition){
        public View(int round,int totalRounds,String attacker,String phase,BombRules.Point pointA,BombRules.Point pointB,String plantedSite,int fuseTicks,String dimension){this(round,totalRounds,attacker,phase,pointA,pointB,plantedSite,fuseTicks,dimension,List.of(),null,null,null);}
    }
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
    private UUID bomber,defuser;
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
    public boolean assigned(UUID id){return id.equals(bomber)||id.equals(defuser);}
    public boolean activePlay(){return game.phase()==BombGame.Phase.RUNNING;}
    public ArenaMap.Spawn spawn(String team){return team.equals(game.attacker())?arena.bomb().attackSpawn():arena.bomb().defenseSpawn();}
    public ArenaMap.Area area(String team){return team.equals(game.attacker())?arena.bomb().attackArea():arena.bomb().defenseArea();}
    private List<ServerPlayer> alive(String team){return server.getPlayerList().getPlayers().stream().filter(p->LobbyManager.team(p.getUUID()).equals(team)&&p.isAlive()&&!p.isSpectator()&&!dead(p.getUUID())&&p.level()==level).toList();}
    public void assignEquipment(boolean reroll){
        if(reroll){bomber=select(game.attacker());defuser=select(game.defender());}
        if(!game.planted())give(bomber,false);
        give(defuser,true);
        for(var p:server.getPlayerList().getPlayers())if(LobbyManager.team(p.getUUID()).equals("ALPHA")||LobbyManager.team(p.getUUID()).equals("BRAVO"))
            p.sendSystemMessage(Component.literal("ROUND "+game.round()+" · "+(LobbyManager.team(p.getUUID()).equals(game.attacker())?"공격팀":"방어팀")+" · "+arena.bomb().winsRequired()+"선승"));
    }
    private UUID select(String team){var candidates=new ArrayList<>(alive(team));Collections.shuffle(candidates);return candidates.isEmpty()?null:candidates.get(0).getUUID();}
    private void give(UUID id,boolean tool){if(id==null)return;var p=server.getPlayerList().getPlayer(id);if(p!=null){LobbyManager.giveBombEquipment(p,tool);p.sendSystemMessage(Component.literal(tool?"폭탄 해체기를 받았습니다.":"폭탄을 받았습니다. A 또는 B에 설치하세요."));}}
    private void transfer(){
        if(!game.planted()&&alive(game.attacker()).stream().noneMatch(p->p.getUUID().equals(bomber))){bomber=select(game.attacker());give(bomber,false);}
        if(alive(game.defender()).stream().noneMatch(p->p.getUUID().equals(defuser))){defuser=select(game.defender());give(defuser,true);}
    }
    public void eliminate(ServerPlayer p){
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
        uses.entrySet().removeIf(e->{var p=server.getPlayerList().getPlayer(e.getKey());return p==null||!p.isUsingItem()||!eligible(p,e.getValue().defuser);});
        var before=game.phase();
        if(before==BombGame.Phase.PREPARING){
            for(var p:alive(game.attacker()))if(!inside(p,arena.bomb().attackArea()))LobbyManager.bombTeleport(p);
        }
        if(before==BombGame.Phase.RUNNING||before==BombGame.Phase.PREPARING)transfer();
        game.tick(alive(game.attacker()).size(),alive(game.defender()).size());
        if(game.phase()!=before){
            if(game.phase()==BombGame.Phase.RUNNING)LobbyManager.startBombRound();
            else if(game.phase()==BombGame.Phase.ROUND_END)roundEnded();
            else if(game.phase()==BombGame.Phase.PREPARING){cleanupBomb();dead.clear();watching.clear();uses.clear();LobbyManager.prepareBombRound();assignEquipment(true);}
            else if(game.phase()==BombGame.Phase.FINISHED)LobbyManager.finish(game.winner());
        }
    }
    private static boolean inside(ServerPlayer p,ArenaMap.Area area){var b=p.getBoundingBox();return area.contains(b.minX,b.minY,b.minZ)&&area.contains(b.maxX,b.maxY,b.maxZ);}
    private void roundEnded(){
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
                &&p.getUUID().equals(tool?defuser:bomber)&&LobbyManager.team(p.getUUID()).equals(tool?game.defender():game.attacker())
                &&p.getMainHandItem().is(tool?GadgetItems.DEFUSER.get():GadgetItems.BOMB.get())&&(tool?game.planted():!game.planted());
    }
    private BlockPos point(String site){var p=site.equals("A")?arena.bomb().pointA():arena.bomb().pointB();return new BlockPos(p.x(),p.y(),p.z());}
    private Aim aimed(ServerPlayer p,boolean tool){
        var hit=p.pick(4.5,1,false);if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK)return null;
        if(tool)return plantedPosition!=null&&block.getBlockPos().equals(plantedPosition)&&level.getBlockState(plantedPosition).is(GimmickBlocks.BOMB.get())?new Aim(plantedSite,plantedPosition):null;
        if(block.getDirection()!=net.minecraft.core.Direction.UP)return null;
        var floor=block.getBlockPos();var placement=floor.above();if(!level.getBlockState(placement).isAir()||placement.getY()>=level.getMaxBuildHeight())return null;
        for(String site:List.of("A","B")){
            var area=site.equals("A")?arena.bomb().areaA():arena.bomb().areaB();
            if(area!=null?area.contains(floor.getX(),floor.getY(),floor.getZ()):placement.equals(point(site)))return new Aim(site,placement);
        }
        return null;
    }
    public boolean beginUse(ServerPlayer p,boolean tool){
        if(!eligible(p,tool))return false;
        var aim=aimed(p,tool);
        if(aim==null){p.displayClientMessage(Component.literal(tool?"설치된 폭탄을 조준하세요.":"A 또는 B 설치 영역의 바닥을 조준하세요."),true);return false;}
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
            var pos=use.position;replaced=level.getBlockState(pos);
            if(!replaced.isAir()||!level.setBlock(pos,GimmickBlocks.BOMB.get().defaultBlockState(),3))return false;
            if(game.plant(timing.fuseTicks())){
                plantedSite=use.site;plantedPosition=pos;removeObjectives(p);
                for(var player:server.getPlayerList().getPlayers())player.sendSystemMessage(Component.literal(use.site+" 지점 폭탄 설치 · "+timing.fuseSeconds()+"초"));
            }
        }
        return false;
    }
    public void cancelUse(ServerPlayer p){uses.remove(p.getUUID());}
    public CombatGadgets.Gauge gauge(ServerPlayer p){
        var use=uses.get(p.getUUID());if(use==null||!p.isUsingItem())return null;
        int total=use.defuser?timing.defuseTicks():timing.plantTicks();
        return new CombatGadgets.Gauge(use.defuser?"bomb_defuse":"bomb_plant",Math.max(0,total-(level.getGameTime()-use.start)),total,true);
    }
    public View view(){return new View(game.round(),arena.bomb().rounds(),game.attacker(),game.phase().name(),arena.bomb().pointA(),arena.bomb().pointB(),plantedSite,game.fuseTicks(),arena.dimension(),dead.stream().map(UUID::toString).sorted().toList(),arena.bomb().areaA(),arena.bomb().areaB(),plantedPosition==null?null:new BombRules.Point(plantedPosition.getX(),plantedPosition.getY(),plantedPosition.getZ()));}
    private static void removeObjectives(ServerPlayer p){
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).getItem() instanceof BombItem)p.getInventory().setItem(i,ItemStack.EMPTY);
        p.inventoryMenu.broadcastChanges();
    }
    private void cleanupBomb(){
        if(plantedPosition!=null){var pos=plantedPosition;if(level.getBlockState(pos).is(GimmickBlocks.BOMB.get()))level.setBlock(pos,replaced==null?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():replaced,3);}
        plantedSite="";plantedPosition=null;replaced=null;
    }
    public void close(){cleanupBomb();uses.clear();dead.clear();watching.clear();for(var p:server.getPlayerList().getPlayers()){p.setCamera(p);p.stopUsingItem();removeObjectives(p);}}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void breaking(net.minecraftforge.event.level.BlockEvent.BreakEvent event){
        if(event.getState().is(GimmickBlocks.BOMB.get()))event.setCanceled(true);
    }
}
