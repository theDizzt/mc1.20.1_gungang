package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.TitleMenuStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractButton.class, ImageButton.class})
public abstract class TitleMenuButtonMixin extends AbstractWidget {
    protected TitleMenuButtonMixin(int x, int y, int width, int height, Component label) {
        super(x, y, width, height, label);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void gungang$menuButton(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo callback) {
        if (!TitleMenuStyle.applies(this)) return;
        callback.cancel();
        // Very low text alpha is treated as opaque by Font; skip those fade-in frames.
        if (alpha < 0.05F) return;
        int color = TitleMenuStyle.button(graphics, this, alpha);
        if ((Object) this instanceof ImageButton) {
            TitleMenuStyle.icon(graphics, this, color);
        } else {
            renderScrollingString(graphics, Minecraft.getInstance().font, 7, color);
        }
    }
}
