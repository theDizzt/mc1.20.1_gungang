package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.ToeItem;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class ToeInput {
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event){
        var player=Minecraft.getInstance().player;if(player==null)return;
        if(ToeItem.is(player.getMainHandItem())||ToeItem.blocksSecondary(player,player.getMainHandItem())){
            event.setCanceled(true);event.setSwingHand(false);
        }
    }
    private static boolean held;
    /** Left click held with the toe in hand sprints; press and release go to the server, which owns the rest. */
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        boolean down=mc.player!=null&&mc.screen==null&&mc.getConnection()!=null&&mc.player.isAlive()&&!mc.player.isSpectator()
                &&ToeItem.is(mc.player.getMainHandItem())&&mc.options.keyAttack.isDown();
        if(down==held)return;held=down;
        if(mc.getConnection()!=null)dev.gungang.acoustics.server.GungangNetwork.CHANNEL.sendToServer(
                new dev.gungang.acoustics.server.GungangNetwork.Action("toeSprint",down?"down":"up",""));
    }
    private ToeInput(){}
}
