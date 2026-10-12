package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class GadgetSmokeChecks {
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var server=event.getServer();server.execute(()->{
            try {
                var level=server.overworld();var player=FakePlayerFactory.getMinecraft(level);player.setPos(0,100,0);
                ProgressionService.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player));
                var dispatcher=server.getCommands().getDispatcher();var source=server.createCommandSourceStack();
                String profile=player.getUUID().toString();
                check(dispatcher.execute("gungang stats set "+profile+" xp 141",source)==1,"Stats edit command");
                check(dispatcher.execute("gungang xp add "+profile+" 60",source)==1,"XP add command");
                check(dispatcher.execute("gungang stats "+profile,source)==1,"Stats query command");
                check(dispatcher.execute("gungang level "+profile,source)==1,"Level query command");
                check(dispatcher.getRoot().getChild("gungang").getChild("lobby")!=null,"Progression commands must preserve lobby commands");
                boolean denied=false;
                try {dispatcher.execute("gungang stats set "+profile+" xp 0",source.withPermission(0));}
                catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {denied=true;}
                check(denied,"Non-admin edits must be denied");
                var persisted=new PlayerDatabase(ServerFiles.root().resolve("player-db.json")).get(profile,player.getGameProfile().getName());
                check(persisted.experience()==201&&XpTable.defaults().progress(persisted.experience()).level()==3,"Command XP must persist and use cumulative thresholds");
                statsFeedback(server,player);
                check(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(GadgetItems.LANDMINE.get()).toString().equals("gungang:landmine"),"New item namespace");
                var legacy=new Loadouts.Weapon("legacy","{id:'gungang_acoustics:landmine',Count:1b}","Mine","",null,"").stack();
                check(legacy.is(GadgetItems.LANDMINE.get()),"Legacy JSON item ID compatibility");
                var stationary=GadgetItems.DEPLOYABLE.get().create(level);
                stationary.setPos(.1,100,0);stationary.setup(player,false,false);level.addFreshEntity(stationary);
                player.setDeltaMovement(0,0,0);stationary.push(player);stationary.pushEntities();
                check(player.getDeltaMovement().lengthSqr()==0,"Mine must not push a nearby disarming player");
                check(!stationary.showHealthBar(),"Mine health bar hidden by default");
                var originalMine=GadgetConfig.mine;
                GadgetConfig.mine=new GadgetConfig.Mine(100,3,1.2,3,5,5,true);stationary.tick();
                check(stationary.showHealthBar(),"Server config must update installed mine health bar flag");
                GadgetConfig.mine=originalMine;stationary.discard();
                var snow=new ItemStack(Items.SNOWBALL,8);snow.getOrCreateTag().putInt("GunGangIssued",40);
                var weapon=new Loadouts.Weapon("smoke","","smoke","",null,"","",100d,100d,3d);
                SecondaryRuntime.configure(player,snow,weapon);player.setItemInHand(InteractionHand.MAIN_HAND,snow);
                snow.use(level,player,InteractionHand.MAIN_HAND);
                check(snow.getCount()==8,"Use must preserve stack count");
                check(SecondaryRuntime.remaining(player,snow)==60,"Configured cooldown must start on consumption");
                check(snow.use(level,player,InteractionHand.MAIN_HAND).getResult()==net.minecraft.world.InteractionResult.FAIL,"Cooldown must block reuse");
                snow.split(1);check(snow.getCount()==7,"Inventory splitting must still work");
                var zero=new ItemStack(Items.SNOWBALL,1);zero.getOrCreateTag().putInt("GunGangFreeSlot",40);
                SecondaryRuntime.configure(player,zero,new Loadouts.Weapon("zero","","zero","",null,""));player.setItemInHand(InteractionHand.MAIN_HAND,zero);
                zero.use(level,player,InteractionHand.MAIN_HAND);zero.use(level,player,InteractionHand.MAIN_HAND);
                check(zero.getCount()==1&&SecondaryRuntime.remaining(player,zero)==0,"Zero cooldown and last item must remain reusable");
                player.getCooldowns().addCooldown(Items.SNOWBALL,500);
                check(!player.getCooldowns().isOnCooldown(Items.SNOWBALL),"Configured zero must override original item cooldown");
                LoadoutAttributes.apply(player,new Loadouts.Weapon("stats","","","",null,"","",100d,120d,0d));player.setHealth(20);
                check(player.getMaxHealth()==100,"Primary health");
                var heal=new ItemStack(GadgetItems.RECOVERY.get());heal.getOrCreateTag().putInt("GunGangIssued",40);
                GadgetManager.use(player,heal,"recovery_potion");
                for(int i=0;i<100;i++)GadgetManager.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
                check(Math.abs(player.getHealth()-70)<.01,"Recovery must heal exactly 50 across 100 ticks");
                player.setHealth(100);double speed=player.getAttributeValue(Attributes.MOVEMENT_SPEED);
                GadgetManager.use(player,new ItemStack(GadgetItems.ADRENALINE.get()),"adrenaline");
                check(player.getHealth()==50,"Adrenaline cost");check(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed*1.2)<.0001,"Adrenaline speed bonus");
                GadgetManager.clear(player);check(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<.0001,"Cleanup must remove only adrenaline modifiers");
                var barrier=GadgetItems.DEPLOYABLE.get().create(level);barrier.setPos(10,100,10);barrier.setup(player,true,false);level.addFreshEntity(barrier);
                check(barrier.getHealth()==200&&barrier.getBoundingBox().getXsize()==3&&barrier.getBoundingBox().getYsize()==3,"Barrier dimensions and health");
                barrier.hurt(level.damageSources().generic(),25);check(barrier.getHealth()==175,"Barrier takes damage");
                var barrierPosition=barrier.position();
                barrier.knockback(2,1,1);barrier.push(2,1,2);
                check(barrier.getDeltaMovement().lengthSqr()==0,"Barrier must reject knockback and direct pushes");
                barrier.setDeltaMovement(2,1,2);barrier.move(net.minecraft.world.entity.MoverType.SELF,barrier.getDeltaMovement());
                check(barrier.position().equals(barrierPosition)&&barrier.getDeltaMovement().lengthSqr()==0,"External velocity must not move barrier");
                barrier.discard();
                var team=level.getScoreboard().getPlayerTeam("smoke_allies");if(team==null)team=level.getScoreboard().addPlayerTeam("smoke_allies");
                level.getScoreboard().addPlayerToTeam(player.getScoreboardName(),team);
                var mine=GadgetItems.DEPLOYABLE.get().create(level);mine.setPos(20,100,20);mine.setup(player,false,false);level.addFreshEntity(mine);mine.tickCount=11;
                var viewer=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("271df092-9fa2-46c7-b5b0-0045511cd2f7"),"MineViewer"));
                check(mine.mineOutlineVisibleTo(player),"Owner sees mine outline");
                level.getScoreboard().addPlayerToTeam(viewer.getScoreboardName(),team);
                check(mine.mineOutlineVisibleTo(viewer),"Teammate sees mine outline");
                var savedMine=GadgetItems.DEPLOYABLE.get().create(level);savedMine.load(mine.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
                check(savedMine.mineOutlineVisibleTo(viewer)&&savedMine.getTeamColor()==mine.getTeamColor(),"Saved mine preserves outline team and color");
                level.getScoreboard().removePlayerFromTeam(viewer.getScoreboardName(),team);
                check(!mine.mineOutlineVisibleTo(viewer),"Unassigned viewer cannot see mine outline");
                var enemyTeam=level.getScoreboard().getPlayerTeam("smoke_enemies");if(enemyTeam==null)enemyTeam=level.getScoreboard().addPlayerTeam("smoke_enemies");
                level.getScoreboard().addPlayerToTeam(viewer.getScoreboardName(),enemyTeam);
                check(!mine.mineOutlineVisibleTo(viewer),"Enemy cannot see mine outline");
                check(!barrier.mineOutlineVisibleTo(player),"Mine outline excludes barriers");
                var ally=net.minecraft.world.entity.EntityType.COW.create(level);ally.setPos(20.3,100,20);ally.setNoAi(true);level.addFreshEntity(ally);level.getScoreboard().addPlayerToTeam(ally.getScoreboardName(),team);
                float allyHealth=ally.getHealth();mine.tick();check(!mine.isRemoved(),"Friendly entity must not trigger mine");
                var enemy=net.minecraft.world.entity.EntityType.COW.create(level);enemy.setPos(20.5,100,20);enemy.setNoAi(true);level.addFreshEntity(enemy);mine.tick();
                check(mine.isRemoved()&&!enemy.isAlive(),"Enemy must trigger mine damage");check(ally.getHealth()==allyHealth,"Mine must never damage allies");ally.discard();enemy.discard();
                check(DeployableLedger.get(server).count(player.getUUID(),false)==0,"Exploded mine must free installation quota");
                player.setHealth(50);player.getFoodData().setFoodLevel(20);player.getFoodData().setSaturation(20);
                for(int i=0;i<100;i++)player.getFoodData().tick(player);
                check(player.getHealth()==50,"Vanilla food healing must not bypass damage recovery delay");
                var red=level.getScoreboard().getPlayerTeam("Red");if(red==null)red=level.getScoreboard().addPlayerTeam("Red");
                level.getScoreboard().addPlayerToTeam(player.getScoreboardName(),red);
                var placed=GadgetItems.DEPLOYABLE.get().create(level);placed.setPos(40,100,40);placed.setup(player,true,false);level.addFreshEntity(placed);
                check(placed.teamColor()==LobbyManager.options().color("ALPHA"),"Placement must snapshot custom team color");
                check(DeployableLedger.get(server).count(player.getUUID(),true)==1,"Barrier limit must count per owner");
                check(DeployableLedger.get(server).count(java.util.UUID.randomUUID(),true)==0,"Different player must have independent quota");
                var unloaded=GadgetItems.DEPLOYABLE.get().create(level);unloaded.setPos(45,100,45);unloaded.setup(player,false,false);
                check(dispatcher.execute("gungang cleardeployables",source)==1,"Cleanup command");
                check(placed.isRemoved()&&DeployableLedger.get(server).count(player.getUUID(),true)==0,"Cleanup must remove loaded entities and ledger");
                GadgetManager.track(unloaded);
                check(unloaded.isRemoved()&&DeployableLedger.get(server).count(player.getUUID(),false)==0,"Cleared unloaded mine must be discarded when tracked again");
                var commandWeapon=ServerFiles.GSON.fromJson("{\"id\":\"command_test\",\"name\":\"Command\",\"description\":\"\",\"usage\":\"\",\"command\":\"item replace entity @s weapon.offhand with minecraft:snowball 1\",\"cooldownSeconds\":3}",Loadouts.Weapon.class);
                commandWeapon.validate(false);LobbyManager.executeSecondaryCommand(player,commandWeapon,40,"GunGangIssued");
                check(player.getOffhandItem().is(Items.SNOWBALL)&&SecondaryRuntime.secondary(player.getOffhandItem()),"Command fallback must execute as player and configure offhand result");
                player.getInventory().setItem(0,new ItemStack(Items.DIAMOND));player.getInventory().setItem(36,new ItemStack(Items.DIAMOND_BOOTS));
                player.containerMenu.setCarried(new ItemStack(Items.STICK));LobbyManager.clearPlayerInventory(player);
                check(player.getInventory().isEmpty()&&player.containerMenu.getCarried().isEmpty(),"Inventory reset must clear armor, offhand, main and cursor");
                var base=ArenaMap.example();var n=NormandyRules.defaults();
                var center=new ArenaMap.Spawn(60,250,60,0,0);
                var points=new java.util.ArrayList<>(n.points());points.set(0,new NormandyRules.Point("A",center,5,n.points().get(0).attackSpawn()));
                var arena=new ArenaMap("smoke","smoke","",false,900,"adventure","minecraft:overworld",base.alphaSpawn(),base.bravoSpawn(),base.spectatorSpawn(),base.alphaArea(),base.bravoArea(),null,java.util.List.of(),java.util.List.of(),java.util.List.of(),new NormandyRules(400,400,10,n.attackLaunch(),points));
                var pos=new net.minecraft.core.BlockPos(60,250,60);var before=level.getBlockState(pos);var below=level.getBlockState(pos.below());
                NormandyBeacon.get(server).activate(server,arena,0);
                check(level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BLUE_STAINED_GLASS)&&level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.BEACON),"Active Normandy point requires colored glass and beacon");
                var beacon=(net.minecraft.world.level.block.entity.BeaconBlockEntity)level.getBlockEntity(pos.below());
                long previousTime=level.getGameTime();
                server.getWorldData().overworldData().setGameTime((previousTime/80+1)*80);
                for(int i=0;i<100;i++)net.minecraft.world.level.block.entity.BeaconBlockEntity.tick(level,pos.below(),level.getBlockState(pos.below()),beacon);
                server.getWorldData().overworldData().setGameTime(previousTime);
                check(!beacon.getBeamSections().isEmpty(),"Normandy beacon must produce a visible beam in open sky");
                NormandyBeacon.get(server).restore(server);
                check(level.getBlockState(pos).equals(before)&&level.getBlockState(pos.below()).equals(below),"Ending Normandy must restore original blocks");
                check(LobbyManager.preparationSeconds(arena)==45&&LobbyManager.preparationSeconds(base)==30,"Normandy-only 45 second preparation");
                var packPos=new net.minecraft.core.BlockPos(80,250,80);
                var secondPos=packPos.east(2);var largePos=packPos.east(4);
                var oldPack=level.getBlockState(packPos);var oldSecond=level.getBlockState(secondPos);var oldLarge=level.getBlockState(largePos);
                long packTime=level.getGameTime();
                try {
                    level.setBlock(packPos,StationBlocks.SMALL.get().defaultBlockState(),3);
                    level.setBlock(secondPos,StationBlocks.SMALL.get().defaultBlockState(),3);
                    level.setBlock(largePos,StationBlocks.LARGE.get().defaultBlockState(),3);
                    var small=(StationEntity)level.getBlockEntity(packPos);
                    var second=(StationEntity)level.getBlockEntity(secondPos);
                    var large=(StationEntity)level.getBlockEntity(largePos);
                    player.setHealth(10);small.interact(player,true);
                    check(player.getHealth()==45&&small.remaining()==200&&small.getBlockState().getValue(StationBlock.COOLING),"Small pack heals 35 and becomes glass for 10 seconds");
                    small.interact(player,true);check(player.getHealth()==45,"Cooling pack cannot be reused");
                    var other=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("cc1b0ad4-f6ef-40c8-a851-68a27aebd11f"),"PackTester"));
                    other.setHealth(5);small.interact(other,true);check(other.getHealth()==5,"Two players share the cooldown of the same placed block");
                    second.interact(player,true);check(player.getHealth()==80&&second.remaining()==200,"Separate placed blocks must have independent cooldowns");
                    var restored=new StationEntity(packPos,small.getBlockState());restored.setLevel(level);restored.load(small.saveWithoutMetadata());
                    check(restored.remaining()==200,"Pack cooldown survives block entity save/load");
                    large.interact(player,true);check(player.getHealth()==100&&large.remaining()==300,"Large pack heals 70 capped to max and cools for 15 seconds");
                    server.getWorldData().overworldData().setGameTime(packTime+200);
                    StationEntity.tick(level,packPos,small.getBlockState(),small);
                    check(!small.getBlockState().getValue(StationBlock.COOLING)&&large.remaining()==100,"Each pack independently becomes ready");
                    small.interact(player,true);check(small.remaining()==0,"Full health must not consume ready pack");
                    player.setHealth(10);small.interact(player,true);check(player.getHealth()==45&&small.remaining()==200,"Recovered pack reusable");
                    check(StationBlocks.SMALL.get().defaultBlockState().getLightEmission()==15,"Health pack emits light");
                    LobbyManager.station(player,StationBlock.Kind.ALPHA,player.blockPosition().below());
                    check(player.getRespawnPosition().equals(player.blockPosition()),"Team station sets respawn above the touched block");
                    check(LobbyManager.team(player.getUUID()).equals("ALPHA"),"Alpha station assigns its team");
                    LobbyManager.station(player,StationBlock.Kind.WAR_TABLE,player.blockPosition().below());
                    check(LobbyManager.team(player.getUUID()).equals("ALPHA"),"War table must not reset team selection");
                    LobbyManager.station(player,StationBlock.Kind.BRAVO,player.blockPosition().below());
                    check(LobbyManager.team(player.getUUID()).equals("BRAVO"),"Bravo station assigns its team");
                } finally {
                    server.getWorldData().overworldData().setGameTime(packTime);
                    level.setBlock(packPos,oldPack,3);level.setBlock(secondPos,oldSecond,3);level.setBlock(largePos,oldLarge,3);
                }
                deployablePlacement(level,player);
                combatEquipment(level,player);
                furniture(level,player);
                ordnance(level,player);
                lobbyReentry(server,player);
                LoadoutAttributes.clear(player);
                System.out.println("GUNGANG_GADGET_SMOKE_PASS");
            } catch(Throwable failure) {failure.printStackTrace();System.out.println("GUNGANG_GADGET_SMOKE_FAIL");}
            finally {server.halt(false);}
        });
    }
    private static void combatEquipment(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player) {
        var server=level.getServer();long time=level.getGameTime();
        var main=player.getMainHandItem();var off=player.getOffhandItem();var position=player.position();float yaw=player.getYRot(),pitch=player.getXRot();
        var originalTeam=player.getTeam();
        var ally=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("a510fe56-10d7-4a55-a216-9ab0e78b507d"),"ScannerAlly"));
        var enemy=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("9f44ad15-415d-42c8-b4bf-d9fb22603f36"),"ScannerEnemy"));
        var red=level.getScoreboard().getPlayerTeam("Red");if(red==null)red=level.getScoreboard().addPlayerTeam("Red");
        var blue=level.getScoreboard().getPlayerTeam("Blue");if(blue==null)blue=level.getScoreboard().addPlayerTeam("Blue");
        try {
            level.getScoreboard().addPlayerToTeam(player.getScoreboardName(),red);level.getScoreboard().addPlayerToTeam(ally.getScoreboardName(),red);level.getScoreboard().addPlayerToTeam(enemy.getScoreboardName(),blue);
            player.setPos(150,240,150);player.setYRot(0);player.setYHeadRot(0);player.setXRot(0);enemy.setPos(150,240,155);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SNOWBALL));
            var shieldWeapon=new Loadouts.Weapon("97_shield","{id:'gungang:shield',Count:1b}","Shield","",null,"","",100d,100d,90d);
            var shield=shieldWeapon.stack();shield.getOrCreateTag().putInt("GunGangIssued",40);SecondaryRuntime.configure(player,shield,shieldWeapon);
            player.setItemInHand(InteractionHand.OFF_HAND,shield);
            check(CombatGadgets.holdingShield(player),"Simply holding shield activates defense");
            check(player.getMainHandItem().use(level,player,InteractionHand.MAIN_HAND).getResult()==net.minecraft.world.InteractionResult.FAIL,"Held shield prevents weapon use");
            var front=new net.minecraftforge.event.entity.living.LivingHurtEvent(player,level.damageSources().playerAttack(enemy),25);
            CombatGadgets.hurt(front);check(front.isCanceled()&&shield.getTag().getFloat("GunGangShieldHealth")==50,"Front hit consumes only 25 durability");
            GadgetManager.use(player,shield,"shield");check(shield.getTag().getFloat("GunGangShieldHealth")==50,"Use cannot repair unbroken shield");
            var replacement=shieldWeapon.stack();replacement.getOrCreateTag().putInt("GunGangIssued",40);SecondaryRuntime.configure(player,replacement,shieldWeapon);
            check(replacement.getTag().getFloat("GunGangShieldHealth")==50,"Reselect cannot refill shield");
            enemy.setPos(150,240,145);
            var rear=new net.minecraftforge.event.entity.living.LivingHurtEvent(player,level.damageSources().playerAttack(enemy),20);
            CombatGadgets.hurt(rear);check(!rear.isCanceled()&&rear.getAmount()==20&&shield.getTag().getFloat("GunGangShieldHealth")==50,"Rear hit bypasses shield without consuming durability");
            enemy.setPos(150,240,155);
            var overflow=new net.minecraftforge.event.entity.living.LivingHurtEvent(player,level.damageSources().playerAttack(enemy),80);
            CombatGadgets.hurt(overflow);check(!overflow.isCanceled()&&overflow.getAmount()==30,"Damage beyond durability reaches player");
            check(!CombatGadgets.holdingShield(player)&&SecondaryRuntime.remaining(player,shield)==1800,"Broken shield begins 90 second repair");
            server.getWorldData().overworldData().setGameTime(time+1799);check(!CombatGadgets.holdingShield(player),"Shield cannot repair early");
            server.getWorldData().overworldData().setGameTime(time+1800);check(CombatGadgets.holdingShield(player),"Shield repairs after 90 seconds");
            check(CombatGadgets.hud(player).gauges().stream().anyMatch(g->g.kind().equals("shield")&&g.remaining()==75),"Shield durability is supplied to HUD");
            var scanWeapon=new Loadouts.Weapon("96_infrared-scanner","{id:'gungang:infrared_scanner',Count:1b}","Scanner","",null,"","",100d,100d,120d);
            var scanner=scanWeapon.stack();scanner.getOrCreateTag().putInt("GunGangIssued",40);SecondaryRuntime.configure(player,scanner,scanWeapon);player.setItemInHand(InteractionHand.OFF_HAND,scanner);
            GadgetManager.use(player,scanner,"infrared_scanner");
            var candidates=java.util.List.of(player,ally,enemy);
            check(CombatGadgets.view(player,candidates).targets().size()==1&&CombatGadgets.view(ally,candidates).targets().size()==1,"Scanner shares enemies with owner and teammate");
            check(CombatGadgets.view(enemy,candidates).targets().isEmpty(),"Opposing team cannot receive scanner positions");
            check(CombatGadgets.view(ally,candidates).targets().get(0).color()==LobbyManager.options().color("BRAVO"),"Enemy silhouette uses enemy team color");
            check(SecondaryRuntime.remaining(player,scanner)==2400,"Scanner starts 120 second cooldown");
            server.getWorldData().overworldData().setGameTime(time+1900);GadgetManager.use(player,scanner,"infrared_scanner");
            server.getWorldData().overworldData().setGameTime(time+2000);
            check(CombatGadgets.view(ally,candidates).targets().isEmpty(),"Scan expires after 10 seconds and cannot be extended during cooldown");
            var replacementScan=scanWeapon.stack();replacementScan.getOrCreateTag().putInt("GunGangIssued",40);SecondaryRuntime.configure(player,replacementScan,scanWeapon);
            check(SecondaryRuntime.remaining(player,replacementScan)==2200,"Scanner cooldown survives reselect");
            var mine=GadgetItems.DEPLOYABLE.get().create(level);mine.setPos(150,240,160);mine.setup(player,false,false);level.addFreshEntity(mine);
            GadgetManager.changingSecondary(player,shieldWeapon);
            check(mine.isRemoved()&&DeployableLedger.get(server).count(player.getUUID(),false)==0,"Changing secondary removes own loaded structures and quota");
        } finally {
            GadgetManager.clear(player);CombatGadgets.clear(ally);CombatGadgets.clear(enemy);
            server.getWorldData().overworldData().setGameTime(time);player.setItemInHand(InteractionHand.MAIN_HAND,main);player.setItemInHand(InteractionHand.OFF_HAND,off);
            player.setPos(position);player.setYRot(yaw);player.setXRot(pitch);
            level.getScoreboard().removePlayerFromTeam(ally.getScoreboardName());level.getScoreboard().removePlayerFromTeam(enemy.getScoreboardName());
            if(originalTeam!=null)level.getScoreboard().addPlayerToTeam(player.getScoreboardName(),level.getScoreboard().getPlayerTeam(originalTeam.getName()));else level.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
        }
    }
    private static void statsFeedback(net.minecraft.server.MinecraftServer server,net.minecraft.server.level.ServerPlayer originalPlayer)throws Exception {
        var messages=new java.util.ArrayList<String>();
        var feedback=server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_SENDCOMMANDFEEDBACK);
        boolean original=feedback.get();
        var player=new net.minecraftforge.common.util.FakePlayer(originalPlayer.serverLevel(),originalPlayer.getGameProfile()) {
            public void sendSystemMessage(net.minecraft.network.chat.Component message) {messages.add(message.getString());}
        };
        var source=player.createCommandSourceStack();
        try {
            feedback.set(false,server);
            source.sendSuccess(()->net.minecraft.network.chat.Component.literal("hidden feedback"),false);
            check(messages.isEmpty(),"Regression requires normal command feedback to be suppressed");
            for(String command:java.util.List.of("stats","xp","level")) {
                for(String target:java.util.List.of(""," "+player.getUUID())) {
                    messages.clear();
                    check(server.getCommands().getDispatcher().execute("gungang "+command+target,source)==1,"Progression query succeeds without feedback");
                    check(messages.size()==1&&messages.get(0).contains("경험치 0/159"),"Query must deliver level-relative XP directly to chat: "+messages);
                    check(messages.get(0).contains("통산")==command.equals("stats"),"Only stats includes full statistics");
                }
            }
        } finally {feedback.set(original,server);}
    }
    private static void deployablePlacement(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player) {
        var pos=new net.minecraft.core.BlockPos(128,240,128);
        var before=new java.util.LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var mode=player.gameMode.getGameModeForPlayer();
        var main=player.getMainHandItem();var off=player.getOffhandItem();
        var location=player.position();float yaw=player.getYRot(),pitch=player.getXRot();
        try {
            for(var at:net.minecraft.core.BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,3,2))) {
                var key=at.immutable();level.getChunk(key);before.put(key,level.getBlockState(key));
                level.setBlock(key,(key.getY()<pos.getY()?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(),3);
            }
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3.5);player.setYRot(180);player.setXRot(28.4f);
            player.xo=player.getX();player.yo=player.getY();player.zo=player.getZ();
            player.yRotO=player.getYRot();player.xRotO=player.getXRot();
            player.setYHeadRot(180);player.yHeadRotO=180;
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
            for(boolean wall:new boolean[]{false,true}) {
                var stack=new ItemStack(wall?GadgetItems.BARRIER.get():GadgetItems.LANDMINE.get());
                stack.getOrCreateTag().putInt("GunGangIssued",40);
                SecondaryRuntime.configure(player,stack,new Loadouts.Weapon("placement_"+wall,"","","",null,"","",100d,100d,3d));
                player.setItemInHand(InteractionHand.OFF_HAND,stack);
                check(!player.mayUseItemAt(pos.below(),net.minecraft.core.Direction.UP,stack),"Regression requires adventure block placement to be denied");
                check(pos.equals(GadgetManager.target(player,5)),"Placement ray must hit prepared floor: target="+GadgetManager.target(player,5)+" eye="+player.getEyePosition(0)+" look="+player.getViewVector(0));
                check(GadgetManager.valid(player,pos,wall,false),"Adventure offhand deployable placement allowed");
                level.setBlock(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                check(!GadgetManager.valid(player,pos,wall,false),"Occupied placement must remain rejected");
                level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                stack.use(level,player,InteractionHand.OFF_HAND);
                check(SecondaryRuntime.remaining(player,stack)==0,"Preview must not start cooldown");
                GadgetManager.action(player,"place");
                check(DeployableLedger.get(level.getServer()).count(player.getUUID(),wall)==1,"Adventure placement must spawn and record deployable");
                check(SecondaryRuntime.remaining(player,stack)==60,"Successful placement starts cooldown");
                GadgetManager.clear(player);
            }
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            check(!GadgetManager.valid(player,pos,true,false),"Spectators cannot deploy");
        } finally {
            GadgetManager.clear(player);player.setGameMode(mode);
            player.setItemInHand(InteractionHand.MAIN_HAND,main);player.setItemInHand(InteractionHand.OFF_HAND,off);
            player.setPos(location);player.setYRot(yaw);player.setXRot(pitch);
            for(var entry:before.entrySet())level.setBlock(entry.getKey(),entry.getValue(),3);
        }
    }
    private static void lobbyReentry(net.minecraft.server.MinecraftServer server,net.minecraft.server.level.ServerPlayer player)throws Exception {
        var playersField=LobbyManager.class.getDeclaredField("PLAYERS");playersField.setAccessible(true);
        var players=(java.util.Map<?,?>)playersField.get(null);var entry=players.get(player.getUUID());
        var ready=entry.getClass().getDeclaredField("ready");ready.setAccessible(true);ready.setBoolean(entry,true);
        String team=LobbyManager.team(player.getUUID());int count=players.size();
        var dispatcher=server.getCommands().getDispatcher();
        check(dispatcher.execute("gungang lobby",player.createCommandSourceStack())==1,"Closed lobby UI can be reopened by a normal player");
        check(ready.getBoolean(entry)&&LobbyManager.team(player.getUUID()).equals(team),"Reopening preserves team and readiness");
        for(int i=0;i<3;i++)check(dispatcher.execute("gungang lobby open",server.createCommandSourceStack())==1,"Repeated open reuses existing lobby");
        LobbyManager.station(player,StationBlock.Kind.WAR_TABLE,player.blockPosition().below());
        check(players.size()==count&&players.get(player.getUUID())==entry&&ready.getBoolean(entry)&&LobbyManager.team(player.getUUID()).equals(team),"Commands and tables share one lobby without resetting players");
        var phase=LobbyManager.class.getDeclaredField("phase");phase.setAccessible(true);
        try {
            for(String active:java.util.List.of("PREPARING","RUNNING")){
                phase.set(null,active);
                check(dispatcher.execute("gungang lobby open",server.createCommandSourceStack())==0,"Additional lobby rejected during active match");
                check(phase.get(null).equals(active)&&players.size()==count&&ready.getBoolean(entry),"Rejected lobby creation preserves active match");
            }
        }finally{phase.set(null,"LOBBY");ready.setBoolean(entry,false);}
    }
    private static void ordnance(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player) {
        long originalTime=level.getGameTime();
        var time=(net.minecraft.world.level.storage.ServerLevelData)level.getLevelData();
        player.getInventory().clearContent();GadgetManager.clear(player);SecondaryRuntime.clear(player);
        var definition=OrdnanceLoadouts.defaults().get(1);var stack=definition.stack();stack.getOrCreateTag().putInt("GunGangFreeSlot",40);
        SecondaryRuntime.configure(player,stack,definition);player.setItemInHand(InteractionHand.OFF_HAND,stack);
        check(SecondaryRuntime.remaining(player,stack)==4800,"Orbital first equip must start a 240-second cooldown");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(GadgetItems.ORBITAL_STRIKE.get()));
        check(player.getCooldowns().getCooldownPercent(stack.getItem(),0)==1,"Untagged main hand must not hide offhand cooldown");
        time.setGameTime(originalTime+20);
        var replacement=definition.stack();replacement.getOrCreateTag().putInt("GunGangFreeSlot",40);SecondaryRuntime.configure(player,replacement,definition);
        check(SecondaryRuntime.remaining(player,replacement)==4780,"Reselecting must preserve orbital deadline");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.getInventory().setItem(4,replacement);
        check(player.getCooldowns().getCooldownPercent(replacement.getItem(),0)>.99,"Inventory slots must retain cooldown display");
        var ally=net.minecraft.world.entity.EntityType.COW.create(level);var outside=net.minecraft.world.entity.EntityType.COW.create(level);
        level.getChunk(1,1);player.setPos(20,100,20);
        ally.setPos(22,100,20);outside.setPos(45,100,20);
        boolean wasRaining=time.isRaining();time.setRaining(false);level.setRainLevel(0);
        for(var cow:java.util.List.of(ally,outside)) {cow.setNoAi(true);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);cow.setHealth(300);level.addFreshEntity(cow);}
        var team=level.getScoreboard().getPlayerTeam("ordnance_test");if(team==null)team=level.getScoreboard().addPlayerTeam("ordnance_test");team.setAllowFriendlyFire(false);
        level.getScoreboard().addPlayerToTeam(player.getScoreboardName(),team);level.getScoreboard().addPlayerToTeam(ally.getScoreboardName(),team);
        var fire=GadgetItems.ORDNANCE.get().create(level);fire.setPos(20,100,20);fire.setup(player,false);level.addFreshEntity(fire);fire.tick();
        check(ally.getRemainingFireTicks()==60,"Molotov must burn allies for 3 seconds; ticks="+ally.getRemainingFireTicks()+", visible="+level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,fire.getBoundingBox().inflate(5)).size());check(!outside.isOnFire(),"Outside molotov radius must not burn");
        time.setGameTime(level.getGameTime()+160);fire.tick();check(fire.isRemoved(),"Molotov field expires after 8 seconds");ally.clearFire();
        var strike=GadgetItems.ORDNANCE.get().create(level);strike.setPos(20,100,20);strike.setup(player,true);level.addFreshEntity(strike);
        long launched=level.getGameTime();time.setGameTime(launched+99);strike.tick();check(ally.getHealth()==300,"Orbital must wait all 5 seconds");
        GadgetManager.clearDeployables(player);check(!strike.isRemoved(),"Changing equipment must not cancel a committed strike");
        time.setGameTime(launched+100);strike.tick();
        check(strike.isRemoved()&&Math.abs(ally.getHealth()-(300-OrdnanceRules.orbitalDamage(2)))<.01,"Orbital applies distance-based damage to allies at 5 seconds");check(outside.getHealth()==300,"Outside orbital radius must not take damage");
        check(!OrdnanceDamage.active(),"Friendly-fire override must be cleared after damage");
        ally.discard();outside.discard();time.setGameTime(originalTime);time.setRaining(wasRaining);SecondaryRuntime.clear(player);player.getInventory().clearContent();
        System.out.println("GUNGANG_ORDNANCE_SMOKE_OK");
        survival(level,player);
    }
    private static void survival(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player) {
        long start=level.getGameTime();var time=level.getServer().getWorldData().overworldData();
        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);player.getInventory().clearContent();SecondaryRuntime.clear(player);
        var definition=OrdnanceLoadouts.defaults().stream().filter(w->w.id().equals("92")).findFirst().orElseThrow();
        var life=definition.stack();life.getOrCreateTag().putInt("GunGangFreeSlot",40);SecondaryRuntime.configure(player,life,definition);player.setItemInHand(InteractionHand.OFF_HAND,life);
        player.setHealth(0);
        var fatal=new net.minecraftforge.event.entity.living.LivingDeathEvent(player,level.damageSources().generic());SurvivalGadgets.death(fatal);
        check(fatal.isCanceled()&&player.isAlive()&&player.getAbsorptionAmount()==30,"Life support cancels lethal damage with 30 yellow health");
        check(SecondaryRuntime.remaining(player,life)==2400,"Life support starts 120-second cooldown on activation");
        for(int tick=1;tick<=58;tick++) {time.setGameTime(start+tick);SurvivalGadgets.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));}
        check(player.getAbsorptionAmount()==1,"Life reserve drains one point every two ticks");
        player.heal(2);time.setGameTime(start+60);SurvivalGadgets.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
        check(player.isAlive()&&player.getHealth()>2&&player.getAbsorptionAmount()==0,"Healing before expiration allows survival");
        var second=new net.minecraftforge.event.entity.living.LivingDeathEvent(player,level.damageSources().generic());SurvivalGadgets.death(second);check(!second.isCanceled(),"Life support cannot reactivate during cooldown");
        SecondaryRuntime.clear(player);SecondaryRuntime.configure(player,life,definition);player.setHealth(0);
        var unhealed=new net.minecraftforge.event.entity.living.LivingDeathEvent(player,level.damageSources().generic());SurvivalGadgets.death(unhealed);
        for(int tick=1;tick<=60;tick++) {time.setGameTime(start+60+tick);SurvivalGadgets.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));}
        check(player.getHealth()==0,"Without healing the reserve expires in death after three seconds");
        player.setHealth(50);player.deathTime=0;
        var jetDefinition=OrdnanceLoadouts.defaults().stream().filter(w->w.id().equals("93")).findFirst().orElseThrow();var jet=jetDefinition.stack();jet.getOrCreateTag().putInt("GunGangFreeSlot",40);
        var jetJson=ServerFiles.GSON.toJsonTree(jetDefinition).getAsJsonObject();jetJson.remove("jetpackVerticalFlight");
        var horizontalDefinition=ServerFiles.GSON.fromJson(jetJson,Loadouts.Weapon.class);
        check(!horizontalDefinition.jetpackVerticalFlight(),"Missing vertical-flight setting defaults to false");
        SecondaryRuntime.configure(player,jet,horizontalDefinition);player.setPos(20,100,20);player.setXRot(-90);
        check(SurvivalGadgets.startJet(player,jet),"Horizontal jetpack starts");
        SurvivalGadgets.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
        check(player.isNoGravity()&&player.getDeltaMovement().y==0&&player.getDeltaMovement().horizontalDistance()>.8,"Default jetpack flies horizontally even when looking straight up");
        player.setXRot(90);SurvivalGadgets.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
        check(player.getDeltaMovement().y==0,"Horizontal jetpack cannot descend by aiming down");
        SurvivalGadgets.clear(player);SecondaryRuntime.clear(player);
        jetJson.addProperty("jetpackVerticalFlight",true);jetDefinition=ServerFiles.GSON.fromJson(jetJson,Loadouts.Weapon.class);
        SecondaryRuntime.configure(player,jet,jetDefinition);player.setItemInHand(InteractionHand.OFF_HAND,jet);player.setPos(20,100,20);player.setXRot(-89);
        check(SurvivalGadgets.startJet(player,jet),"Jetpack starts");
        check(SecondaryRuntime.remaining(player,jet)==480,"Jetpack cooldown is 24 seconds");
        player.heal(20);check(player.getHealth()==50,"Jetpack blocks healing");
        var snow=new ItemStack(Items.SNOWBALL);player.setItemInHand(InteractionHand.MAIN_HAND,snow);
        check(snow.use(level,player,InteractionHand.MAIN_HAND).getResult()==net.minecraft.world.InteractionResult.FAIL,"Jetpack blocks weapon use");
        SurvivalGadgets.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
        check(player.isNoGravity()&&player.getDeltaMovement().y>.8&&Math.abs(player.getDeltaMovement().length()-SurvivalRules.JET_SPEED)<1e-6,"Jetpack flies upward without gravity at constant speed");
        player.setXRot(89);player.fallDistance=10;
        SurvivalGadgets.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
        check(player.getDeltaMovement().y<-.8&&player.fallDistance==0,"Jetpack follows downward aim and clears accumulated fall distance");
        time.setGameTime(start+180);SurvivalGadgets.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
        check(!SurvivalGadgets.jetting(player),"Jetpack ends after three seconds");
        check(!player.isNoGravity()&&player.getDeltaMovement().lengthSqr()==0,"Jetpack restores gravity and stops thrust on expiry");
        player.setNoGravity(true);
        check(SurvivalGadgets.startJet(player,jet),"Jetpack starts with preexisting no-gravity state");
        SurvivalGadgets.clear(player);
        check(player.isNoGravity(),"Jetpack cleanup preserves preexisting no-gravity state");
        player.setNoGravity(false);
        SecondaryRuntime.configure(player,jet,horizontalDefinition);
        check(!jet.getTag().getBoolean("GunGangJetpackVerticalFlight"),"Reconfiguration disables previously enabled vertical flight");
        jetpackSteps(level,player,jet);
        SurvivalGadgets.reset(level.getServer());SurvivalGadgets.clear(player);SecondaryRuntime.clear(player);time.setGameTime(start);player.getInventory().clearContent();
        System.out.println("GUNGANG_SURVIVAL_SMOKE_OK");
    }
    private static void jetpackSteps(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player,ItemStack jet) {
        var base=new net.minecraft.core.BlockPos(180,240,180);
        var saved=new java.util.LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int x=-1;x<=4;x++)for(int y=-1;y<=3;y++)for(int z=-1;z<=1;z++) {
            var pos=base.offset(x,y,z);saved.put(pos,level.getBlockState(pos));
            level.setBlock(pos,y==-1?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        }
        try {
            player.setPos(180.4,240,180.5);player.setOnGround(false);
            check(SurvivalGadgets.startJet(player,jet),"Horizontal jetpack starts for step collision checks");
            var obstacle=base.offset(1,0,0);
            level.setBlock(obstacle,net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState(),3);
            player.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.85,0,0));
            check(Math.abs(player.getY()-240.5)<1e-6&&player.getX()>181,"No-gravity jetpack climbs a half slab");
            level.setBlock(base.offset(2,0,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            player.setOnGround(false);player.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.85,0,0));
            check(Math.abs(player.getY()-241)<1e-6,"Jetpack climbs consecutive half-block rises");
            level.setBlock(base.offset(2,0,0),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            level.setBlock(obstacle,net.minecraft.world.level.block.Blocks.STONE_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING,net.minecraft.core.Direction.EAST),3);
            player.setPos(180.4,240,180.5);player.setOnGround(false);
            player.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.4,0,0));
            check(Math.abs(player.getY()-240.5)<1e-6,"Jetpack climbs the lower stair tread");
            for(int i=0;i<3;i++) {player.setOnGround(false);player.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.4,0,0));}
            check(Math.abs(player.getY()-241)<1e-6,"Jetpack climbs the upper stair tread");
            level.setBlock(obstacle,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            player.setPos(180.4,240,180.5);player.setOnGround(false);
            player.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.85,0,0));
            check(player.getY()==240&&player.getX()<181,"Jetpack cannot climb a full-block wall");
            level.setBlock(obstacle,net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState(),3);
            level.setBlock(obstacle.above(2),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            player.setPos(180.4,240,180.5);player.setOnGround(false);
            player.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.85,0,0));
            check(player.getY()==240&&player.getX()<181,"Jetpack stepping respects low ceilings");
            SurvivalGadgets.clear(player);level.setBlock(obstacle.above(2),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            player.setPos(180.4,240,180.5);player.setOnGround(false);
            player.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.85,0,0));
            check(player.getY()==240,"Inactive jetpack leaves airborne stepping unchanged");
            System.out.println("GUNGANG_JETPACK_STEP_SMOKE_OK");
        } finally {
            SurvivalGadgets.clear(player);saved.forEach((pos,state)->level.setBlock(pos,state,3));
        }
    }
    private static void furniture(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player){
        var root=new net.minecraft.core.BlockPos(96,240,96);
        var before=new java.util.LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var block:java.util.List.of(StationBlocks.WAR_TABLE.get(),StationBlocks.ARMORY.get())){
            var furniture=(FurnitureBlock)block;
            for(var facing:net.minecraft.core.Direction.Plane.HORIZONTAL){
                for(int part=0;part<furniture.partCount();part++){
                    var at=root.offset(furniture.offset(part,facing));level.getChunk(at);
                    before.putIfAbsent(at,level.getBlockState(at));level.setBlock(at,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                }
                player.setYRot(facing.getOpposite().toYRot());player.setPos(110,240,110);
                var stack=new ItemStack(block);
                var context=new net.minecraft.world.item.context.BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(root),net.minecraft.core.Direction.UP,root,false));
                check(furniture.getStateForPlacement(context)!=null,"Furniture fits in clear area: "+facing);
                var blocked=root.offset(furniture.offset(furniture.partCount()-1,facing));
                level.setBlock(blocked,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                check(furniture.getStateForPlacement(context)==null,"Occupied slice must reject whole placement");
                level.setBlock(blocked,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                check(((net.minecraft.world.item.BlockItem)block.asItem()).place(context).consumesAction(),"One item must place the whole furniture");
                check(stack.isEmpty(),"Multi-block furniture consumes only one item");
                var anchorEntity=level.getBlockEntity(root);
                for(int part=0;part<furniture.partCount();part++){
                    var at=root.offset(furniture.offset(part,facing));var state=level.getBlockState(at);
                    check(state.is(block)&&state.getValue(FurnitureBlock.PART)==part&&state.getValue(FurnitureBlock.FACING)==facing,"Every slice has matching orientation and part");
                    check(furniture.interactionEntity(level,at,state)==anchorEntity,"Every slice must open the same station");
                    check(!state.getCollisionShape(level,at).isEmpty(),"Furniture collision must be present");
                }
                var broken=root.offset(furniture.offset(furniture.partCount()-1,facing));
                var area=new net.minecraft.world.phys.AABB(root).inflate(5);
                int oldDrops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).size();
                level.destroyBlock(broken,true);
                check(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).size()==oldDrops+1,"Breaking a slice drops one furniture item, not one per slice");
                for(int part=0;part<furniture.partCount();part++)check(!level.getBlockState(root.offset(furniture.offset(part,facing))).is(block),"Breaking any slice must remove the full station");
            }
        }
        for(var entry:before.entrySet())level.setBlock(entry.getKey(),entry.getValue(),3);
        for(var drop:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(root).inflate(5)))drop.discard();
    }
}
