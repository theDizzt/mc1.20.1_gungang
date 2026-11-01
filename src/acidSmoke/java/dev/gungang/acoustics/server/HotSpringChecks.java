package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

final class HotSpringChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run(ServerLevel level){
        var pos=new BlockPos(0,240,0);var water=HotSpringContent.SOURCE.get();
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)for(int y=239;y<249;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),y==239?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        for(var block:new Block[]{Blocks.OAK_FENCE,Blocks.GLASS_PANE,Blocks.IRON_BARS,Blocks.STONE_STAIRS,Blocks.STONE_SLAB}){
            var dry=block.defaultBlockState();check(!AcidLogging.hot(dry),"Dry default: "+block);
            var container=(LiquidBlockContainer)block;level.setBlockAndUpdate(pos,dry);
            check(container.canPlaceLiquid(level,pos,dry,water)&&container.placeLiquid(level,pos,dry,water.getSource(false)),"Spring logging: "+block);
            var wet=level.getBlockState(pos);check(AcidLogging.hot(wet)&&wet.getFluidState().getType()==water,"Spring identity preserved: "+block);
            check(!container.canPlaceLiquid(level,pos,wet,Fluids.WATER)&&!container.canPlaceLiquid(level,pos,wet,GimmickBlocks.ACID_SOURCE.get()),"No fluid mixing");
            check(((BucketPickup)block).pickupBlock(level,pos,wet).is(HotSpringContent.BUCKET.get()),"Vanilla bucket picks up spring water");
            check(level.getBlockState(pos).getBlock()==block&&!AcidLogging.hot(level.getBlockState(pos)),"Bucket preserves block");
            level.setBlockAndUpdate(pos,water.getSource(false).createLegacyBlock());
            check(AcidLogging.hot(AcidLogging.placement(dry,level,pos)),"Placing a block into spring logs it");
        }
        var doubleSlab=Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.DOUBLE);
        check(!((LiquidBlockContainer)Blocks.STONE_SLAB).canPlaceLiquid(level,pos,doubleSlab,water),"Double slabs reject fluid");
        level.setBlockAndUpdate(pos,HotSpringContent.FLOWING.get().getFlowing(7,false).createLegacyBlock());
        level.setBlockAndUpdate(pos.west(),water.getSource(false).createLegacyBlock());level.setBlockAndUpdate(pos.east(),water.getSource(false).createLegacyBlock());
        HotSpringContent.FLOWING.get().tick(level,pos,level.getFluidState(pos));check(level.getFluidState(pos).isSource(),"Two spring sources merge into infinite source");
        check(HotSpringContent.WATER.get().pickupBlock(level,pos,level.getBlockState(pos)).is(HotSpringContent.BUCKET.get()),"Source bucket pickup");
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"SpringCheck"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(.5,244,.5);player.setHealth(10);level.addNewPlayer(player);
        try{
            HotSpringEffect.touch(player);check(player.hasEffect(HotSpringContent.HEALING.get()),"Water grants healing status");
            HotSpringContent.HEALING.get().applyEffectTick(player,0);check(player.getHealth()==11,"Healing restores one health");
            player.setRemainingFireTicks(200);HotSpringEffect.touch(player);HotSpringContent.HEALING.get().applyEffectTick(player,0);
            check(!player.hasEffect(HotSpringContent.HEALING.get())&&player.getHealth()==11,"Burning blocks healing");
            check(!HotSpringContent.TYPE.get().canExtinguish(player),"Spring water cannot extinguish burn restriction");
            player.clearFire();
            var start=new Vec3(-2,244.6,.5);var end=new Vec3(2,244.6,.5);
            ThermalEmitterEntity.spray(level,start,end,false);check(player.hasEffect(HotSpringContent.HEALING.get()),"Nozzle contact heals");player.removeEffect(HotSpringContent.HEALING.get());
            level.setBlockAndUpdate(new BlockPos(-1,244,0),Blocks.STONE.defaultBlockState());ThermalEmitterEntity.spray(level,start,end,false);check(!player.hasEffect(HotSpringContent.HEALING.get()),"Spray cannot heal through walls");
            level.setBlockAndUpdate(new BlockPos(-1,244,0),Blocks.AIR.defaultBlockState());ThermalEmitterEntity.spray(level,start,end,true);
            check(player.getRemainingFireTicks()==200&&!player.hasEffect(HotSpringContent.HEALING.get()),"Geyser contact causes ten-second burn");
            var bounds=ThermalEmitterEntity.plume(pos);check(bounds.getXsize()==3&&bounds.getYsize()==8&&bounds.getZsize()==3,"Geyser is 3 x 8 x 3");
            var geyser=HotSpringContent.GEYSER.get();level.setBlockAndUpdate(pos,geyser.defaultBlockState());geyser.setPlacedBy(level,pos,geyser.defaultBlockState(),player,net.minecraft.world.item.ItemStack.EMPTY);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)check(level.getBlockState(pos.offset(x,0,z)).is(geyser),"Geyser base occupies 3 x 3");
            level.destroyBlock(pos.east(),false);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)check(!level.getBlockState(pos.offset(x,0,z)).is(geyser),"Geyser dismantles as one structure");
            System.out.println("GUNGANG_HOT_SPRING_PASS logging slab bucket infinite_source healing burn nozzle obstruction geyser_size structure");
        }finally{level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);}
    }
}
