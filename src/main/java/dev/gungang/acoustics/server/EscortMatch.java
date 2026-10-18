package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class EscortMatch {
    public record View(double distance,double length,List<Double> checkpoints,int completed,String motion,double multiplier,int stopTicks,double x,double y,double z,int overtimeTicks){
        public View(double distance,double length,List<Double> checkpoints,int completed,String motion,double multiplier,int stopTicks,double x,double y,double z){this(distance,length,checkpoints,completed,motion,multiplier,stopTicks,x,y,z,0);}
    }
    public final EscortGame game;
    public final EscortRoute route;
    public final EscortCart cart;
    private final ServerLevel level;
    private final ArenaMap map;
    private final Map<BlockPos,Boolean> hidden=new HashMap<>();
    private final Set<net.minecraft.world.level.ChunkPos> tickets=new HashSet<>();
    private static final net.minecraft.server.level.TicketType<BlockPos> TICKET=net.minecraft.server.level.TicketType.create("gungang_escort",Comparator.comparingLong(BlockPos::asLong));
    public static ServerLevel level(MinecraftServer server,ArenaMap map){
        var l=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(map.dimension())));
        if(l==null)throw new IllegalArgumentException("호위 맵 차원이 없습니다.");return l;
    }
    public static EscortRoute validate(MinecraftServer server,ArenaMap map){
        var r=EscortRoute.scan(level(server,map),map.escort());
        if(r.length>2048)throw new IllegalArgumentException("호위 경로는 2048블록 이하여야 합니다.");
        for(var spawn:map.escort().respawns())if(spawn.checkpoint()>r.checkpoints.size())throw new IllegalArgumentException("존재하지 않는 체크포인트 부활 설정");
        return r;
    }
    public EscortMatch(MinecraftServer server,ArenaMap map,EscortRoute route){
        this.map=map;this.route=route;level=level(server,map);game=new EscortGame(map.escort(),map.timeLimitSeconds(),route.length,route.checkpoints);
        EscortTracks.get(level).rebuild(level,route);
        for(var p:route.markers){var state=level.getBlockState(p);hidden.put(p,state.getValue(EscortBlocks.Marker.HIDDEN));level.setBlock(p,state.setValue(EscortBlocks.Marker.HIDDEN,true),3);}
        for(var p:route.samples){var cp=new net.minecraft.world.level.ChunkPos(BlockPos.containing(p));if(tickets.add(cp))level.getChunkSource().addRegionTicket(TICKET,cp,2,route.markers.get(0));}
        cart=EscortBlocks.CART.get().create(level);cart.setPos(route.at(0));cart.setYRot((float)Math.toDegrees(Math.atan2(-route.direction(0).x,route.direction(0).z)));level.addFreshEntity(cart);
    }
    public void tick(boolean running){
        if(!running)return;
        int attackers=0;boolean defenders=false;var center=cart.position();
        for(var p:level.players())if(p.isAlive()&&!p.isSpectator()&&!LobbyManager.respawning(p.getUUID())&&p.distanceToSqr(center)<=game.rules.radius()*game.rules.radius()){
            String team=LobbyManager.team(p.getUUID());
            if(team.equals("ALPHA")){attackers++;if(level.getGameTime()%game.rules.healIntervalTicks()==0)p.heal(game.rules.healAmount());}
            if(team.equals("BRAVO"))defenders=true;
        }
        int previousCheckpoint=game.completed;
        game.tick(attackers,defenders);double before=cart.position().distanceToSqr(route.at(game.distance));
        cart.advance(route.at(game.distance),route.direction(game.distance));
        if(before>.000001&&level.getGameTime()%20==0)level.playSound(null,cart.blockPosition(),net.minecraft.sounds.SoundEvents.MINECART_RIDING,net.minecraft.sounds.SoundSource.BLOCKS,.8f,game.motion.equals("REVERSE")?.7f:1f);
        if(game.stopTicks>0&&(game.completed!=previousCheckpoint||level.getGameTime()%4==0))for(var p:level.players())if(Set.of("ALPHA","BRAVO").contains(LobbyManager.team(p.getUUID())))p.displayClientMessage(Component.literal("임시 정차 중 · "+String.format(Locale.ROOT,"%.1f",game.stopTicks/20.0)+"초 뒤 다시 이동 · +"+game.rules.checkpointBonusSeconds()+"초 지급 완료"),true);
        if(!game.winner.isEmpty())LobbyManager.finish(game.winner);
    }
    public ArenaMap.Spawn spawn(String team){
        var result=team.equals("ALPHA")?map.alphaSpawn():map.bravoSpawn();
        for(var r:game.rules.respawns().stream().sorted(Comparator.comparingInt(EscortRules.Respawn::checkpoint)).toList())if(r.checkpoint()<=game.completed){var s=team.equals("ALPHA")?r.alpha():r.bravo();if(s!=null)result=s;}
        return result;
    }
    public View view(){var p=route.at(game.completed<route.checkpoints.size()?route.checkpoints.get(game.completed):route.length);return new View(game.distance,route.length,route.checkpoints,game.completed,game.motion,game.multiplier,game.stopTicks,p.x,p.y,p.z,game.overtime()?game.overtimeTicks:0);}
    public void close(){cart.discard();for(var e:hidden.entrySet()){var s=level.getBlockState(e.getKey());if(s.getBlock() instanceof EscortBlocks.Marker)level.setBlock(e.getKey(),s.setValue(EscortBlocks.Marker.HIDDEN,e.getValue()),3);}for(var cp:tickets)level.getChunkSource().removeRegionTicket(TICKET,cp,2,route.markers.get(0));}
}
