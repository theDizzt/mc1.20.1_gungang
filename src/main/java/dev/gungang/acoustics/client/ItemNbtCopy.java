package dev.gungang.acoustics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class ItemNbtCopy {
    public static final KeyMapping COPY = new KeyMapping("key.gungang.copy_item_nbt", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.gungang");
    private static boolean wasDown;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        boolean clicked=false;
        while(COPY.consumeClick())clicked=true;
        boolean down=COPY.isDown(),pressed=clicked&&!wasDown;
        wasDown=down;
        var mc=Minecraft.getInstance();
        if(!pressed||mc.player==null||mc.level==null||mc.screen!=null||!mc.isWindowActive())return;
        var stack=mc.player.getMainHandItem();
        if(stack.isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("gungang.nbt.empty"),true);
            return;
        }
        // Serialize the whole stack, including arbitrary nested tags and Forge capabilities.
        // Never select only known TaCZ keys or edit the held item.
        String snbt=stack.save(new CompoundTag()).toString();
        mc.keyboardHandler.setClipboard(snbt);
        mc.player.displayClientMessage(Component.translatable("gungang.nbt.copied",stack.getHoverName()),true);
    }
    private ItemNbtCopy() {}
}
