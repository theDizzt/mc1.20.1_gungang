package dev.gungang.acoustics.client;

import dev.gungang.acoustics.mixin.EffectColorAccessor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

final class PortraitEffects {
    static void tint(GuiGraphics graphics, Player player, int x, int y, int size) {
        if (player == null) return;
        int red = 0, green = 0, blue = 0, weight = 0;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            int color = effect.getEffect().getColor();
            int amount = effect.getAmplifier() + 1;
            red += (color >> 16 & 255) * amount;
            green += (color >> 8 & 255) * amount;
            blue += (color & 255) * amount;
            weight += amount;
        }
        int color;
        if (weight > 0) {
            color = (red / weight << 16) | (green / weight << 8) | blue / weight;
        } else {
            // Other players' effect lists may be unavailable, but the aggregate color is synced.
            color = player.getEntityData().get(EffectColorAccessor.colorKey());
            if (color == 0) return;
        }
        graphics.fill(x, y, x + size, y + size, 0x55000000 | color);
    }

    private PortraitEffects() { }
}
