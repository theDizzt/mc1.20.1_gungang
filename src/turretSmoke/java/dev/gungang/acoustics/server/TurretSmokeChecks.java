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
            check(stack.getTag().getInt("GunGangCooldownTicks")==4200,"Cooldown is not 210 seconds");
            var turret=GadgetItems.TURRET_ENTITY.get().create(level);turret.setPos(1,240,1);turret.setup(p);
            check(Turrets.claim(turret)&&level.addFreshEntity(turret),"Could not place turret");
            var duplicate=GadgetItems.TURRET_ENTITY.get().create(level);duplicate.setup(p);
            check(!Turrets.claim(duplicate),"Allowed more than one turret");duplicate.discard();
            check(turret.getHealth()==100&&turret.ammo()==250,"Incorrect health/ammo defaults");
            check(turret.getBoundingBox().getXsize()==2&&turret.getBoundingBox().getYsize()==2&&turret.getBoundingBox().getZsize()==2,"Wrong hitbox");
            var close=target(level,5,1);var far=target(level,9,5);
            turret.tick();check(close.getHealth()==1014&&far.getHealth()==1024&&turret.ammo()==249,"Did not hit nearest enemy for 10");
            for(int i=0;i<5;i++)turret.tick();check(close.getHealth()==1004&&turret.ammo()==248,"Rapid fire blocked by vanilla hurt immunity");
            var team=server.getScoreboard().addPlayerTeam("turret_test");
            server.getScoreboard().addPlayerToTeam(p.getScoreboardName(),team);
            server.getScoreboard().addPlayerToTeam(close.getScoreboardName(),team);
            turret.setup(p);
            for(int i=0;i<5;i++)turret.tick();check(close.getHealth()==1004&&far.getHealth()==1014,"Friendly filtering failed");
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
            turret.hurt(level.damageSources().generic(),100);check(turret.isRemoved()&&Turrets.owned(p.getUUID())==null,"Destruction did not release owner slot");
            var grounded=GadgetItems.TURRET_ENTITY.get().create(level);grounded.setPos(1,240,1);grounded.setup(p);Turrets.claim(grounded);level.addFreshEntity(grounded);
            level.setBlockAndUpdate(pos.below(),Blocks.AIR.defaultBlockState());grounded.tick();check(grounded.isRemoved(),"Turret floated after floor removal");
            System.out.println("GUNGANG_TURRET_SERVER_PASS placement nearest_enemy team visibility range damage cooldown reload hud destruction support");
        }catch(Throwable error){error.printStackTrace();System.out.println("GUNGANG_TURRET_SERVER_FAIL");}
        finally{server.getWorldData().overworldData().setGameTime(start);server.halt(false);}
    }
}
