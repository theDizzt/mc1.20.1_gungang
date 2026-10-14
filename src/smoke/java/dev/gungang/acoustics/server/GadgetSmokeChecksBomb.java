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
            check(players.stream().filter(p->p.getOffhandItem().is(GadgetItems.BOMB.get())).count()==1,"Exactly one attacker receives bomb");
            check(players.stream().filter(p->p.getOffhandItem().is(GadgetItems.DEFUSER.get())).count()==1,"Exactly one defender receives defuser");
            check(Loadouts.load().secondary().stream().noneMatch(BombEquipment::reserved),"Reserved items never appear in weapon selection");
            var attacker=players.stream().filter(p->p.getOffhandItem().is(GadgetItems.BOMB.get())).findFirst().orElseThrow();
            var defender=players.stream().filter(p->p.getOffhandItem().is(GadgetItems.DEFUSER.get())).findFirst().orElseThrow();
            attacker.setPos(450,240,450);defender.setPos(450,240,450);match.tick();
            check(attacker.getX()<440&&defender.getX()==450,"Preparation confines attackers only");
            for(int i=1;i<600;i++)match.tick();check(match.activePlay(),"Thirty seconds starts round");
            swap(attacker);attacker.setPos(415.5,241,410.5);attacker.setXRot(90);
            check(!match.beginUse(attacker,false),"Cannot plant outside A/B");
            aim(attacker,a);check(match.beginUse(attacker,false),"Can start planting at A");attacker.startUsingItem(InteractionHand.MAIN_HAND);
            attacker.setPos(attacker.position().add(.5,0,0));check(!match.continueUse(attacker,false),"Movement cancels plant");
            attacker.stopUsingItem();aim(attacker,a);
            check(attacker.getMainHandItem().use(level,attacker,InteractionHand.MAIN_HAND).getResult().consumesAction(),"Bomb item right click starts use");
            advanceUse(match,attacker,79);check(!match.game.planted(),"Plant must not complete before four seconds");
            advanceUse(match,attacker,1);check(match.game.planted()&&level.getBlockState(new BlockPos(a.x(),a.y(),a.z())).is(GimmickBlocks.BOMB.get()),"Four seconds places one bomb block");
            check(match.view().plantedPosition().equals(a),"Timer and defuse position follow actual placement rather than area center");
            attacker.stopUsingItem();check(!match.beginUse(attacker,false),"Cannot plant twice");
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
            swap(defender);aim(defender,a);check(defender.getMainHandItem().use(level,defender,InteractionHand.MAIN_HAND).getResult().consumesAction(),"Defuser starts on planted bomb");
            advanceUse(match,defender,139);check(match.activePlay(),"Defuse must take seven seconds");advanceUse(match,defender,1);defender.stopUsingItem();
            check(match.game.reason().equals("DEFUSED")&&level.getBlockState(new BlockPos(a.x(),a.y(),a.z())).isAir(),"Defuse awards round and removes bomb");
            String oldAttack=match.game.attacker();
            for(int i=0;i<60;i++)match.tick();
            check(match.game.round()==2&&!match.game.attacker().equals(oldAttack)&&players.stream().noneMatch(ServerPlayer::isSpectator),"Next round revives and swaps roles");
            check(players.stream().allMatch(p->p.getCamera()==p),"Next round restores all player cameras");
            for(int i=0;i<600;i++)match.tick();
            var nextBomber=players.stream().filter(p->p.getOffhandItem().is(GadgetItems.BOMB.get())).findFirst().orElseThrow();
            match.eliminate(nextBomber);match.tick();
            check(players.stream().anyMatch(p->!p.isSpectator()&&p.getOffhandItem().is(GadgetItems.BOMB.get())),"Carrier death transfers bomb to surviving teammate");
            nextBomber=players.stream().filter(p->!p.isSpectator()&&p.getOffhandItem().is(GadgetItems.BOMB.get())).findFirst().orElseThrow();
            swap(nextBomber);aim(nextBomber,b);check(nextBomber.getMainHandItem().use(level,nextBomber,InteractionHand.MAIN_HAND).getResult().consumesAction(),"B also allows planting");advanceUse(match,nextBomber,80);nextBomber.stopUsingItem();
            var health=new HashMap<UUID,Float>();players.forEach(p->health.put(p.getUUID(),p.getHealth()));
            for(int i=0;i<900;i++)match.tick();
            check(match.game.reason().equals("EXPLODED"),"Bomb fuse explodes after forty-five seconds");
            check(players.stream().allMatch(p->p.getHealth()==health.get(p.getUUID())),"Objective explosion never damages players");
            LobbyManager.finish("ABORTED");
            for(int i=0;i<players.size();i++)LobbyManager.station(players.get(i),i<3?StationBlock.Kind.ALPHA:StationBlock.Kind.BRAVO,new BlockPos(400,239,400));
            field(LobbyManager.class,"selectedMode").set(null,"DEATHMATCH");prepare.invoke(null);
            check(MatchManager.bomb()==null,"Bomb map in deathmatch has no bomb rules");
            check(players.get(0).getX()==rules.attackSpawn().x()&&players.get(3).getX()==rules.defenseSpawn().x(),"Deathmatch maps attack/defense spawns to ALPHA/BRAVO");
            field(LobbyManager.class,"deadline").setInt(null,0);
            LobbyManager.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,server));
            players.get(0).setPos(440.5,240,440.5);
            LobbyManager.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,server));
            check(LobbyManager.respawning(players.get(0).getUUID()),"Deathmatch death zone schedules ordinary respawn");
            var tdmLedger=(MatchLedger)field(MatchManager.class,"ledger").get(null);
            check(tdmLedger.kills("ALPHA")==0&&tdmLedger.kills("BRAVO")==0,"Deathmatch death zone cannot change team score");
            System.out.println("GUNGANG_BOMB_SMOKE_PASS roles=swap respawn=round-only sites=A,B plant=4s defuse=7s fuse=45s deathzone=suicide spectator=team-cycle,disconnect,enemy-reject,round-reset");
        }finally{
            LobbyManager.finish("ABORTED");
            for(var p:players){online.remove(p);byId.remove(p.getUUID());lobbyPlayers.remove(p.getUUID());level.removePlayerImmediately(p,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);}
            field(LobbyManager.class,"files").set(null,oldFiles);field(LobbyManager.class,"selected").set(null,oldSelected);field(LobbyManager.class,"selectedMode").set(null,oldMode);
            saved.forEach((pos,state)->level.setBlock(pos,state,3));server.getWorldData().overworldData().setGameTime(oldTime);
        }
    }
    private static void swap(ServerPlayer p){var main=p.getMainHandItem();p.setItemInHand(InteractionHand.MAIN_HAND,p.getOffhandItem());p.setItemInHand(InteractionHand.OFF_HAND,main);}
    private static void aim(ServerPlayer p,BombRules.Point point){p.setPos(point.x()+.5,point.y()+1,point.z()+.5);p.setXRot(90);p.setYRot(0);}
    private static void advanceUse(BombMatch match,ServerPlayer p,int ticks){
        for(int i=0;i<ticks;i++){
            p.server.getWorldData().overworldData().setGameTime(p.level().getGameTime()+1);
            var stack=p.getMainHandItem();if(stack.getItem() instanceof BombItem item)item.onUseTick(p.level(),p,stack,72000-i);
            match.tick();
        }
    }
}
