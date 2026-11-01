package dev.gungang.acoustics.server;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class ShutterSmokeChecks {
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static ShutterEntity at(ServerLevel level,BlockPos p){return (ShutterEntity)level.getBlockEntity(p);}
    private static void near(double expected,double actual,String label){check(Math.abs(expected-actual)<1e-6,label+": "+actual);}
    private static void tick(ServerLevel level,BlockPos root,long time){level.getServer().getWorldData().overworldData().setGameTime(time);ShutterEntity.tick(level,root,level.getBlockState(root),at(level,root));}
    @SubscribeEvent public static void start(net.minecraftforge.event.server.ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();long before=level.getGameTime();
        try{
            var root=new BlockPos(48,240,48);var block=ShutterBlocks.SHUTTER.get();var state=block.defaultBlockState();
            for(var p:BlockPos.betweenClosed(root.offset(-2,-1,-2),root.offset(4,5,2)))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)level.setBlockAndUpdate(root.offset(x,y,0),state);
            var separate=root.north();level.setBlockAndUpdate(separate,state);
            ShutterEntity.refresh(level,root);ShutterEntity.refresh(level,separate);
            var shape=state.getCollisionShape(level,root,CollisionContext.empty());near(3/16d,shape.max(Direction.Axis.Z)-shape.min(Direction.Axis.Z),"Door thickness");
            check(ShutterBlock.joined(level,root,state,Direction.UP)&&ShutterBlock.joined(level,root,state,Direction.EAST),"Vertical and horizontal joins");
            check(!ShutterBlock.joined(level,root,state,Direction.NORTH),"Parallel plane stays separate");
            var power=root.west();level.setBlockAndUpdate(power,Blocks.REDSTONE_BLOCK.defaultBlockState());
            long now=before+1;server.getWorldData().overworldData().setGameTime(now);ShutterEntity.refresh(level,root);
            check(at(level,root.offset(2,2,0)).powered(),"One signal controls all connected tiles");check(!at(level,separate).powered(),"Neighbor plane not powered through shutter");
            tick(level,root,now+10);near(.5,at(level,root).localBottom(0),"Gradual opening");near(0,at(level,root.above()).localBottom(0),"Upper tile remains closed initially");
            var stored=at(level,root).saveWithoutMetadata();var restored=new ShutterEntity(root,state);restored.load(stored);near(.5,restored.lift(0),"Save/load keeps partial opening");
            level.setBlockAndUpdate(power,Blocks.AIR.defaultBlockState());server.getWorldData().overworldData().setGameTime(++now+10);ShutterEntity.refresh(level,root);double turn=at(level,root).lift(0);
            tick(level,root,now+15);near(turn-.25,at(level,root).lift(0),"Signal reversal continues from partial lift");
            level.setBlockAndUpdate(power,Blocks.REDSTONE_BLOCK.defaultBlockState());server.getWorldData().overworldData().setGameTime(now+16);ShutterEntity.refresh(level,root);
            tick(level,root,now+100);
            var middle=root.east().above();check(state.getCollisionShape(level,middle,CollisionContext.empty()).isEmpty(),"Open doorway has no central collision");
            check(!state.getCollisionShape(level,root,CollisionContext.empty()).isEmpty(),"Outer guide rails remain");
            var secondPower=root.east(3);level.setBlockAndUpdate(secondPower,Blocks.REDSTONE_BLOCK.defaultBlockState());level.setBlockAndUpdate(power,Blocks.AIR.defaultBlockState());
            tick(level,root,now+101);ShutterEntity.refresh(level,root);check(at(level,root).powered(),"Any remaining signal keeps shutter open");
            level.setBlockAndUpdate(secondPower,Blocks.AIR.defaultBlockState());tick(level,root,now+102);ShutterEntity.refresh(level,root);tick(level,root,now+200);near(0,at(level,middle).localBottom(0),"All signals off closes shutter");
            for(int y=0;y<3;y++)level.setBlockAndUpdate(root.offset(1,y,0),Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(power,Blocks.REDSTONE_BLOCK.defaultBlockState());
            tick(level,root,now+201);ShutterEntity.refresh(level,root);ShutterEntity.refresh(level,root.east(2));
            check(at(level,root).powered()&&!at(level,root.east(2)).powered(),"Removing connecting column separates control groups");
            var rotated=state.setValue(ShutterBlock.AXIS,Direction.Axis.X);level.setBlockAndUpdate(root.east(4),rotated);
            shape=rotated.getCollisionShape(level,root.east(4),CollisionContext.empty());near(3/16d,shape.max(Direction.Axis.X)-shape.min(Direction.Axis.X),"Rotated door thickness");
            level.setBlockAndUpdate(power,Blocks.AIR.defaultBlockState());
            var lever=root.south();level.setBlockAndUpdate(lever.below(),Blocks.STONE.defaultBlockState());
            var leverOn=Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACE,net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(net.minecraft.world.level.block.LeverBlock.POWERED,true);
            level.setBlockAndUpdate(lever,leverOn);
            tick(level,root,now+202);ShutterEntity.refresh(level,root);tick(level,root,now+212);
            check(at(level,root).lift(0)>0,"Lever opens shutter before reset");
            GimmickEpoch.reset(server);
            check(!level.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED),"Reset turns lever off");
            near(0,at(level,root).lift(0),"Reset immediately closes moving shutter");
            tick(level,root,now+213);near(0,at(level,root).lift(0),"Scheduled refresh stays closed after reset");
            var chunk=level.getChunkAt(root);
            var data=new net.minecraft.nbt.CompoundTag();
            ShutterReset.save(new net.minecraftforge.event.level.ChunkDataEvent.Save(chunk,level,data));
            check(data.getLong("GunGangShutterReset")==GimmickEpoch.get(level).generation,"Chunk saves reset epoch");
            var wrappedData=new net.minecraft.nbt.CompoundTag();
            ShutterReset.save(new net.minecraftforge.event.level.ChunkDataEvent.Save(new net.minecraft.world.level.chunk.ImposterProtoChunk(chunk,false),level,wrappedData));
            check(wrappedData.equals(data),"Wrapped chunk preserves reset epoch when saved");
            level.setBlockAndUpdate(lever,leverOn);
            ShutterReset.unload(new net.minecraftforge.event.level.ChunkEvent.Unload(chunk));
            ShutterReset.read(new net.minecraftforge.event.level.ChunkDataEvent.Load(chunk,data,net.minecraft.world.level.chunk.ChunkStatus.ChunkType.LEVELCHUNK));
            ShutterReset.load(new net.minecraftforge.event.level.ChunkEvent.Load(chunk,false));
            ShutterReset.catchUp(level,chunk);
            check(level.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED),"Normal chunk reload does not reset lever");
            ShutterReset.unload(new net.minecraftforge.event.level.ChunkEvent.Unload(chunk));
            GimmickEpoch.reset(server);
            check(level.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED),"Unloaded chunk is not force loaded for reset");
            ShutterReset.load(new net.minecraftforge.event.level.ChunkEvent.Load(chunk,false));ShutterReset.catchUp(level,chunk);
            check(!level.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED),"Deferred reset applies when chunk returns");
            near(0,at(level,root).lift(0),"Deferred reset closes shutter");
            System.out.println("GUNGANG_SHUTTER_PASS thickness connection signal gradual reverse persistence collision split rotation reset lever deferred_reload");
        }catch(Throwable error){error.printStackTrace();System.out.println("GUNGANG_SHUTTER_FAIL");}
        finally{server.getWorldData().overworldData().setGameTime(before);server.halt(false);}
    }
}
