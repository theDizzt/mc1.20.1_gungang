package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class GimmickAttackInput {
    private static long lastThrust=Long.MIN_VALUE/2;
    @SubscribeEvent(receiveCanceled=true) public static void attack(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event){
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(!event.isAttack()||mc.player==null||mc.level==null||mc.screen!=null)return;
        if(mc.player.getMainHandItem().getItem() instanceof RapierItem){
            if(event.isCanceled())return;
            event.setCanceled(true);event.setSwingHand(false);
            // Thrust only as often as the server deals one, so every thrust the player sees is a real attack.
            long now=mc.level.getGameTime();
            if(now-lastThrust>=0&&now-lastThrust<RapierItem.THRUST_TICKS)return;
            lastThrust=now;
            // The entity under the crosshair on this client: the server accepts it when its own ray misses by lag.
            String aimed=mc.crosshairPickEntity!=null?Integer.toString(mc.crosshairPickEntity.getId()):"";
            GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("rapierAttack",aimed,""));
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }
        if(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof GimmickBlock){
            GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("gimmickAttack","",""));
        }
    }
}
