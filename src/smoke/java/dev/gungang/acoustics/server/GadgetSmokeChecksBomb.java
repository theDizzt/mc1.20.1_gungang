package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Opt-in integration checks, excluded from release jars by the GadgetSmokeChecks* rule. */
final class GadgetSmokeChecksBomb {
    private static java.lang.reflect.Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") static void run(MinecraftServer server)throws Exception{
        var level=server.overworld();long oldTime=level.getGameTime();
        var playerList=server.getPlayerList();
        var online=(List<ServerPlayer>)field(net.minecraft.server.players.PlayerList.class,"players").get(playerList);
        var byId=(Map<UUID,ServerPlayer>)field(net.minecraft.server.players.PlayerList.class,"playersByUUID").get(playerList);
        var lobbyPlayers=(Map<UUID,?>)field(LobbyManager.class,"PLAYERS").get(null);
        var oldFiles=field(LobbyManager.class,"files").get(null);var oldSelected=field(LobbyManager.class,"selected").get(null);var oldMode=field(LobbyManager.class,"selectedMode").get(null);
        var players=new ArrayList<ServerPlayer>();
        var saved=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var a=new BombRules.Point(410,240,410);var b=new BombRules.Point(420,240,410);
        try{
            for(var site:List.of(a,b))for(int x=-1;x<=1;x++)for(int y=-1;y<=4;y++)for(int z=-1;z<=1;z++){
                var pos=new BlockPos(site.x()+x,site.y()+y,site.z()+z);level.getChunk(pos);saved.put(pos,level.getBlockState(pos));level.setBlock(pos,y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
            }
            for(int i=0;i<6;i++){
                var p=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(("bomb_smoke_"+i).getBytes()),"BombSmoke"+i)){
                    @Override public void teleportTo(net.minecraft.server.level.ServerLevel target,double x,double y,double z,float yaw,float pitch){setPos(x,y,z);setYRot(yaw);setXRot(pitch);}
                };
                p.setPos(400.5,240,400.5);level.addNewPlayer(p);
                players.add(p);online.add(p);byId.put(p.getUUID(),p);
                LobbyManager.station(p,i<3?StationBlock.Kind.ALPHA:StationBlock.Kind.BRAVO,new BlockPos(400,239,400));
            }
            var rules=new BombRules(5,1,30,new ArenaMap.Spawn(400.5,240,400.5,0,0),new ArenaMap.Area(395,239,395,405,245,405),
                    new ArenaMap.Spawn(430.5,240,400.5,0,0),new ArenaMap.Area(425,239,395,435,245,405),a,b,
                    new BombRules.Site(a.x()-4,a.y()-1,a.z()-2,a.x(),a.y()-1,a.z()+2),
                    new BombRules.Site(b.x()-4,b.y()-1,b.z()-2,b.x(),b.y()-1,b.z()+2));
            check(!rules.pointA().equals(a),"Area center differs from the tested planting position");
            var map=new ArenaMap("bomb_smoke","Bomb smoke","",true,210,"adventure","minecraft:overworld",rules.attackSpawn(),rules.defenseSpawn(),
                    new ArenaMap.Spawn(415,250,400,0,0),rules.attackArea(),rules.defenseArea(),null,List.of(),List.of(),List.of(),null,"","clear","day",rules,
                    List.of(new DeathZone(440,239,440,442,244,442)));
            var files=(ServerFiles.Loaded)oldFiles;
            field(LobbyManager.class,"files").set(null,new ServerFiles.Loaded(files.acoustics(),List.of(map),files.options()));
            field(LobbyManager.class,"selected").set(null,map.id());field(LobbyManager.class,"selectedMode").set(null,"BOMB");
            var prepare=LobbyManager.class.getDeclaredMethod("prepare");prepare.setAccessible(true);prepare.invoke(null);
            var match=MatchManager.bomb();check(match!=null,"Bomb map initializes round controller");
            check(players.stream().noneMatch(p->p.getMainHandItem().getItem() instanceof BombItem||p.getOffhandItem().getItem() instanceof BombItem),"Nobody is issued bomb or defuser items");
            check(Loadouts.load().secondary().stream().noneMatch(BombEquipment::reserved),"Reserved items never appear in weapon selection");
            var attacker=players.stream().filter(p->LobbyManager.team(p.getUUID()).equals(match.game.attacker())).findFirst().orElseThrow();
            var defender=players.stream().filter(p->LobbyManager.team(p.getUUID()).equals(match.game.defender())).findFirst().orElseThrow();
            attacker.setPos(450,240,450);defender.setPos(450,240,450);match.tick();
            check(attacker.getX()<440&&defender.getX()==450,"Preparation confines attackers only");
            for(int i=1;i<600;i++)match.tick();check(match.activePlay(),"Thirty seconds starts round");
            attacker.setPos(415.5,240,410.5);attacker.setOnGround(true);
            check(!match.beginUse(attacker,false),"Cannot plant outside A/B");
            stand(defender,a);check(!match.beginUse(defender,false),"Defenders cannot plant");defender.setPos(450,240,450);
            stand(attacker,a);match.key(attacker,true);check(match.gauge(attacker)!=null,"Any living attacker starts planting with E inside A");
            attacker.setPos(attacker.position().add(.5,0,0));check(!match.continueUse(attacker,false),"Movement cancels plant");
            stand(attacker,a);match.key(attacker,true);advanceUse(match,attacker,20);match.key(attacker,false);
            check(match.gauge(attacker)==null&&!match.game.planted(),"Releasing E cancels incomplete planting");
            match.key(attacker,true);
            advanceUse(match,attacker,99);check(!match.game.planted(),"Plant must not complete before five seconds");
            advanceUse(match,attacker,1);check(match.game.planted()&&level.getBlockState(new BlockPos(a.x(),a.y(),a.z())).is(GimmickBlocks.BOMB.get()),"Five seconds places one bomb block at the feet");
            check(match.view().plantedPosition().equals(a),"Timer and defuse position follow actual placement rather than area center");
            match.key(attacker,false);check(!match.beginUse(attacker,false),"Cannot plant twice");
            var ledger=(MatchLedger)field(MatchManager.class,"ledger").get(null);
            ledger.damage(defender.getUUID(),attacker.getUUID(),10,server.getTickCount());
            attacker.setPos(440.5,240,440.5);
            LobbyManager.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,server));
            check(attacker.isSpectator()&&match.dead(attacker.getUUID()),"Death zone eliminates until next round");
            check(match.view().deadPlayers().contains(attacker.getUUID().toString()),"Client receives eliminated player state");
            var first=attacker.getCamera();
            check(first instanceof ServerPlayer&&first!=attacker&&LobbyManager.team(first.getUUID()).equals(match.game.attacker()),"Eliminated player watches a living teammate");
            match.cycleSpectator(attacker,1);var second=attacker.getCamera();
            check(second!=first&&second!=attacker,"Wheel down switches to next teammate");
            match.cycleSpectator(attacker,-1);check(attacker.getCamera()==first,"Wheel up switches back");
            match.cycleSpectator(attacker,-1);check(attacker.getCamera()==second,"Selection wraps in reverse");
            attacker.setCamera(defender);check(attacker.getCamera()==second,"Server rejects enemy camera requests");
            attacker.setCamera(attacker);match.tick();check(attacker.getCamera()==second,"Cannot leave teammate camera using sneak");
            online.remove(second);byId.remove(second.getUUID());match.tick();
            check(attacker.getCamera()==first,"Disconnected teammate is replaced automatically");
            online.add((ServerPlayer)second);byId.put(second.getUUID(),(ServerPlayer)second);
            match.eliminate((ServerPlayer)first);match.tick();check(attacker.getCamera()==second,"Dead camera target is replaced with the remaining living teammate");
            var defenderCamera=defender.getCamera();match.cycleSpectator(defender,1);check(defender.getCamera()==defenderCamera,"Alive players cannot request spectator cycling");
            check(ledger.rows().stream().filter(r->r.id().equals(defender.getUUID().toString())).findFirst().orElseThrow().kills()==0,"Death zone is suicide even after enemy damage");
            for(var p:players)if(LobbyManager.team(p.getUUID()).equals(match.game.attacker())&&!p.isSpectator())match.eliminate(p);
            match.tick();check(match.activePlay(),"Attack wipe after planting does not end round");
            check(attacker.getCamera()==attacker&&attacker.getY()==map.spectatorSpawn().y(),"Team wipe waits at the fixed spectator spawn");
            aim(defender,a);match.key(defender,true);check(match.gauge(defender)!=null,"Any living defender starts defusing with E on the bomb");
            advanceUse(match,defender,199);check(match.activePlay(),"Defuse must take ten seconds");advanceUse(match,defender,1);match.key(defender,false);
            check(match.game.reason().equals("DEFUSED")&&level.getBlockState(new BlockPos(a.x(),a.y(),a.z())).isAir(),"Defuse awards round and removes bomb");
            String oldAttack=match.game.attacker();
            for(int i=0;i<60;i++)match.tick();
            check(match.game.round()==2&&!match.game.attacker().equals(oldAttack)&&players.stream().noneMatch(ServerPlayer::isSpectator),"Next round revives and swaps roles");
            check(players.stream().allMatch(p->p.getCamera()==p),"Next round restores all player cameras");
            for(int i=0;i<600;i++)match.tick();
            var fallen=players.stream().filter(p->!p.isSpectator()&&LobbyManager.team(p.getUUID()).equals(match.game.attacker())).findFirst().orElseThrow();
            fallen.setPos(415.5,240,415.5);match.eliminate(fallen);match.tick();
            boolean dropped=false;for(var e:level.getAllEntities())if(e instanceof net.minecraft.world.entity.item.ItemEntity item&&!item.isRemoved()&&item.getItem().getItem() instanceof BombItem)dropped=true;
            check(!dropped,"Death drops no objective");
            var planter=players.stream().filter(p->!p.isSpectator()&&LobbyManager.team(p.getUUID()).equals(match.game.attacker())).findFirst().orElseThrow();
            stand(planter,b);match.key(planter,true);advanceUse(match,planter,100);match.key(planter,false);
            check(match.game.planted()&&"B".equals(match.view().plantedSite()),"Another living attacker plants at B");
            var health=new HashMap<UUID,Float>();players.forEach(p->health.put(p.getUUID(),p.getHealth()));
            for(int i=0;i<900;i++)match.tick();
            check(match.game.reason().equals("EXPLODED"),"Bomb fuse explodes after forty-five seconds");
            check(players.stream().allMatch(p->p.getHealth()==health.get(p.getUUID())),"Objective explosion never damages players");
            var endDrop=looseDrop(level);check(!endDrop.isRemoved(),"Cleanup fixture exists");
            LobbyManager.finish("ABORTED");
            check(endDrop.isRemoved(),"Game end removes objective drops");
            for(int i=0;i<players.size();i++)LobbyManager.station(players.get(i),i<3?StationBlock.Kind.ALPHA:StationBlock.Kind.BRAVO,new BlockPos(400,239,400));
            var startDrop=looseDrop(level);
            field(LobbyManager.class,"selectedMode").set(null,"DEATHMATCH");prepare.invoke(null);
            check(startDrop.isRemoved(),"New game preparation clears objective drops across modes");
            check(MatchManager.bomb()==null,"Bomb map in deathmatch has no bomb rules");
            check(players.get(0).getX()==rules.attackSpawn().x()&&players.get(3).getX()==rules.defenseSpawn().x(),"Deathmatch maps attack/defense spawns to ALPHA/BRAVO");
            field(LobbyManager.class,"deadline").setInt(null,0);
            LobbyManager.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,server));
            players.get(0).setPos(440.5,240,440.5);
            LobbyManager.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,server));
            check(LobbyManager.respawning(players.get(0).getUUID()),"Deathmatch death zone schedules ordinary respawn");
            var tdmLedger=(MatchLedger)field(MatchManager.class,"ledger").get(null);
            check(tdmLedger.kills("ALPHA")==0&&tdmLedger.kills("BRAVO")==0,"Deathmatch death zone cannot change team score");
            LobbyManager.finish("ABORTED");var reloadDrop=looseDrop(level);
            check(server.getCommands().getDispatcher().execute("gungang reload",server.createCommandSourceStack())==1&&reloadDrop.isRemoved(),"Reload immediately clears objective drops");
            System.out.println("GUNGANG_BOMB_SMOKE_PASS roles=swap respawn=round-only sites=A,B plant=5s(E,anyone) defuse=10s(E,anyone) fuse=45s deathzone=suicide spectator=team-cycle,disconnect,enemy-reject,round-reset");
        }finally{
            LobbyManager.finish("ABORTED");
            for(var p:players){online.remove(p);byId.remove(p.getUUID());lobbyPlayers.remove(p.getUUID());level.removePlayerImmediately(p,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);}
            field(LobbyManager.class,"files").set(null,oldFiles);field(LobbyManager.class,"selected").set(null,oldSelected);field(LobbyManager.class,"selectedMode").set(null,oldMode);
            saved.forEach((pos,state)->level.setBlock(pos,state,3));server.getWorldData().overworldData().setGameTime(oldTime);
        }
    }
    private static net.minecraft.world.entity.item.ItemEntity looseDrop(net.minecraft.server.level.ServerLevel level){
        var item=new net.minecraft.world.entity.item.ItemEntity(level,400,240,400,new ItemStack(GadgetItems.BOMB.get()));level.addFreshEntity(item);return item;
    }
    /** Looking straight down at the cell from above, for defusing. */
    private static void aim(ServerPlayer p,BombRules.Point point){p.setPos(point.x()+.5,point.y()+1,point.z()+.5);p.setXRot(90);p.setYRot(0);}
    /** Feet in the cell, standing on the floor below it, for planting. */
    private static void stand(ServerPlayer p,BombRules.Point point){p.setPos(point.x()+.5,point.y(),point.z()+.5);p.setOnGround(true);}
    private static void advanceUse(BombMatch match,ServerPlayer p,int ticks){
        for(int i=0;i<ticks;i++){
            p.server.getWorldData().overworldData().setGameTime(p.level().getGameTime()+1);
            match.tick();
        }
    }
}
