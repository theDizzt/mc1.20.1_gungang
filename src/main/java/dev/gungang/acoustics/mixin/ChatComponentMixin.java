package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.ChatLayout;
import dev.gungang.acoustics.client.ShooterHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;guiHeight()I"))
    private int gungang$height(GuiGraphics graphics) { return graphics.guiHeight() - ChatLayout.lift(); }

    @ModifyVariable(method = "screenToChatY", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double gungang$mouseY(double y) { return y + ChatLayout.lift(); }

    @ModifyVariable(method = "handleChatQueueClicked", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double gungang$queueY(double y) { return y + ChatLayout.lift(); }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void gungang$background(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        if (!ShooterHud.visible(Minecraft.getInstance()) || (color & 0xFFFFFF) != 0)
            graphics.fill(left, top, right, bottom, color);
    }

    @Inject(method = "getHeight()I", at = @At("RETURN"), cancellable = true)
    private void gungang$limitHeight(CallbackInfoReturnable<Integer> callback) {
        if (ShooterHud.visible(Minecraft.getInstance()))
            callback.setReturnValue(Math.min(callback.getReturnValue(), ChatLayout.availableHeight()));
    }
}
