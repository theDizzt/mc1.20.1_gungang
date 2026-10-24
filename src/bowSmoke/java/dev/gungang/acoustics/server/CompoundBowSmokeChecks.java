package dev.gungang.acoustics.server;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Opt-in real-mod compatibility checks, excluded from release jars. */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class CompoundBowSmokeChecks {
    private static ServerLevel level;
    private static ServerPlayer player;
    private static ItemStack bow;
    private static Class<?> arrows,mechanics;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static java.lang.reflect.Method method(Class<?> type,String name,Class<?>... args)throws Exception{
        var m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;
    }
    private static Projectile arrow(boolean aimed,float charge)throws Exception{
        mechanics.getMethod("aim",ServerPlayer.class,boolean.class).invoke(null,player,aimed);
        var type=ForgeRegistries.ENTITY_TYPES.getValues().stream().filter(t->{var key=ForgeRegistries.ENTITY_TYPES.getKey(t);return key!=null&&key.getNamespace().equals("tacticalmilitarybow")&&t.create(level) instanceof Projectile;}).findFirst().orElseThrow();
        var arrow=(Projectile)type.create(level);
        arrows.getMethod("prepare",ServerPlayer.class,ItemStack.class,float.class).invoke(arrow,player,bow,charge);
        arrow.setPos(.5,101.5,.5);arrow.xOld=.5;arrow.yOld=101.5;arrow.zOld=.5;
        check(arrow instanceof CompoundBow.ArrowAccess,"Optional arrow mixin applied");
        return arrow;
    }
    private static void fire(Projectile arrow){
        arrow.setDeltaMovement(0,0,8);level.addFreshEntity(arrow);arrow.tick();
    }
    private static void hit(Projectile arrow,Entity victim,boolean head)throws Exception{
        var hit=Class.forName(arrows.getName()+"$Hit");var ctor=hit.getDeclaredConstructor(Entity.class,Vec3.class,boolean.class);ctor.setAccessible(true);
        method(arrows,"hitEntity",hit).invoke(arrow,ctor.newInstance(victim,victim.getBoundingBox().getCenter(),head));
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        var server=event.getServer();server.execute(()->{
            try{
                level=server.overworld();
                arrows=Class.forName("com.korkuluk.standalonebow.BowArrowEntity");mechanics=Class.forName("com.korkuluk.standalonebow.BowMechanics");
                for(int x=-2;x<=3;x++)for(int z=-2;z<=12;z++)for(int y=100;y<=104;y++)level.setBlock(new BlockPos(x,y,z),y==100?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BowSmoke");
                player=new ServerPlayer(server,level,profile);player.connection=net.minecraftforge.common.util.FakePlayerFactory.get(level,profile).connection;
                player.setPos(.5,101,.5);player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);level.addNewPlayer(player);
                var config=Files.readString(Path.of("../../server_resource/config/gungang/server/weapons/26_compound-bow.json"));
                var weapon=ServerFiles.GSON.fromJson(config,Loadouts.Weapon.class);weapon.validate(true);
                check(weapon.id().equals("26"),"Weapon ID 26");
                player.getInventory().setItem(7,new ItemStack(Items.DIAMOND,3));
                method(LobbyManager.class,"grantFree",ServerPlayer.class,Loadouts.Weapon.class,int.class).invoke(null,player,weapon,0);
                bow=player.getMainHandItem();
                check(CompoundBow.isBow(bow)&&bow.getTag().getString("ArrowType").equals("piercing"),"Requested bow and NBT granted");
                check(CompoundBow.issuedAmmo(player.getInventory().getItem(7))&&player.getInventory().getItem(7).getCount()==1,"Slot 8 gets one managed arrow");
                check(player.getInventory().countItem(Items.DIAMOND)==3,"Existing slot item preserved");
                var consume=method(mechanics,"consumeAmmo",ServerPlayer.class);
                for(int i=0;i<10;i++)consume.invoke(null,player);
                check(player.getInventory().getItem(7).getCount()==1,"Actual reload consumption never consumes supplied arrow");
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));CompoundBow.syncAmmo(player);
                check(player.getInventory().getItem(7).isEmpty(),"Changing held weapon removes supplied ammo");
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,bow);CompoundBow.syncAmmo(player);
                System.out.println("COMPOUND_BOW_AMMO_PASS");
                for(boolean aimed:new boolean[]{false,true})for(boolean head:new boolean[]{false,true}){
                    var target=EntityType.COW.create(level);target.setPos(8,101,8);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);level.addFreshEntity(target);
                    var shot=arrow(aimed,1);hit(shot,target,head);
                    float expected=(aimed?42:32)*(head?1.5f:1);
                    check(Math.abs(1000-target.getHealth()-expected)<.01,"Actual hit damage expected "+expected+" got "+(1000-target.getHealth()));
                    target.discard();shot.discard();
                }
                var partial=arrow(true,.5f);check(Math.abs(((CompoundBow.ArrowAccess)partial).gungang$shotDamage()-21)<.01,"Partial draw scales damage");partial.discard();
                System.out.println("COMPOUND_BOW_DAMAGE_PASS 32 48 42 63");
                var stone=new BlockPos(0,101,4);level.setBlock(stone,Blocks.STONE.defaultBlockState(),3);
                var stuck=arrow(false,1);level.addFreshEntity(stuck);((CompoundBow.ArrowAccess)stuck).gungang$embed(new BlockHitResult(new Vec3(.5,101,4),net.minecraft.core.Direction.NORTH,stone,false));
                for(int i=0;i<CompoundBow.WALL_ARROW_TICKS;i++)stuck.tick();
                check(!stuck.isRemoved(),"Wall arrow stays for 15 seconds");
                stuck.tick();check(stuck.isRemoved(),"Wall arrow vanishes after 15 seconds");
                level.removeBlock(stone,false);
                System.out.println("COMPOUND_BOW_WALL_EXPIRY_PASS");
                var face=new BlockHitResult(new Vec3(.5,101.5,4),net.minecraft.core.Direction.NORTH,new BlockPos(0,101,4),false);
                var impacts=Class.forName("com.korkuluk.standalonebow.BlockImpactHandler");
                for(var state:new net.minecraft.world.level.block.state.BlockState[]{Blocks.GLASS.defaultBlockState(),Blocks.GLASS_PANE.defaultBlockState(),Blocks.OAK_PLANKS.defaultBlockState(),Blocks.ICE.defaultBlockState()})
                    for(int i=0;i<20;i++){
                        level.setBlock(face.getBlockPos(),state,3);var shot=arrow(false,1);level.addFreshEntity(shot);
                        method(impacts,"onHit",arrows,BlockHitResult.class).invoke(null,shot,face);
                        method(impacts,"igniteFlameArrow",arrows,BlockHitResult.class).invoke(null,shot,face);
                        method(impacts,"ignite",arrows,BlockHitResult.class).invoke(null,shot,face);
                        check(level.getBlockState(face.getBlockPos())==state,"Arrow left "+state+" intact");
                        check(level.getBlockState(face.getBlockPos().north()).isAir(),"Arrow set no fire");shot.discard();
                    }
                level.removeBlock(face.getBlockPos(),false);
                System.out.println("COMPOUND_BOW_MAP_PROTECTION_PASS glass pane wood ice fire");
                level.setBlock(face.getBlockPos(),Blocks.STONE.defaultBlockState(),3);
                var loose=arrow(false,1);level.addFreshEntity(loose);((CompoundBow.ArrowAccess)loose).gungang$embed(face);
                var recoverable=arrows.getDeclaredField("recoverable");recoverable.setAccessible(true);recoverable.setBoolean(loose,true);
                level.removeBlock(face.getBlockPos(),false);
                for(int i=0;i<5&&!loose.isRemoved();i++)loose.tick();
                var area=new net.minecraft.world.phys.AABB(face.getBlockPos()).inflate(3);
                check(loose.isRemoved(),"Arrow in a removed block goes away");
                check(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).isEmpty(),"Arrow in a removed block drops no item");
                var item=new net.minecraft.world.entity.item.ItemEntity(level,.5,101,4,new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("tacticalmilitarybow","flame_arrow"))));
                check(!level.addFreshEntity(item)&&level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).isEmpty(),"Bow arrow items never enter the world");
                System.out.println("COMPOUND_BOW_NO_ARROW_DROP_PASS");
                var wall=GadgetItems.DEPLOYABLE.get().create(level);wall.setPos(.5,101,4.5);wall.setup(player,true,false);level.addFreshEntity(wall);
                float hp=wall.getHealth();var shot=arrow(false,1);fire(shot);
                check(wall.getHealth()<hp,"Real arrow flight damages barrier");wall.discard();shot.discard();
                var mine=GadgetItems.DEPLOYABLE.get().create(level);mine.setPos(.5,101,4.5);mine.setup(player,false,false);level.addFreshEntity(mine);
                shot=arrow(false,1);shot.setPos(.5,101.08,.5);fire(shot);
                check(mine.isRemoved()||mine.getHealth()<20,"Real arrow flight damages mine");mine.discard();shot.discard();
                var pos=new BlockPos(0,101,4);var block=GimmickBlocks.GIMMICKS.get("cracked_oak_planks").get();level.setBlock(pos,block.defaultBlockState(),3);
                var gimmick=GimmickEntity.at(level,pos);GimmickEntity.hit(level,pos,1);double before=gimmick.health();
                shot=arrow(false,1);fire(shot);
                check(Math.abs(before-gimmick.health()-32)<.01,"Arrow deals exactly one hit to breakable GunGang block");
                shot.discard();level.removeBlock(pos,false);
                System.out.println("COMPOUND_BOW_TARGETS_PASS barrier mine block");
                shot=arrow(false,1);level.addFreshEntity(shot);((CompoundBow.ArrowAccess)shot).gungang$embed(new BlockHitResult(new Vec3(.5,101,4),net.minecraft.core.Direction.NORTH,pos,false));
                var saved=shot.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                check(server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gungang cleardeployables")==1,"Cleanup command succeeds");
                check(shot.isRemoved(),"Cleanup removes embedded arrow");
                var stale=arrow(false,1);stale.load(saved);
                var join=new net.minecraftforge.event.entity.EntityJoinLevelEvent(stale,level,true);CompoundBow.joined(join);
                check(join.isCanceled(),"Previously unloaded arrow is invalidated by reset epoch");stale.discard();
                shot=arrow(false,1);level.addFreshEntity(shot);
                check(server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gungang reset")==1&&shot.isRemoved(),"Reset removes arrows");
                MatchManager.prepare(server,ArenaMap.example(),null);
                shot=arrow(false,1);level.addFreshEntity(shot);MatchManager.start();check(shot.isRemoved(),"Match start clears arrows");
                shot=arrow(false,1);level.addFreshEntity(shot);MatchManager.end("ABORTED");check(shot.isRemoved(),"Match end clears arrows");
                System.out.println("COMPOUND_BOW_CLEANUP_PASS command reset start end unloaded");
                System.out.println("GUNGANG_COMPOUND_BOW_SMOKE_PASS");
            }catch(Throwable failure){failure.printStackTrace();System.out.println("GUNGANG_COMPOUND_BOW_SMOKE_FAIL");}
            finally{server.halt(false);}
        });
    }
}
