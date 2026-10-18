package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Q never drops an item in survival or adventure, from the hotbar or an inventory screen; the server refuses any other toss too. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class DropLock {
    private static boolean locked(){var mc=Minecraft.getInstance();return mc.gameMode!=null&&mc.gameMode.getPlayerMode().isSurvival();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        // START runs before vanilla handles key binds this tick, so the queued press is gone before it can drop.
        if(e.phase!=TickEvent.Phase.START||!locked())return;
        while(Minecraft.getInstance().options.keyDrop.consumeClick());
    }
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre e){
        if(e.getScreen() instanceof AbstractContainerScreen<?>&&locked()&&Minecraft.getInstance().options.keyDrop.matches(e.getKeyCode(),e.getScanCode()))e.setCanceled(true);
    }
    private DropLock(){}
}
