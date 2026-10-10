package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import net.minecraft.client.Minecraft;

public final class ChatLayout {
    public static int lift() {
        Minecraft mc = Minecraft.getInstance();
        if (!ShooterHud.visible(mc)) return 0;
        return Math.min(Math.max(0, mc.getWindow().getGuiScaledHeight() - 80),
                (int) Math.ceil((64 + AcousticConfig.HUD_OFFSET_Y.get() + 8) * 2.0 / mc.getWindow().getGuiScale()));
    }

    public static int availableHeight() {
        Minecraft mc = Minecraft.getInstance();
        int top = (int) Math.ceil((TeamHud.rosterBottom + 8) * 2.0 / mc.getWindow().getGuiScale());
        return Math.max(9, mc.getWindow().getGuiScaledHeight() - lift() - 40 - top);
    }

    private ChatLayout() { }
}
