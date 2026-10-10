package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.ChatLayout;
import dev.gungang.acoustics.client.ShooterHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"))
    private void gungang$inputPosition(CallbackInfo callback) {
        input.setY(input.getY() - ChatLayout.lift());
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void gungang$inputBackground(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        if (!ShooterHud.visible(Minecraft.getInstance())) graphics.fill(left, top, right, bottom, color);
    }
}
