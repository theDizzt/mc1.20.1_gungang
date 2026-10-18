package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pins the selected hotbar slot to 1 in survival and adventure. Creative and spectator stay free.
 * Offhand swap (F) and the offhand macros keep
 * the selected slot unchanged, so they still work; only slot changes are blocked.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class HotbarLock {
    static boolean locked() {
        var mc=Minecraft.getInstance();
        return mc.player!=null&&!mc.player.isSpectator()&&!mc.player.isCreative();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!locked())return;
        var mc=Minecraft.getInstance();
        if(event.phase==TickEvent.Phase.START) {
            // Vanilla reads hotbar keys later in this tick; drop the queued 2-9 presses first.
            for(int i=1;i<mc.options.keyHotbarSlots.length;i++)while(mc.options.keyHotbarSlots[i].consumeClick());
            return;
        }
        // Pick-block, server slot changes or anything else that moved the selection.
        if(mc.player.getInventory().selected!=0&&mc.getConnection()!=null) {
            mc.player.getInventory().selected=0;
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(0));
        }
    }
    /** Lowest priority so TAB paging, spectator cycling and other mods see the wheel first. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        var mc=Minecraft.getInstance();
        if(!event.isCanceled()&&mc.screen==null&&locked())event.setCanceled(true);
    }
    private HotbarLock() { }
}
