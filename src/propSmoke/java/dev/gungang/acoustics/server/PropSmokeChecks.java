package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/** Isolated opt-in server integration test; never included in the exported mod. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class PropSmokeChecks {
    private static java.lang.reflect.Field field(Class<?> c,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @SubscribeEvent public static void started(ServerStartedEvent e){e.getServer().execute(()->{try{run(e.getServer());System.out.println("GUNGANG_PROP_SMOKE_PASS");}catch(Throwable t){t.printStackTrace();System.out.println("GUNGANG_PROP_SMOKE_FAIL");}finally{e.getServer().halt(false);}});}
    @SuppressWarnings("unchecked") private static void run(net.minecraft.server.MinecraftServer server)throws Exception{
        var level=server.overworld();var random=new Random(5);
        for(String type:PropCatalog.available()){
            var prop=PropEntity.TYPE.get().create(level);prop.setPos(0.5,100,0.5);prop.setup(UUID.randomUUID(),PropCatalog.variant(type,random),0);
            for(int i=0;i<4;i++){prop.rotate();check(prop.getBoundingBox().getSize()>0,"Nonempty hitbox: "+type);}
            prop.discard();
        }
        check(PropCatalog.available().stream().filter(s->s.equals("iron_block")).count()==1,"Iron is one deck entry");
        var online=(List<ServerPlayer>)field(net.minecraft.server.players.PlayerList.class,"players").get(server.getPlayerList());
        var byId=(Map<UUID,ServerPlayer>)field(net.minecraft.server.players.PlayerList.class,"playersByUUID").get(server.getPlayerList());
        var a=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PropAlpha")){
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
        };
        var b=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PropBravo")){
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
        };
        var entryClass=Class.forName(LobbyManager.class.getName()+"$Entry");var ctor=entryClass.getDeclaredConstructor(ServerPlayer.class);ctor.setAccessible(true);
        var lobby=(Map<UUID,Object>)field(LobbyManager.class,"PLAYERS").get(null);
        for(var p:List.of(a,b)){p.setPos(p==a?.5:40.5,100,.5);level.addNewPlayer(p);online.add(p);byId.put(p.getUUID(),p);var entry=ctor.newInstance(p);field(entryClass,"team").set(entry,p==a?"ALPHA":"BRAVO");lobby.put(p.getUUID(),entry);}
        var gun=new Loadouts.Weapon("test_shotgun","{id:\"tacz:modern_kinetic_gun\",Count:1b,tag:{GunId:\"tacz:m1014\",GunCurrentAmmoCount:6,HasBulletInBarrel:1b,GunFireMode:\"SEMI\"}}","M1014","",new Loadouts.Specs("","","","",""),"");
        if(!net.minecraftforge.fml.ModList.get().isLoaded("tacz"))gun=new Loadouts.Weapon("test_shotgun","{id:\"minecraft:bow\",Count:1b}","Test bow","",null,"");
        options(a,b,lobby);
        var map=PropHuntRules.example();var loadouts=new Loadouts(List.of(gun),List.of(),null);
        if(net.minecraftforge.fml.ModList.get().isLoaded("tacz"))PropHuntMatch.validate(server,map,loadouts);
        var gatePos=new net.minecraft.core.BlockPos(5,110,5);level.setBlock(gatePos,StationBlocks.ALPHA_GATE.get().defaultBlockState(),3);
        var through=new net.minecraft.world.phys.Vec3(5.5,110.5,3.5);var beyond=new net.minecraft.world.phys.Vec3(5.5,110.5,7.5);
        check(!level.getBlockState(gatePos).getCollisionShape(level,gatePos).isEmpty()&&SpawnGateProtection.crosses(level,through,beyond),"Outside prop hunt the gate stops bullets");
        RecoveryImpulseChecks.run(server,a);
        var match=new PropHuntMatch(server,map,gun);field(MatchManager.class,"propHunt").set(null,match);
        check(level.getBlockState(gatePos).getCollisionShape(level,gatePos).isEmpty()&&level.getBlockState(gatePos).getShape(level,gatePos).isEmpty()&&!SpawnGateProtection.crosses(level,through,beyond),"Prop hunt gate lets bullets and swings through");
        check(level.clip(new net.minecraft.world.level.ClipContext(through,beyond,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,a)).getType()==net.minecraft.world.phys.HitResult.Type.MISS,"Bullet ray passes the prop hunt gate");
        level.setBlock(gatePos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        try{
            match.equip(a);if(net.minecraftforge.fml.ModList.get().isLoaded("tacz"))match.equip(b);
            check(a.getMaxHealth()==(match.prop(a.getUUID()).item().equals("gungang:mannequin")?255:50),"Hider health follows disguise");check(!a.isPickable(),"Invisible player cannot absorb bullets");
            check(match.prop(a.getUUID()).isPickable(),"Prop is bullet target");
            check(StationBlocks.ALPHA_GATE.get().allows(b)&&StationBlocks.BRAVO_GATE.get().allows(a),"Both teams pass both gates");
            match.action(a,"lock");check(match.frozen(a.getUUID()),"T fixes hider");
            check(match.frozen(b.getUUID()),"Hunters held during hide phase");
            var hiddenAt=match.prop(a.getUUID()).position();
            match.action(a,"next");check(a.isSpectator()&&a.getCamera()==b,"Fixed hider can watch hunter through server camera");
            match.tick();check(match.prop(a.getUUID()).position().distanceToSqr(hiddenAt)<.001,"Watching never moves the real disguise");
            match.action(a,"home");check(!a.isSpectator()&&a.getCamera()==a&&a.position().distanceToSqr(hiddenAt)<.001,"R returns to real hiding place without unlocking");
            var nest=match.prop(a.getUUID());nest.setup(a.getUUID(),"minecraft:bee_nest",0);match.abilities.initialize(a,nest);match.abilities.tick(a,nest);
            var bees=level.getEntitiesOfClass(net.minecraft.world.entity.animal.Bee.class,nest.getBoundingBox().inflate(5));check(bees.size()==3,"Bees exist during hiding without active seekers");
            var beeAt=bees.get(0).position();match.abilities.tick(a,nest);check(!bees.get(0).isRemoved()&&bees.get(0).position().distanceToSqr(beeAt)>0,"Idle bees orbit during hiding");
            for(int i=0;i<PropHuntGame.HIDE;i++)match.game.tick();
            check(!match.frozen(b.getUUID()),"Hunters released after one minute");
            match.eliminate(b,null);check(!b.isSpectator()&&match.frozen(b.getUUID()),"Hunter death uses stationary waiting, not spectator");
            var members=(Map<?,?>)field(PropHuntMatch.class,"members").get(match);var bm=members.get(b.getUUID());field(bm.getClass(),"waiting").setInt(bm,0);
            PropExpansionChecks.run(server,match,a,b);
            var prop=match.prop(a.getUUID());check(!match.hit(prop,level.damageSources().playerAttack(b),50),"Melee cannot find props");
            if(net.minecraftforge.fml.ModList.get().isLoaded("tacz")){
                var awn=b.getOffhandItem();check(PropAwn.is(awn)&&awn.getTag().getInt("GunCurrentAmmoCount")==0&&awn.getTag().getBoolean("HasBulletInBarrel"),"AWN has exactly one chambered round in offhand");
                var reloadType=Class.forName("com.tacz.guns.api.event.common.GunReloadEvent");
                var reload=(net.minecraftforge.eventbus.api.Event)reloadType.getConstructor(net.minecraft.world.entity.LivingEntity.class,net.minecraft.world.item.ItemStack.class,net.minecraftforge.fml.LogicalSide.class).newInstance(b,awn,net.minecraftforge.fml.LogicalSide.SERVER);
                check(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(reload),"Ordinary TaCZ reload cannot bypass AWN timer");
                var fireType=Class.forName("com.tacz.guns.api.event.common.GunFireEvent");
                var fire=(net.minecraftforge.eventbus.api.Event)fireType.getConstructor(net.minecraft.world.entity.LivingEntity.class,net.minecraft.world.item.ItemStack.class,net.minecraftforge.fml.LogicalSide.class).newInstance(b,awn,net.minecraftforge.fml.LogicalSide.SERVER);
                check(!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(fire),"First AWN shot is allowed");PropAwn.tick(b,awn);
                check(!awn.getTag().getBoolean("HasBulletInBarrel")&&SecondaryRuntime.remaining(b,awn)==600,"AWN empty for 30 seconds after firing");
                var again=(net.minecraftforge.eventbus.api.Event)fireType.getConstructor(net.minecraft.world.entity.LivingEntity.class,net.minecraft.world.item.ItemStack.class,net.minecraftforge.fml.LogicalSide.class).newInstance(b,awn,net.minecraftforge.fml.LogicalSide.SERVER);
                check(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(again),"AWN cannot fire twice during reload");
                long gameTime=level.getGameTime();
                try{
                    server.getWorldData().overworldData().setGameTime(gameTime+599);PropAwn.tick(b,awn);check(!awn.getTag().getBoolean("HasBulletInBarrel"),"No early AWN refill");
                    server.getWorldData().overworldData().setGameTime(gameTime+600);PropAwn.tick(b,awn);check(awn.getTag().getBoolean("HasBulletInBarrel")&&awn.getTag().getInt("GunCurrentAmmoCount")==0,"One round restored at exactly 600 ticks");
                    var shotgun=b.getMainHandItem();b.getInventory().setItem(0,awn);b.getInventory().setItem(40,shotgun);match.tick();
                    check(b.getMainHandItem()==awn&&b.getOffhandItem()==shotgun,"Hand swap preserved for AWN and shotgun");
                }finally{server.getWorldData().overworldData().setGameTime(gameTime);}
                var type=Class.forName("com.tacz.guns.entity.EntityKineticBullet");var bullet=(net.minecraft.world.entity.projectile.Projectile)type.getConstructor(net.minecraft.world.entity.EntityType.class,net.minecraft.world.level.Level.class).newInstance(type.getField("TYPE").get(null),level);bullet.setOwner(b);
                check(match.hit(prop,level.damageSources().thrown(bullet,b),20),"Real TaCZ bullet damages prop");check(a.getHealth()==30,"Hider receives prop damage");
            }
            match.eliminate(a,null);match.tick();check(prop.marker()&&prop.hasCustomName(),"Death leaves glowing named prop");check(a.isSpectator()&&b.isSpectator(),"Review grants free spectator to both teams");
            check(match.game.phase()==PropHuntGame.Phase.REVIEW,"No immediate results");
        }finally{match.close();field(MatchManager.class,"propHunt").set(null,null);for(var p:List.of(a,b)){online.remove(p);byId.remove(p.getUUID());lobby.remove(p.getUUID());level.removePlayerImmediately(p,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);}}
        check(!a.isInvisible()&&a.getMaxHealth()==20,"Restore original attributes");
    }
    @SuppressWarnings("unchecked") private static void options(ServerPlayer a,ServerPlayer b,Map<UUID,Object> lobby)throws Exception{
        var saved=new HashMap<String,Object>();for(String name:List.of("files","loadouts","enabled","selectedMode","phase"))saved.put(name,field(LobbyManager.class,name).get(null));
        var choices=(Map<UUID,String[]>)field(LobbyManager.class,"FREE_SELECTION").get(null);
        var oldA=choices.get(a.getUUID());var oldB=choices.get(b.getUUID());var entry=lobby.get(b.getUUID());var team=field(entry.getClass(),"team");var oldTeam=team.get(entry);
        try{
            var one=new Loadouts.Weapon("one","{id:\"minecraft:bow\",Count:1b}","One","",null,"");var two=new Loadouts.Weapon("two","{id:\"minecraft:bow\",Count:1b}","Two","",null,"");
            field(LobbyManager.class,"loadouts").set(null,new Loadouts(List.of(one,two),List.of(one,two),null));
            field(LobbyManager.class,"enabled").set(null,true);field(LobbyManager.class,"phase").set(null,"LOBBY");field(LobbyManager.class,"selectedMode").set(null,"DEATHMATCH");team.set(entry,"ALPHA");
            choices.put(a.getUUID(),new String[]{"one","one"});choices.put(b.getUUID(),new String[]{"one","one"});
            var files=new ServerFiles.Loaded(null,List.of(),TeamOptions.defaults());field(LobbyManager.class,"files").set(null,files);
            check(LobbyManager.teamWeaponTaken(a.getUUID(),"ALPHA",true,"one"),"Default rejects teammate primary duplicate");
            check(!LobbyManager.teamWeaponTaken(a.getUUID(),"ALPHA",false,"one"),"Default permits secondary duplicate");
            var allocate=LobbyManager.class.getDeclaredMethod("allocateLoadouts");allocate.setAccessible(true);
            var result=(Map<UUID,String[]>)allocate.invoke(null);
            check(!result.get(a.getUUID())[0].equals(result.get(b.getUUID())[0])&&result.get(a.getUUID())[1].equals(result.get(b.getUUID())[1]),"Starting duplicate primaries assigned distinct weapons, secondaries shared");
            check(choices.get(b.getUUID())[0].equals("one"),"Allocation does not mutate saved free selection");
            team.set(entry,"BRAVO");check(!LobbyManager.teamWeaponTaken(a.getUUID(),"ALPHA",true,"one"),"Opponents may share weapons");team.set(entry,"ALPHA");
            var custom=new TeamOptions(null,null,5,null,null,new GameOptions(0,0,true,false));field(LobbyManager.class,"files").set(null,new ServerFiles.Loaded(null,List.of(),custom));
            check(!LobbyManager.teamWeaponTaken(a.getUUID(),"ALPHA",true,"one")&&LobbyManager.teamWeaponTaken(a.getUUID(),"ALPHA",false,"one"),"Both duplicate toggles enforced independently");
            field(LobbyManager.class,"selectedMode").set(null,"FREE_FOR_ALL");check(!LobbyManager.teamWeaponTaken(a.getUUID(),"ALPHA",false,"one"),"Individual mode has no team duplicate restriction");
            System.out.println("GUNGANG_GAME_OPTIONS_PASS defaults toggles allocation opposing_teams individual_mode");
        }finally{
            for(var row:saved.entrySet())field(LobbyManager.class,row.getKey()).set(null,row.getValue());team.set(entry,oldTeam);
            if(oldA==null)choices.remove(a.getUUID());else choices.put(a.getUUID(),oldA);if(oldB==null)choices.remove(b.getUUID());else choices.put(b.getUUID(),oldB);
        }
    }
}
