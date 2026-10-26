package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated opt-in integration checks; excluded from release jars. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public class ArrowGrenadeSmokeChecks {
    private static net.minecraft.server.MinecraftServer pending;
    private static int warmup;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        pending=event.getServer();warmup=40;
        // A fresh world's spawn may be far from the fixture. Entity queries only
        // expose loaded sections after their chunk-status callbacks have run.
        for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++)pending.overworld().setChunkForced(x,z,true);
    }
    @SubscribeEvent public static void ready(net.minecraftforge.event.TickEvent.ServerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||pending==null||--warmup>0)return;
        var server=pending;pending=null;run(server);
    }
    private static void run(net.minecraft.server.MinecraftServer server){
        var level=server.overworld();long start=level.getGameTime();
        try {
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=249;y<=253;y++)
                level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            var old=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
            for(var entity:level.getAllEntities())if(entity instanceof ArrowFragmentEntity||entity instanceof ArrowGrenadeEntity||entity instanceof net.minecraft.world.entity.animal.Cow)old.add(entity);
            old.forEach(net.minecraft.world.entity.Entity::discard);
            var owner=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
            owner.setPos(-7,250,-7);level.addNewPlayer(owner);
            var weapon=OrdnanceLoadouts.defaults().stream().filter(w->w.id().equals("79")).findFirst().orElseThrow();
            var stack=weapon.stack();SecondaryRuntime.configure(owner,stack,weapon);
            check(stack.getTag().getInt("GunGangCooldownTicks")==600,"30-second cooldown");
            var origin=new Vec3(0,250,0);
            var grenade=GadgetItems.ARROW_GRENADE_PROJECTILE.get().create(level);
            grenade.setPos(origin);grenade.setOwner(owner);check(level.addFreshEntity(grenade),"Grenade spawn rejected");
            grenade.onHitBlock(new BlockHitResult(origin,Direction.UP,BlockPos.containing(origin),false));
            int count=0;
            for(int t=0;t<=120;t++){
                server.getWorldData().overworldData().setGameTime(start+t);grenade.tick();
                for(var entity:level.getAllEntities())if(entity instanceof ArrowFragmentEntity&&!entity.isRemoved()){count++;entity.discard();}
            }
            check(count==120,"Expected 120 projectiles, got "+count);
            check(grenade.isRemoved(),"Emitter survived 6 seconds");
            var arrow=GadgetItems.ARROW_FRAGMENT.get().create(level);
            arrow.launch(owner,origin,new Vec3(1,0,0),16,6,1.2);level.addFreshEntity(arrow);
            for(int t=0;t<20&&!arrow.isRemoved();t++)arrow.tick();
            check(arrow.isRemoved()&&Math.abs(arrow.getX()-6)<.00001,"Range must end exactly at 6 blocks");
            var cow=EntityType.COW.create(level);cow.setPos(3,250,0);cow.setNoAi(true);cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);level.addFreshEntity(cow);
            for(int shot=0;shot<2;shot++){
                arrow=GadgetItems.ARROW_FRAGMENT.get().create(level);arrow.launch(owner,origin,new Vec3(1,0,0),16,6,1.2);level.addFreshEntity(arrow);
                for(int t=0;t<20&&!arrow.isRemoved();t++)arrow.tick();
            }
            check(cow.getHealth()==68,"Two arrows must each deal 16 damage, health="+cow.getHealth());
            arrow=GadgetItems.ARROW_FRAGMENT.get().create(level);arrow.launch(owner,origin.add(0,.2,.82),new Vec3(1,0,0),16,6,1.2);level.addFreshEntity(arrow);
            for(int t=0;t<20&&!arrow.isRemoved();t++)arrow.tick();
            check(cow.getHealth()==52,"Wider collision margin missed a grazing arrow");
            arrow=GadgetItems.ARROW_FRAGMENT.get().create(level);arrow.launch(owner,origin.add(0,.2,.96),new Vec3(1,0,0),16,6,1.2);level.addFreshEntity(arrow);
            for(int t=0;t<20&&!arrow.isRemoved();t++)arrow.tick();
            check(cow.getHealth()==52,"Collision margin is larger than requested");
            level.setBlockAndUpdate(new BlockPos(1,250,0),Blocks.STONE.defaultBlockState());
            arrow=GadgetItems.ARROW_FRAGMENT.get().create(level);arrow.launch(owner,origin,new Vec3(1,0,0),16,6,1.2);level.addFreshEntity(arrow);
            for(int t=0;t<20&&!arrow.isRemoved();t++)arrow.tick();
            check(arrow.isRemoved()&&cow.getHealth()==52,"Arrow passed through wall");
            level.setBlockAndUpdate(new BlockPos(1,250,0),Blocks.AIR.defaultBlockState());
            cow.setPos(0,250,0);cow.setHealth(100);
            arrow=GadgetItems.ARROW_FRAGMENT.get().create(level);arrow.launch(owner,origin.add(0,.2,0),new Vec3(1,0,0),16,6,1.2);level.addFreshEntity(arrow);
            for(int t=0;t<20&&!arrow.isRemoved();t++)arrow.tick();
            check(cow.getHealth()==84,"Arrow born inside target missed, health="+cow.getHealth());
            cow.setHealth(100);
            arrow=GadgetItems.ARROW_FRAGMENT.get().create(level);arrow.launch(cow,origin.add(0,.2,0),new Vec3(1,0,0),16,6,1.2);level.addFreshEntity(arrow);
            for(int t=0;t<20&&!arrow.isRemoved();t++)arrow.tick();
            check(cow.getHealth()==84,"Grenade owner standing at emitter was immune, health="+cow.getHealth());
            cow.setHealth(100);
            arrow=GadgetItems.ARROW_FRAGMENT.get().create(level);arrow.launch(owner,origin.add(.6,.2,0),new Vec3(1,0,0),16,6,1.2);level.addFreshEntity(arrow);
            for(int t=0;t<20&&!arrow.isRemoved();t++)arrow.tick();
            check(cow.getHealth()==84,"Arrow born inside standard arrow collision margin missed");
            cow.discard();
            var team=server.getScoreboard().getPlayerTeam("arrow_self_test");
            if(team==null)team=server.getScoreboard().addPlayerTeam("arrow_self_test");
            // Forge FakePlayer intentionally rejects all damage; use ServerPlayer with a no-op connection.
            var player=new net.minecraft.server.level.ServerPlayer(server,level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ArrowSelfTest"));
            player.connection=owner.connection;player.setPos(0,250,0);level.addNewPlayer(player);
            team.setAllowFriendlyFire(false);server.getScoreboard().addPlayerToTeam(player.getScoreboardName(),team);
            player.setHealth(20);player.getAbilities().invulnerable=false;
            for(int t=0;t<80;t++)player.tick();
            arrow=GadgetItems.ARROW_FRAGMENT.get().create(level);arrow.launch(player,player.position().add(0,.2,0),new Vec3(1,0,0),16,6,1.2);level.addFreshEntity(arrow);
            arrow.tick();
            check(player.getHealth()==4,"Player owner at center must take self damage even with team friendly fire disabled, health="+player.getHealth());
            System.out.println("ARROW_GRENADE_SMOKE_PASS: cooldown, 120 emissions, 6 seconds, range, damage, wall collision, center overlap, self damage, widened hit margin");
        } catch(Throwable failure){failure.printStackTrace();System.out.println("ARROW_GRENADE_SMOKE_FAIL");}
        finally{for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++)level.setChunkForced(x,z,false);server.getWorldData().overworldData().setGameTime(start);server.halt(false);}
    }
}
