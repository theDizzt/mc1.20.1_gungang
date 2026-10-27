package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runs against transformed Minecraft classes, including liquid-container interface dispatch. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class AcidSmokeChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();
        try {
            var pos=new BlockPos(0,250,0);
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)for(int y=249;y<=252;y++)
                level.setBlockAndUpdate(new BlockPos(x,y,z),y==249?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
            var acid=GimmickBlocks.ACID_SOURCE.get();var flowing=GimmickBlocks.ACID_FLOWING.get();
            for(var block:new Block[]{Blocks.OAK_FENCE,Blocks.STONE_STAIRS,Blocks.GLASS_PANE,Blocks.IRON_BARS}){
                var dry=block.defaultBlockState();
                check(!AcidLogging.acid(dry),"Default must be dry: "+block);
                var container=(LiquidBlockContainer)block;
                check(container.canPlaceLiquid(level,pos,dry,flowing),"Flowing acid rejected: "+block);
                level.setBlockAndUpdate(pos,dry);
                check(container.placeLiquid(level,pos,dry,flowing.getFlowing(7,false)),"Acid placement failed");
                var wet=level.getBlockState(pos);
                check(wet.getBlock()==block&&AcidLogging.acid(wet)&&wet.getFluidState().getType()==acid,"Logged fluid lost");
                check(!container.canPlaceLiquid(level,pos,wet,Fluids.WATER),"Water mixed with acid");
                check(((BucketPickup)block).pickupBlock(level,pos,wet).is(GimmickBlocks.ACID_BUCKET.get()),"Wrong bucket");
                check(!AcidLogging.acid(level.getBlockState(pos)),"Pickup left acid behind");
                check(container.placeLiquid(level,pos,dry,Fluids.WATER.getSource(false)),"Vanilla waterlogging broken");
                check(!container.canPlaceLiquid(level,pos,level.getBlockState(pos),acid),"Acid replaced water");
                level.setBlockAndUpdate(pos,flowing.getFlowing(5,false).createLegacyBlock());
                check(AcidLogging.acid(AcidLogging.placement(dry,level,pos)),"Block placement into flowing acid failed");
            }
            level.setBlockAndUpdate(pos,flowing.getFlowing(7,false).createLegacyBlock());
            level.setBlockAndUpdate(pos.west(),acid.getSource(false).createLegacyBlock());
            level.setBlockAndUpdate(pos.east(),acid.getSource(false).createLegacyBlock());
            flowing.tick(level,pos,level.getFluidState(pos));
            check(level.getFluidState(pos).isSource(),"Two sources failed to merge");
            var cow=net.minecraft.world.entity.EntityType.COW.create(level);
            cow.setPos(0,252,4);cow.setNoAi(true);cow.setNoGravity(true);
            cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);
            cow.setHealth(17);cow.invulnerableTime=20;
            cow.addEffect(new net.minecraft.world.effect.MobEffectInstance(GimmickBlocks.ACID_POISON.get(),60));
            cow.tick();check(cow.getHealth()==9,"Each acid tick must deal exactly 8 damage; health="+cow.getHealth());
            for(int i=0;i<19;i++)cow.tick();
            check(cow.getHealth()==9,"Acid must wait 20 ticks between hits");
            cow.tick();check(cow.getHealth()==1,"Second acid hit must leave 1 health");
            for(int i=0;i<20;i++)cow.tick();
            check(!cow.isAlive(),"Acid must kill at 1 health instead of stopping");
            var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
            player.setHealth(10);
            net.minecraft.world.effect.MobEffects.REGENERATION.applyEffectTick(player,4);
            check(player.getHealth()==10,"Regeneration potion must not stack with passive recovery");
            player.heal(2);check(player.getHealth()==12,"Explicit healing items must still work");
            var healthyCow=net.minecraft.world.entity.EntityType.COW.create(level);healthyCow.setHealth(4);
            net.minecraft.world.effect.MobEffects.REGENERATION.applyEffectTick(healthyCow,0);
            check(healthyCow.getHealth()==5,"Vanilla mob regeneration must remain intact");
            var rapier=new net.minecraft.world.item.ItemStack(GadgetItems.RAPIER.get());
            player.getPersistentData().putInt("RapierEnergy",37);
            ((RapierItem)rapier.getItem()).inventoryTick(rapier,level,player,0,false);
            check(RapierItem.gauge(rapier)==37,"Newly acquired rapier must inherit player energy");
            player.getPersistentData().putInt("RapierEnergy",100);
            ((RapierItem)rapier.getItem()).inventoryTick(rapier,level,player,0,false);
            check(RapierItem.gauge(rapier)==100,"Rapier energy must update after recovery");
            player.removeAllEffects();AcidBlock.poison(level,player);
            var poison=player.getEffect(GimmickBlocks.ACID_POISON.get());
            check(poison!=null,"Acid contact effect missing");
            int duration=poison.getDuration();AcidBlock.poison(level,player);
            check(player.getEffect(GimmickBlocks.ACID_POISON.get())==poison&&poison.getDuration()==duration,"Duplicate acid contact changed effect phase");
            System.out.println("OPTIMIZATION_SMOKE_PASS: regeneration isolation, explicit healing, rapier energy, duplicate acid contact");
            System.out.println("ACID_SMOKE_PASS: flowing logging, bucket pickup, water isolation, source merge, 8 damage every 20 ticks, lethal at 1 health");
        }catch(Throwable failure){failure.printStackTrace();System.out.println("ACID_SMOKE_FAIL");}
        finally {server.halt(false);}
    }
}
