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
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other){return true;}
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
        var skinRow=new MatchSnapshot.Row(BotDatabase.id(0).toString(),"Skin bot","ALPHA",9,2,3,40,50,9,0x123456,4,5,"file:smoke.png");
        var mappedSkin=match.rows(List.of(skinRow)).get(0);
        check(mappedSkin.skin().equals(skinRow.skin())&&mappedSkin.color()==skinRow.color(),"Prop hunt rows preserve bot skin and display metadata");
        check(ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(mappedSkin),MatchSnapshot.Row.class).skin().equals(skinRow.skin()),"Bot skin survives prop hunt snapshot serialization");
        System.out.println("PROP_BOT_SKIN_SNAPSHOT_PASS");
        check(level.getBlockState(gatePos).getCollisionShape(level,gatePos).isEmpty()&&level.getBlockState(gatePos).getShape(level,gatePos).isEmpty()&&!SpawnGateProtection.crosses(level,through,beyond),"Prop hunt gate lets bullets and swings through");
        check(level.clip(new net.minecraft.world.level.ClipContext(through,beyond,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,a)).getType()==net.minecraft.world.phys.HitResult.Type.MISS,"Bullet ray passes the prop hunt gate");
        level.setBlock(gatePos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        try{
            match.equip(a);if(net.minecraftforge.fml.ModList.get().isLoaded("tacz"))match.equip(b);
            match.action(a,"pick1"); // Initial disguise now waits for the player's three-block choice.
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
            var bees=level.getEntitiesOfClass(PropSummonEntity.class,nest.getBoundingBox().inflate(5),e->e.kind()==PropSummonEntity.Kind.BEE);check(bees.size()==3,"Bees exist during hiding without active seekers");
            var beeAt=bees.get(0).position();match.abilities.tick(a,nest);check(!bees.get(0).isRemoved()&&bees.get(0).position().distanceToSqr(beeAt)>0,"Idle bees orbit during hiding");
            for(int i=0;i<PropHuntGame.HIDE;i++)match.game.tick();
            check(!match.frozen(b.getUUID()),"Hunters released after one minute");
            match.eliminate(b,null);check(!b.isSpectator()&&match.frozen(b.getUUID()),"Hunter death uses stationary waiting, not spectator");
            var members=(Map<?,?>)field(PropHuntMatch.class,"members").get(match);var bm=members.get(b.getUUID());field(bm.getClass(),"waiting").setInt(bm,0);
            PropExpansionChecks.run(server,match,a,b);
            BotBehaviourChecks.run(match,b);
            var prop=match.prop(a.getUUID());check(!match.hit(prop,level.damageSources().playerAttack(b),50),"Melee cannot find props");
            if(net.minecraftforge.fml.ModList.get().isLoaded("tacz")){
                PropBulletChecks.run(match,a,b);
                var jet=b.getOffhandItem();check(PropAwn.jetpack(jet)&&jet.getTag().getBoolean("GunGangJetpackVerticalFlight")&&!PropAwn.is(jet),"Seeker gets the vertical prop jetpack instead of the AWN");
                check(java.util.stream.IntStream.range(0,b.getInventory().getContainerSize()).noneMatch(i->PropAwn.is(b.getInventory().getItem(i))),"No AWN anywhere in the seeker inventory");
                var shotgun=b.getMainHandItem();b.getInventory().setItem(0,jet);b.getInventory().setItem(40,shotgun);match.tick();
                check(b.getMainHandItem()==jet&&b.getOffhandItem()==shotgun,"Hand swap preserved for jetpack and shotgun");
                b.getInventory().setItem(0,shotgun);b.getInventory().setItem(40,jet);
                b.setXRot(-90);GadgetManager.use(b,jet,"jetpack");check(SurvivalGadgets.jetting(b),"Prop jetpack starts while hunting");
                var tick=new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,b);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(tick);check(b.getDeltaMovement().y>.5,"Prop jetpack flies straight up when looking up, y="+b.getDeltaMovement().y);
                SurvivalGadgets.clear(b);b.setXRot(0);
                a.getFoodData().setFoodLevel(20);a.getFoodData().setSaturation(5);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,a));
                check(a.getFoodData().getFoodLevel()==SprintStamina.FOOD&&a.getFoodData().getSaturationLevel()==0,"Food pinned at 17 with no saturation: sprint yes, hunger healing no");
                float before=a.getHealth();a.setHealth(before-10);
                var ticks=field(net.minecraft.server.MinecraftServer.class,"tickCount");int savedTicks=ticks.getInt(server);
                for(int i=0;i<400;i++){net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,a));ticks.setInt(server,savedTicks+i+1);}
                ticks.setInt(server,savedTicks);
                check(a.getHealth()==before-10,"Hider never regenerates out of combat, health="+a.getHealth());a.setHealth(before);
                var type=Class.forName("com.tacz.guns.entity.EntityKineticBullet");var bullet=(net.minecraft.world.entity.projectile.Projectile)type.getConstructor(net.minecraft.world.entity.EntityType.class,net.minecraft.world.level.Level.class).newInstance(type.getField("TYPE").get(null),level);bullet.setOwner(b);
                a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(255);a.setHealth(255);
                check(match.hit(prop,level.damageSources().thrown(bullet,b),20),"Real TaCZ bullet damages prop");check(Math.abs(a.getHealth()-(255-match.pelletDamage()))<1e-3,"Each seeker pellet deals 100 / pellets, health="+a.getHealth());check(match.pelletDamage()*PropHuntMatch.pellets(gun.stack())==100,"A full volley deals 100");
                a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(50);a.setHealth(50);
            }
            match.eliminate(a,null);match.tick();check(prop.marker()&&prop.hasCustomName(),"Death leaves glowing named prop");check(a.isSpectator()&&b.isSpectator(),"Review grants free spectator to both teams");
            check(match.game.phase()==PropHuntGame.Phase.REVIEW,"No immediate results");
            field(MatchManager.class,"propHunt").set(null,null);
            PayloadDamageChecks.run(b);
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
