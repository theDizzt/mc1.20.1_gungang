package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;

/** Live navigation, line-of-sight and equipment regressions in the isolated prop-hunt world. */
final class BotBehaviourChecks {
    private static java.lang.reflect.Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void run(PropHuntMatch hunt,net.minecraft.server.level.ServerPlayer seeker)throws Exception{
        var level=seeker.serverLevel();var savedArena=field(BotManager.class,"arena").get(null);var savedServer=field(BotManager.class,"server").get(null);
        var savedSeeker=seeker.position();var bot=BotEntity.TYPE.get().create(level);var prop=PropEntity.TYPE.get().create(level);
        var origin=new Vec3(4.5,121,8.5);var agent=new BotAgent(0,"ALPHA");agent.bot=bot;bot.setUUID(agent.id);bot.setPos(origin);bot.setOnGround(true);
        var states=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        try{
            for(var p:BlockPos.betweenClosed(0,120,0,96,123,16)){states.put(p.immutable(),level.getBlockState(p));level.setBlock(p,p.getY()==120?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
            var json=ServerFiles.GSON.toJsonTree(PropHuntRules.example()).getAsJsonObject();json.addProperty("id","bot_behaviour_checks");
            json.add("alphaSpawn",ServerFiles.GSON.toJsonTree(new ArenaMap.Spawn(4.5,121,8.5,0,0)));
            json.add("bravoSpawn",ServerFiles.GSON.toJsonTree(new ArenaMap.Spawn(8.5,121,8.5,0,0)));
            json.add("previewArea",ServerFiles.GSON.toJsonTree(new MapPreviewArea(0,120,0,96,123,16)));
            var arena=ServerFiles.GSON.fromJson(json,ArenaMap.class);field(BotManager.class,"arena").set(null,arena);field(BotManager.class,"server").set(null,level.getServer());
            prop.setPos(origin);prop.setup(agent.id,"minecraft:stone",0);level.addFreshEntity(prop);bot.disguise(prop);
            BotNavGraph graph=null;
            for(int i=0;i<200&&graph==null;i++){BotNavGraph.tick(level,arena);graph=BotNavGraph.of(arena,level);}
            check(graph!=null,"Whole-map graph finishes");
            var random=new Random(7);boolean far=false;
            for(int i=0;i<40;i++){var p=graph.roamSpot(origin,random);if(p!=null&&p.getX()>64)far=true;}
            check(far,"Whole-map samples reach beyond the old 32-block spawn radius");
            // A doubtful block beyond the old eight-block gate must be shot at while approaching.
            var suspect=Class.forName(BotPropHunt.class.getName()+"$Suspect");var ctor=suspect.getDeclaredConstructors()[0];ctor.setAccessible(true);
            var farPoint=new Vec3(25.5,121.5,8.5);var seekerAgent=new BotAgent(3,"BRAVO");seekerAgent.bot=bot;
            field(BotPropHunt.class,"aiming").set(seekerAgent.propHunt,ctor.newInstance(farPoint,null,BlockPos.containing(farPoint),1.0));
            field(BotPropHunt.class,"aimSince").setInt(seekerAgent.propHunt,1000);
            field(BotPropHunt.class,"hesitation").setInt(seekerAgent.propHunt,0);
            var seek=BotPropHunt.class.getDeclaredMethod("seek",BotAgent.class,PropHuntMatch.class,int.class);seek.setAccessible(true);
            for(int t=1001;t<1040&&!field(BotPropHunt.class,"shotPending").getBoolean(seekerAgent.propHunt);t++){
                BotMovement.newTick();seek.invoke(seekerAgent.propHunt,seekerAgent,hunt,t);
            }
            check(field(BotPropHunt.class,"shotPending").getBoolean(seekerAgent.propHunt),"Distant visible block reaches firing decision");
            check(!bot.getNavigation().isDone(),"Long-range firing leaves pursuit path active");
            bot.getNavigation().stop();
            var mind=agent.propHunt;mind.reset(agent,hunt);((Random)field(BotPropHunt.class,"random").get(mind)).setSeed(7);
            var search=BotPropHunt.class.getDeclaredMethod("search",BotAgent.class,PropHuntMatch.class,PropEntity.class,int.class);search.setAccessible(true);
            BotMovement.newTick();search.invoke(mind,agent,hunt,prop,1000);
            var spot=(BlockPos)field(BotPropHunt.class,"spot").get(mind);
            check(spot!=null&&spot.distToCenterSqr(origin)>32*32,"Hider chooses a distant reachable hiding spot");
            seeker.setPos(14.5,121,8.5);field(BotPropHunt.class,"nextPerceive").setInt(mind,0);BotMovement.newTick();
            mind.tick(agent,hunt,1005);
            check(field(BotPropHunt.class,"state").get(mind).toString().equals("FLEE"),"Visible seeker immediately starts escape instead of freezing");
            var exposed=BotPropHunt.class.getDeclaredMethod("exposed",BlockPos.class,int.class);exposed.setAccessible(true);
            check((boolean)exposed.invoke(mind,spot,1006),"Exposed destination cannot be selected again immediately");
            check(!(boolean)exposed.invoke(mind,spot,1606),"Exposed destination becomes available after thirty seconds");
            field(BotPropHunt.class,"fleeUntil").setInt(mind,1006);BotMovement.newTick();mind.tick(agent,hunt,1010);
            check(field(BotPropHunt.class,"state").get(mind).toString().equals("FLEE"),"Visible pursuer cannot time out escape");
            var ownId=bot.getUUID();
            var hider=hunt.bodies().stream().filter(b->hunt.hider(b.getUUID())).findFirst().orElseThrow();
            try{
                bot.setUUID(hider.getUUID());seeker.setPos(10.5,121,8.5);
                var reactions=new BotPropReactions();reactions.observe(bot,hunt,seeker,1,2000);
                check(seeker.getUUID().equals(field(BotPropReactions.class,"sender").get(reactions)),"Visible nearby emote queues a response");
                int due=field(BotPropReactions.class,"due").getInt(reactions);
                check(due>=2008&&due<=2020,"Reaction has a human-sized delay");
                reactions.observe(bot,hunt,seeker,7,2001);
                check(field(BotPropReactions.class,"due").getInt(reactions)==due&&field(BotPropReactions.class,"slot").getInt(reactions)==1,"Emote spam does not reset pending reply");
                reactions.tick(bot,hunt,due,true);
                check(field(BotPropReactions.class,"sender").get(reactions)==null,"Reply is emitted once and cleared");
                reactions.reset();seeker.setPos(30.5,121,8.5);reactions.observe(bot,hunt,seeker,1,2200);
                check(field(BotPropReactions.class,"sender").get(reactions)==null,"Distant emote is ignored");
                seeker.setPos(10.5,121,8.5);
                for(int up=0;up<3;up++)level.setBlock(new BlockPos(7,121+up,8),Blocks.STONE.defaultBlockState(),3);
                reactions.observe(bot,hunt,seeker,1,2300);
                check(field(BotPropReactions.class,"sender").get(reactions)==null,"Emote behind a wall is ignored");
                for(int up=0;up<3;up++)level.setBlock(new BlockPos(7,121+up,8),Blocks.AIR.defaultBlockState(),3);
                System.out.println("BOT_PROP_REACTIONS_PASS delay cooldown reply range visibility");
            }finally{bot.setUUID(ownId);seeker.setPos(14.5,121,8.5);}
            float hp=bot.getHealth();bot.fallDistance=40;check(!bot.causeFallDamage(40,1,level.damageSources().fall())&&bot.getHealth()==hp&&bot.fallDistance==0,"Bots take no landing damage");
            for(var item:List.of(Items.IRON_SWORD,Items.IRON_AXE,Items.BOW,Items.CROSSBOW)){
                var stack=new ItemStack(item);check(BotManager.held(stack),"Supported held weapon: "+item);
                if(BotRanged.supports(stack)){bot.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,stack);BotRanged.fire(bot,0,0);}
            }
            check(BotGadgets.supported(new ItemStack(GadgetItems.TURRET.get())),"Turret can be selected as a secondary");
            var loadouts=Loadouts.load();
            for(var w:loadouts.primary())check(BotManager.supportedPrimary(w),"Primary is usable: "+w.id());
            for(var w:loadouts.secondary())if(!(w.stack().getItem() instanceof BombItem)&&!w.commandOnly())
                check(BotManager.supportedSecondary(w),"Secondary is usable: "+w.id());
            float absorption=seeker.getAbsorptionAmount();
            Defibrillator.grant(bot,seeker);check(seeker.getAbsorptionAmount()>=Defibrillator.BONUS,"Bot mercy support grants bonus health");
            seeker.setAbsorptionAmount(absorption);
            var turretWeapon=new Loadouts.Weapon("turret_test","{id:\"gungang:turret\",Count:1b}","Turret","",null,"");
            agent.gadgets.equip(bot,turretWeapon,new ItemStack(GadgetItems.TURRET.get()),false);
            var place=BotGadgets.class.getDeclaredMethod("placeTurret",BotEntity.class,net.minecraft.server.level.ServerLevel.class,net.minecraft.world.entity.LivingEntity.class);place.setAccessible(true);
            check((boolean)place.invoke(agent.gadgets,bot,level,seeker)&&Turrets.owned(bot.getUUID())!=null,"Bot actually deploys its owned turret");
            var exits=BotPropHunt.class.getDeclaredMethod("exits",BotEntity.class,BlockPos.class);exits.setAccessible(true);
            var cell=new BlockPos(40,121,8);
            check((int)exits.invoke(mind,bot,cell)==4,"Open floor offers four escape directions");
            for(var direction:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST))
                for(int n=1;n<=3;n++)for(int up=0;up<2;up++)level.setBlock(cell.relative(direction,n).above(up),Blocks.STONE.defaultBlockState(),3);
            check((int)exits.invoke(mind,bot,cell)==1,"Dead-end hideout has only one escape direction");
            bomb(level,arena);
            System.out.println("BOT_BEHAVIOUR_PASS global_hide visible_escape sustained_escape exposed_memory dead_end no_fall ranged_equipment catalog mercy turret_selection");
        }finally{
            seeker.setPos(savedSeeker);var turret=Turrets.owned(bot.getUUID());if(turret!=null)turret.discard();
            for(var entity:level.getAllEntities())if(entity instanceof net.minecraft.world.entity.projectile.Projectile shot&&shot.getOwner()==bot)shot.discard();
            prop.discard();bot.discard();
            field(BotManager.class,"arena").set(null,savedArena);field(BotManager.class,"server").set(null,savedServer);
            for(var entry:states.entrySet())level.setBlock(entry.getKey(),entry.getValue(),3);
            BotMovement.newTick();
        }
    }
    @SuppressWarnings("unchecked")
    private static void bomb(net.minecraft.server.level.ServerLevel level,ArenaMap arena)throws Exception{
        var agents=(List<BotAgent>)field(BotManager.class,"agents").get(null);var saved=new ArrayList<>(agents);
        boolean savedBots=field(MatchManager.class,"bots").getBoolean(null);
        boolean savedRunning=field(MatchManager.class,"running").getBoolean(null);
        var savedMap=field(MatchManager.class,"map").get(null);
        var savedBomb=field(MatchManager.class,"bomb").get(null);
        var json=ServerFiles.GSON.toJsonTree(BombRules.example()).getAsJsonObject();
        var rules=json.getAsJsonObject("bomb");rules.add("pointA",ServerFiles.GSON.toJsonTree(new BombRules.Point(20,121,8)));
        rules.add("pointB",ServerFiles.GSON.toJsonTree(new BombRules.Point(80,121,8)));
        var match=new BombMatch(level.getServer(),ServerFiles.GSON.fromJson(json,ArenaMap.class));
        var testBots=new ArrayList<BotAgent>();
        try{
            field(MatchManager.class,"bots").setBoolean(null,true);
            field(MatchManager.class,"running").setBoolean(null,true);field(MatchManager.class,"map").set(null,arena);
            agents.clear();
            for(int i=0;i<4;i++){
                var a=new BotAgent(i,i<2?match.game.attacker():match.game.defender());a.bot=BotEntity.TYPE.get().create(level);
                a.bot.setUUID(a.id);a.bot.setPos(20.5+i,121,8.5);a.bot.setOnGround(true);agents.add(a);testBots.add(a);
            }
            var dead=(Set<UUID>)field(BombMatch.class,"dead").get(match);
            for(var player:level.getServer().getPlayerList().getPlayers())dead.add(player.getUUID());
            check(match.attackSite().equals("A"),"Bot-only attackers pick the closer site without a player ping");
            while(match.game.phase()==BombGame.Phase.PREPARING)match.game.tick(2,2);
            check(match.objective(match.game.attacker(),0).act()==BotObjective.Act.PLANT,"Nearest attacker volunteers to plant");
            check(match.objective(match.game.attacker(),1).act()==BotObjective.Act.NONE,"Second attacker covers planter");
            field(MatchManager.class,"bomb").set(null,match);
            var walker=testBots.get(0);walker.bot.setPos(10.5,121,8.5);walker.bot.setOnGround(true);
            walker.movement.prepareSpawnExit();walker.movement.followObjectiveFromSpawn();
            check(!walker.movement.leavingSpawn(),"Bomb objective releases generic spawn-exit priority");
            var ctx=walker.actions.ctx();ctx.bot=walker.bot;ctx.arena=arena;ctx.team=walker.team;ctx.settings=BotManager.settings();
            ctx.movement=walker.movement;ctx.aim=walker.aim;ctx.perception=walker.perception;ctx.health=walker.health;
            ctx.objective=match.objective(walker.team,0);ctx.pathTurn=true;
            for(int t=0;t<30;t++){
                BotMovement.newTick();ctx.now=3000+t;walker.actions.run(BotTactics.Action.OBJECTIVE);walker.bot.tick();
            }
            check(walker.bot.getX()>11.5,"Bomb objective physically moves bot toward site");
            var first=testBots.get(0);first.bot.setHealth(0);
            check(match.objective(match.game.attacker(),1).act()==BotObjective.Act.PLANT,"Survivor takes over dead planter's task");
            var planter=testBots.get(1).bot;planter.setPos(20.5,121,8.5);planter.setOnGround(true);
            check(match.botUse(planter,false)==BombMatch.BotUse.WORKING,"Bot begins planting without player use input: floor="+planter.getOnPos()+" ground="+planter.onGround());
            ageUse(match,planter.getUUID(),BombEquipment.timing.plantTicks());
            check(match.botUse(planter,false)==BombMatch.BotUse.DONE&&match.game.planted(),"Bot completes real bomb placement");
            var defender=testBots.get(2).bot;defender.setPos(21.5,121,8.5);defender.setOnGround(true);
            check(match.objective(match.game.defender(),2).act()==BotObjective.Act.DEFUSE,"Nearest defender volunteers to defuse");
            check(match.objective(match.game.defender(),3).act()==BotObjective.Act.NONE,"Second defender covers defuser");
            check(match.botUse(defender,true)==BombMatch.BotUse.WORKING,"Bot begins defusing without player use input");
            ageUse(match,defender.getUUID(),BombEquipment.timing.defuseTicks());
            check(match.botUse(defender,true)==BombMatch.BotUse.DONE&&match.game.reason().equals("DEFUSED"),"Bot defuse wins round");
            System.out.println("BOT_BOMB_PASS assignment takeover plant defuse cover");
        }finally{
            match.close();for(var a:testBots)a.bot.discard();agents.clear();agents.addAll(saved);field(MatchManager.class,"bots").setBoolean(null,savedBots);
            field(MatchManager.class,"running").setBoolean(null,savedRunning);field(MatchManager.class,"map").set(null,savedMap);
            field(MatchManager.class,"bomb").set(null,savedBomb);
        }
    }
    @SuppressWarnings("unchecked")
    private static void ageUse(BombMatch match,UUID id,int ticks)throws Exception{
        var uses=(Map<UUID,Object>)field(BombMatch.class,"uses").get(match);var use=uses.get(id);var type=use.getClass();
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        uses.put(id,constructor.newInstance(field(type,"defuser").get(use),field(type,"site").get(use),field(type,"position").get(use),
                field(type,"origin").get(use),(long)field(type,"start").get(use)-ticks));
    }
}
