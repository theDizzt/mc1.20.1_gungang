package dev.gungang.acoustics.server;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Runs the actual item/server state machine and the shared local-player collision movement in an isolated world. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class AirAssaultSmokeChecks {
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var server=event.getServer();server.execute(()->{
            try {run(server);System.out.println("GUNGANG_AIR_ASSAULT_SMOKE_PASS");}
            catch(Throwable error) {error.printStackTrace();System.out.println("GUNGANG_AIR_ASSAULT_SMOKE_FAIL");}
            finally {server.halt(false);}
        });
    }
    private static ServerPlayer player(net.minecraft.server.level.ServerLevel level,String name,double x,double z) throws Exception {
        var p=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),name)) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {return false;}
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) {return true;}
            @Override public boolean isControlledByLocalInstance() {return true;}
        };
        p.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);p.setHealth(100);p.setPos(x,241,z);
        var spawnProtection=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");spawnProtection.setAccessible(true);spawnProtection.setInt(p,0);
        p.invulnerableTime=0;level.addNewPlayer(p);return p;
    }
    private static void run(net.minecraft.server.MinecraftServer server) throws Exception {
        var level=server.overworld();long start=level.getGameTime();
        for(var pos:BlockPos.betweenClosed(new BlockPos(20,240,20),new BlockPos(50,255,50)))
            level.setBlock(pos,pos.getY()==240?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        var owner=player(level,"AssaultOwner",30.5,30.5);
        var enemy=player(level,"AssaultEnemy",23.5,30.5);
        var ally=player(level,"AssaultAlly",23.5,31.5);
        var outer=player(level,"AssaultOuter",28.5,30.5);
        var outside=player(level,"AssaultOutside",29.5,30.5);
        var board=server.getScoreboard();var team=board.addPlayerTeam("assault_test");
        board.addPlayerToTeam(owner.getScoreboardName(),team);board.addPlayerToTeam(ally.getScoreboardName(),team);
        var bot=BotEntity.TYPE.get().create(level);bot.setPos(23.5,241,29.5);
        bot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);bot.setHealth(100);level.addFreshEntity(bot);
        var monster=net.minecraft.world.entity.EntityType.SKELETON.create(level);monster.setPos(22.5,241,30.5);
        monster.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);monster.setHealth(100);level.addFreshEntity(monster);
        var stack=new net.minecraft.world.item.ItemStack(GadgetItems.AIR_ASSAULT.get());
        stack.getOrCreateTag().putInt("GunGangIssued",40);
        SecondaryRuntime.configure(owner,stack,OrdnanceLoadouts.defaults().stream().filter(w->w.id().equals("77")).findFirst().orElseThrow());
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        stack.use(level,owner,net.minecraft.world.InteractionHand.MAIN_HAND);
        check(SecondaryRuntime.remaining(owner,stack)==800,"Right click must commit 40-second cooldown");
        check(owner.getY()==241,"Activation must not teleport");
        double previous=owner.getY(),peak=previous;
        for(int tick=1;tick<=48;tick++) {
            // Replay the server-authored motion through the same collision step used by the client mixin.
            AirAssaultMotion.receive(owner,true,owner.getDeltaMovement());
            owner.travel(new Vec3(1,0,1)); // Real travel mixin must override movement input and vanilla gravity.
            check(owner.getY()-previous<=.5001,"Rise must be gradual");
            peak=Math.max(peak,owner.getY());previous=owner.getY();
            if(tick==25){owner.setYRot(90);owner.setXRot(0);}
            server.getWorldData().overworldData().setGameTime(start+tick);
            AirAssault.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,owner));
            if(tick<40)check(enemy.getHealth()==100,"No damage before dive");
            if(!owner.isNoGravity())break;
        }
        check(Math.abs(peak-246)<.01,"Five-block ascent: "+peak);
        check(Math.abs(owner.getX()-23.5)<.1&&Math.abs(owner.getZ()-30.5)<.1,"Dive follows changed facing: "+owner.position());
        check(owner.getY()==241&&!owner.isNoGravity(),"Landed and restored gravity");
        check(owner.getYRot()==90&&owner.getXRot()==0,"Movement must not overwrite camera");
        check(enemy.getHealth()==40,"Enemy player receives 60 damage: "+enemy.getHealth());
        check(bot.getHealth()==40,"Enemy bot receives 60 damage: "+bot.getHealth());
        check(monster.getHealth()==40,"Hostile monster receives 60 damage: "+monster.getHealth());
        check(ally.getHealth()==100&&ally.getDeltaMovement().y==0,"Ally unaffected");
        check(outer.getHealth()==100&&outer.getDeltaMovement().y>1,"Outer area launches without damage");
        check(outside.getHealth()==100&&outside.getDeltaMovement().y==0,"Outside 10x10 unaffected");
        var motion=enemy.getDeltaMovement();double height=0;
        for(double v=motion.y;v>0;v=(v-.08)*.98)height+=v;
        check(Math.abs(height-10)<.001,"Launch apex ten blocks");
        AirAssault.start(owner,stack);owner.setHealth(0);
        AirAssault.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,owner));
        check(!owner.isNoGravity()&&enemy.getHealth()==40,"Death before landing cancels effect");
        System.out.println("ASSAULT_CHECKS ascent=5 gradual=true aim=free damage=60 ally=safe launch=10 death=cancel cooldown=800");
    }
}
