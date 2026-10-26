package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in server integration checks, in an isolated world, never packaged. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class TurretSmokeChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static BotEntity target(ServerLevel level,double x,double z){
        var bot=BotEntity.TYPE.get().create(level);bot.setPos(x,240,z);bot.setNoGravity(true);bot.setNoAi(true);
        bot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);bot.setHealth(1024);level.addFreshEntity(bot);return bot;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();long start=level.getGameTime();
        try {
            // Exercise a fixed fixture independently of live server tuning.
            Turrets.config=new Turrets.Config(6,16,120,150,250,1,20,3,5);
            var p=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TurretOwner"));
            p.setPos(0,240,4);p.setHealth(20);level.addNewPlayer(p);
            var pos=new BlockPos(0,240,0);
            for(int x=-5;x<=22;x++)for(int z=-5;z<=10;z++){
                level.setBlockAndUpdate(new BlockPos(x,239,z),Blocks.STONE.defaultBlockState());
                for(int y=240;y<=243;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            check(Turrets.valid(p,pos),"Clear 2x2x2 foundation rejected");
            level.setBlockAndUpdate(pos.offset(1,-1,1),Blocks.AIR.defaultBlockState());
            check(!Turrets.valid(p,pos),"Missing one of four support blocks accepted");
            level.setBlockAndUpdate(pos.offset(1,-1,1),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos.offset(1,1,1),Blocks.STONE.defaultBlockState());
            check(!Turrets.valid(p,pos),"Low ceiling accepted");level.setBlockAndUpdate(pos.offset(1,1,1),Blocks.AIR.defaultBlockState());
            check(!Turrets.valid(p,pos.above()),"Floating placement accepted");
            var weapon=OrdnanceLoadouts.defaults().stream().filter(w->w.id().equals("90")).findFirst().orElseThrow();
            var stack=weapon.stack();stack.getOrCreateTag().putInt("GunGangIssued",40);SecondaryRuntime.configure(p,stack,weapon);
            check(stack.getTag().getInt("GunGangCooldownTicks")==2400,"Cooldown is not 120 seconds");
            p.getInventory().setItem(0,stack);
            var turret=GadgetItems.TURRET_ENTITY.get().create(level);turret.setPos(1,240,1);turret.setup(p);
            check(Turrets.claim(turret)&&level.addFreshEntity(turret),"Could not place turret");
            turret.deployment(stack);
            check(SecondaryRuntime.remaining(p,stack)==0,"Placement started cooldown");
            var duplicate=GadgetItems.TURRET_ENTITY.get().create(level);duplicate.setup(p);
            check(!Turrets.claim(duplicate),"Allowed more than one turret");duplicate.discard();
            check(turret.getHealth()==150&&turret.ammo()==250,"Incorrect health/ammo defaults");
            check(turret.getBoundingBox().getXsize()==2&&turret.getBoundingBox().getYsize()==2&&turret.getBoundingBox().getZsize()==2,"Wrong hitbox");
            var close=target(level,5,1);var far=target(level,9,5);
            turret.getRandom().setSeed(0);close.setDeltaMovement(.1,.2,.3);var velocity=close.getDeltaMovement();
            turret.tick();check(close.getHealth()==1018&&far.getHealth()==1024&&turret.ammo()==249,"Did not instantly hit nearest enemy for 6");
            check(close.getDeltaMovement().equals(velocity),"Turret shot changed target velocity");
            for(int i=0;i<19;i++)turret.tick();check(close.getHealth()==904&&turret.ammo()==230,"Every hitscan round must damage even during hurt immunity");
            float friendlyHealth=close.getHealth();
            var team=server.getScoreboard().addPlayerTeam("turret_test");
            team.setColor(net.minecraft.ChatFormatting.RED);
            server.getScoreboard().addPlayerToTeam(p.getScoreboardName(),team);
            server.getScoreboard().addPlayerToTeam(close.getScoreboardName(),team);
            turret.setup(p);
            check(turret.teamColor()==net.minecraft.ChatFormatting.RED.getColor(),"Marker color does not match installer team");
            for(int i=0;i<5;i++)turret.tick();check(close.getHealth()==friendlyHealth&&far.getHealth()<1024,"Friendly filtering failed");
            close.discard();far.discard();
            var hidden=target(level,5,1);
            for(int y=240;y<243;y++)level.setBlockAndUpdate(new BlockPos(3,y,1),Blocks.STONE.defaultBlockState());
            int before=turret.ammo();for(int i=0;i<8;i++)turret.tick();
            check(turret.ammo()==before&&hidden.getHealth()==1024,"Shot through a wall");hidden.discard();
            for(int y=240;y<243;y++)level.setBlockAndUpdate(new BlockPos(3,y,1),Blocks.AIR.defaultBlockState());
            var outside=target(level,18,1);for(int i=0;i<8;i++)turret.tick();check(turret.ammo()==before,"Shot outside 16 blocks");outside.discard();
            var reloadTag=new net.minecraft.nbt.CompoundTag();turret.addAdditionalSaveData(reloadTag);reloadTag.putInt("Ammo",200);turret.readAdditionalSaveData(reloadTag);
            check(Turrets.canReload(p,turret),"Partial reload was rejected");
            for(int i=0;i<20;i++){server.getWorldData().overworldData().setGameTime(start+i);if(i%10==0)Turrets.key(p,"hold");turret.tick();}
            check(turret.ammo()==220,"Reload must add exactly 20 rounds/second");
            Turrets.key(p,"up");turret.tick();check(turret.ammo()==220,"Reload continued after release");
            p.setPos(20,240,1);Turrets.key(p,"down");turret.tick();check(turret.ammo()==220,"Remote reload accepted");
            p.setPos(0,240,4);
            var stranger=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TurretStranger"));stranger.setPos(0,240,4);
            check(!Turrets.canReload(stranger,turret),"Non-owner can reload");
            turret.setHealth(120);
            check(Turrets.canRepair(p,turret)&&!Turrets.canRepair(stranger,turret),"Repair ownership validation failed");
            int repairAmmo=turret.ammo();
            for(int i=0;i<20;i++){server.getWorldData().overworldData().setGameTime(start+30+i);if(i%10==0)Turrets.key(p,"repair_hold");turret.tick();}
            check(turret.getHealth()==140&&turret.ammo()==repairAmmo,"Repair must heal one per tick without reloading");
            Turrets.key(p,"up");turret.tick();check(turret.getHealth()==140,"Repair continued after release");
            turret.setHealth(149.5f);Turrets.key(p,"repair_down");turret.tick();turret.tick();check(turret.getHealth()==150,"Repair exceeds maximum health");Turrets.key(p,"up");
            check(turret.ownerOutline(p)&&!turret.ownerOutline(stranger),"Outline must be owner-only");
            reloadTag.putInt("Ammo",0);turret.readAdditionalSaveData(reloadTag);
            var gauge=CombatGadgets.hud(p).gauges().stream().filter(g->g.kind().equals("turret_ammo")).findFirst().orElseThrow();
            check(gauge.remaining()==0&&gauge.maximum()==250&&!gauge.timed(),"Empty turret HUD missing");
            var emptyTarget=target(level,5,1);for(int i=0;i<8;i++)turret.tick();
            check(emptyTarget.getHealth()==1024&&turret.ammo()==0,"Empty turret still fires");emptyTarget.discard();
            Turrets.key(p,"down");turret.tick();check(turret.ammo()==1,"Empty turret cannot reload");Turrets.key(p,"up");
            reloadTag.putInt("Ammo",249);turret.readAdditionalSaveData(reloadTag);Turrets.key(p,"down");
            turret.tick();turret.tick();check(turret.ammo()==250,"Reload exceeded magazine capacity");
            reloadTag.putInt("Ammo",100);turret.readAdditionalSaveData(reloadTag);Turrets.key(p,"down");
            server.getWorldData().overworldData().setGameTime(start+100);turret.tick();check(turret.ammo()==100,"Stale held-key packet kept reloading");
            p.setHealth(0);turret.tick();
            check(!turret.isRemoved()&&Turrets.owned(p.getUUID())==turret,"Owner death removed turret");
            p.setHealth(20);p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);turret.tick();
            check(!turret.isRemoved(),"Respawn spectator camera removed turret");
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);turret.tick();
            check(Turrets.owned(p.getUUID())==turret,"Respawn lost ownership");
            float ownerHealth=p.getHealth();turret.hurt(level.damageSources().generic(),150);check(turret.isRemoved()&&Turrets.owned(p.getUUID())==null,"Destruction did not release owner slot");
            check(SecondaryRuntime.remaining(p,stack)==2400,"Destruction must start a fresh 120-second cooldown");
            turret.discard();check(SecondaryRuntime.remaining(p,stack)==2400,"Repeated removal changed cooldown");
            check(p.getHealth()==ownerHealth&&level.getBlockState(pos.below()).is(Blocks.STONE),"Destruction effect caused damage or changed terrain");
            var grounded=GadgetItems.TURRET_ENTITY.get().create(level);grounded.setPos(1,240,1);grounded.setup(p);Turrets.claim(grounded);level.addFreshEntity(grounded);
            level.setBlockAndUpdate(pos.below(),Blocks.AIR.defaultBlockState());grounded.tick();check(grounded.isRemoved(),"Turret floated after floor removal");
            p.setPos(.5,240,4);p.setYRot(180);p.setXRot(0);p.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(GadgetItems.RAPIER.get()));
            var wallPos=new BlockPos(0,241,2);
            level.setBlockAndUpdate(wallPos,GimmickBlocks.GIMMICKS.get("cracked_oak_planks").get().defaultBlockState());
            p.getPersistentData().remove("RapierAttackTick");GimmickEvents.request(p);
            check(GimmickEntity.at(level,wallPos).health()==67,"Adventure rapier attack did not damage wall");
            GimmickEvents.request(p);check(GimmickEntity.at(level,wallPos).health()==67,"Duplicate attack bypassed cooldown");
            level.setBlockAndUpdate(wallPos,GimmickBlocks.GAS.get().defaultBlockState());
            p.getPersistentData().remove("RapierAttackTick");GimmickEvents.request(p);
            check(!level.getBlockState(wallPos).getValue(GimmickBlock.ACTIVE),"Rapier did not explode gas barrel");
            p.setOnGround(true);p.getPersistentData().putInt("RapierEnergy",100);
            GadgetItems.RAPIER.get().use(level,p,net.minecraft.world.InteractionHand.MAIN_HAND);
            check(RapierItem.gauge(p.getMainHandItem())==80,"Dash must cost 20");
            var damage=new net.minecraftforge.event.entity.living.LivingAttackEvent(p,level.damageSources().generic(),10);
            RapierItem.dodge(damage);check(damage.isCanceled(),"Dash immunity missing");
            server.getWorldData().overworldData().setGameTime(level.getGameTime()+RapierItem.DASH_TICKS);
            var expired=new net.minecraftforge.event.entity.living.LivingAttackEvent(p,level.damageSources().generic(),10);
            RapierItem.dodge(expired);check(!expired.isCanceled(),"Dash immunity did not expire");
            level.setBlockAndUpdate(wallPos,GimmickBlocks.UXO.get().defaultBlockState());
            p.getPersistentData().remove("RapierAttackTick");
            p.setXRot((float)Math.toDegrees(Math.atan2(p.getEyeY()-241.06,1.5)));GimmickEvents.request(p);
            check(!level.getBlockState(wallPos).getValue(GimmickBlock.ACTIVE),"Rapier did not trigger UXO");
            System.out.println("GUNGANG_TURRET_SERVER_PASS turret_lifecycle rapier_adventure_gimmicks dash_cost20 immunity_expiry");
        }catch(Throwable error){error.printStackTrace();System.out.println("GUNGANG_TURRET_SERVER_FAIL");}
        finally{server.getWorldData().overworldData().setGameTime(start);server.halt(false);}
    }
}
