package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.DEDICATED_SERVER)
public final class MotionSmokeChecks {
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();long time=level.getGameTime();
        try {
            var origin=new BlockPos(48,240,48);var block=MotionBlocks.CONVEYOR.get();
            for(var p:BlockPos.betweenClosed(origin.offset(-3,-1,-3),origin.offset(8,20,8)))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
            var route=new BlockPos[]{origin,origin.south(),origin.south(2),origin.south(2).east(),origin.south(2).east(2)};
            for(var p:route)level.setBlockAndUpdate(p,block.defaultBlockState());
            for(int pass=0;pass<8;pass++)for(var p:route)block.tick(level.getBlockState(p),level,p,level.random);
            check(level.getBlockState(route[2]).getValue(ConveyorBlock.SHAPE)==RailShape.NORTH_EAST,"Automatic corner connection");
            for(var p:route){var s=level.getBlockState(p);check(s.getShape(level,p).max(Direction.Axis.Y)==.5,"Bottom half height");}
            var top=origin.east(5);level.setBlockAndUpdate(top,block.defaultBlockState().setValue(ConveyorBlock.HALF,Half.TOP));
            check(level.getBlockState(top).getShape(level,top).min(Direction.Axis.Y)==.5,"Top half height");
            var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);player.setPos(origin.getX()+.5,240.5,origin.getZ()+.5);player.getAbilities().mayBuild=true;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(MotionBlocks.WRENCH.get()));
            ConveyorWrench.apply(player,origin,30,false);
            for(var p:route)check(((ConveyorEntity)level.getBlockEntity(p)).speed==30,"Connected speed setting");
            for(int i=0;i<route.length-1;i++){var d=Direction.fromDelta(route[i+1].getX()-route[i].getX(),0,route[i+1].getZ()-route[i].getZ());check(ConveyorBlock.output(level.getBlockState(route[i]))==d,"Forward flow at "+i);}
            ConveyorWrench.apply(player,origin,1,true);
            for(int i=1;i<route.length;i++){var d=Direction.fromDelta(route[i-1].getX()-route[i].getX(),0,route[i-1].getZ()-route[i].getZ());check(ConveyorBlock.output(level.getBlockState(route[i]))==d,"Reverse flow at "+i);}
            check(((ConveyorEntity)level.getBlockEntity(top)).speed==10,"Separate height must not change");
            var be=(ConveyorEntity)level.getBlockEntity(origin);var saved=be.saveWithoutMetadata();var restored=new ConveyorEntity(origin,level.getBlockState(origin));restored.load(saved);check(restored.speed==1,"NBT speed persistence");
            ConveyorWrench.apply(player,origin,30,true);
            var item=new ItemEntity(level,origin.getX()+.5,240.5,origin.getZ()+.4,new ItemStack(Items.STONE));item.setDeltaMovement(0,0,0);item.setOnGround(true);level.addFreshEntity(item);
            double start=item.getZ();ConveyorEntity.tick(level,origin,level.getBlockState(origin),be);check(item.getZ()>start+.25,"Item carried at 3x");
            double moved=item.getZ();ConveyorEntity.tick(level,origin,level.getBlockState(origin),be);check(item.getZ()==moved,"No duplicate movement per tick");item.discard();
            for(int speed:new int[]{1,10,30}){
                ConveyorWrench.apply(player,origin,speed,false);
                var cargo=new ItemEntity(level,origin.getX()+.5,240.5,origin.getZ()+.3,new ItemStack(Items.STONE));cargo.setDeltaMovement(0,0,0);level.addFreshEntity(cargo);
                boolean reached=false;
                for(int tick=0;tick<700;tick++){
                    server.getWorldData().overworldData().setGameTime(time+tick+1);cargo.setOnGround(true);
                    for(var p:route)ConveyorEntity.tick(level,p,level.getBlockState(p),(ConveyorEntity)level.getBlockEntity(p));
                    if(cargo.getX()>origin.getX()+2.1){reached=true;break;}
                }
                check(reached&&Math.abs(cargo.getZ()-(origin.getZ()+2.5))<.3,"Cargo follows corner at speed "+speed);cargo.discard();
            }
            for(var pad:new JumpPadBlock[]{MotionBlocks.BLUE.get(),MotionBlocks.LIME.get()}){
                var p=origin.east(6);level.setBlockAndUpdate(p,pad.defaultBlockState());level.setBlockAndUpdate(p.south(),pad.defaultBlockState());
                check(level.getBlockState(p).getValue(JumpPadBlock.SOUTH),"Matching pads join");
                level.setBlockAndUpdate(p.south(),(pad==MotionBlocks.BLUE.get()?MotionBlocks.LIME:MotionBlocks.BLUE).get().defaultBlockState());
                check(!level.getBlockState(p).getValue(JumpPadBlock.SOUTH),"Different colors retain edge");
                var rider=EntityType.COW.create(level);rider.setPos(p.getX()+.5,p.getY()+.125,p.getZ()+.5);
                JumpPadBlock.jump(new LivingEvent.LivingJumpEvent(rider));double peak=rider.getY(),base=rider.getY();
                for(int tick=0;tick<60;tick++){rider.travel(net.minecraft.world.phys.Vec3.ZERO);peak=Math.max(peak,rider.getY());}
                check(Math.abs((peak-base)-pad.height)<.03,"Actual vanilla travel peak: "+(peak-base)+" expected "+pad.height);rider.discard();
            }
            com.mojang.logging.LogUtils.getLogger().info("GUNGANG_MOTION_SMOKE_PASS connection=corner direction=both speed=range persistence=pass movement=pass jump=8,16");
        }catch(Throwable error){com.mojang.logging.LogUtils.getLogger().error("GUNGANG_MOTION_SMOKE_FAIL",error);}
        finally{server.getWorldData().overworldData().setGameTime(time);server.halt(false);}
    }
}
