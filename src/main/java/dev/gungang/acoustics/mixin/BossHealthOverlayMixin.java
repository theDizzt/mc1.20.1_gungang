package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.ShooterHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void gungang$begin(GuiGraphics graphics, CallbackInfo ci) {
        dev.gungang.acoustics.client.BossBarHud.beginFrame();
    }
    // Vanilla retains title, position, spacing and Forge boss events.
    @Inject(method = "drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private void gungang$bar(GuiGraphics graphics, int x, int y, BossEvent boss, CallbackInfo callback) {
        dev.gungang.acoustics.client.BossBarHud.bottom = Math.max(dev.gungang.acoustics.client.BossBarHud.bottom, y + 9);
        if (!ShooterHud.visible(Minecraft.getInstance())) return;
        callback.cancel();
        int color = switch (boss.getColor()) {
            case PINK -> 0xFFEC00B8;
            case BLUE -> 0xFF008BFF;
            case RED -> 0xFFFF3030;
            case GREEN -> 0xFF00CC30;
            case YELLOW -> 0xFFFFFF00;
            case PURPLE -> 0xFF9900FF;
            case WHITE -> 0xFFFFFFFF;
        };
        dev.gungang.acoustics.client.BossBarHud.draw(graphics, x, y, boss, color);
    }
}
