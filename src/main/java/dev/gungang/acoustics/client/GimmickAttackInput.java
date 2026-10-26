package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class GimmickAttackInput {
    @SubscribeEvent(receiveCanceled=true) public static void attack(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event){
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(!event.isAttack()||mc.player==null||mc.level==null||mc.screen!=null)return;
        if(mc.player.getMainHandItem().getItem() instanceof RapierItem){
            if(event.isCanceled())return;
            GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("rapierAttack","",""));
            event.setCanceled(true);event.setSwingHand(false);
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }
        if(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof GimmickBlock){
            GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("gimmickAttack","",""));
        }
    }
}
