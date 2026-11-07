package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.client.TitleMenuStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Main-menu branding only; other screens retain their own logo renderers. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique
    private static final ResourceLocation GUNGANG_LOGO = new ResourceLocation(
            GungangAcoustics.ID, "textures/gui/title/gungang.png");

    @Shadow private SplashRenderer splash;
    @Unique
    private Component gungang$versionLabel = Component.empty();
    @Unique
    private static final ResourceLocation GUNGANG_VERSION_FONT = new ResourceLocation(
            GungangAcoustics.ID, "hud_number");

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gungang$versionSplash(CallbackInfo callback) {
        // Release versions are <update>.<date>, e.g. 255.261106.
        ModList.get().getModContainerById(GungangAcoustics.ID).ifPresent(mod -> {
            String label = "UPDATE " + mod.getModInfo().getVersion().getMajorVersion();
            splash = new SplashRenderer(label);
            gungang$versionLabel = Component.literal(label).withStyle(style -> style.withFont(GUNGANG_VERSION_FONT));
        });
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"))
    private void gungang$renderVersion(SplashRenderer original, GuiGraphics graphics, int screenWidth, Font font, int alpha) {
        // A horizontal, enlarged label in a dedicated 32px gap above the menu frame.
        int textWidth = font.width(gungang$versionLabel);
        float textHeight = font.lineHeight + 4.0F; // Include TTF baseline shift and shadow.
        float scale = Math.min(1.5F, Math.min((float)Math.max(1, screenWidth - 40) / Math.max(1, textWidth),
                20.0F / textHeight));
        graphics.pose().pushPose();
        graphics.pose().translate(screenWidth / 2.0F, height / 4 + 24.0F, 0);
        graphics.pose().scale(scale, scale, scale);
        graphics.drawString(font, gungang$versionLabel, -textWidth / 2, -(font.lineHeight + 2) / 2,
                0xFFFF55 | alpha, true);
        graphics.pose().popPose();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(FF)V"))
    private void gungang$skipPanorama(PanoramaRenderer original, float partial, float alpha) {
        // The opaque holographic backdrop is drawn with the logo below.
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void gungang$renderLogo(LogoRenderer original, GuiGraphics graphics, int screenWidth, float alpha) {
        TitleMenuStyle.backdrop(graphics, screenWidth, height, alpha);
        // Reserve a separate version row above the menu; preserve the artwork's 8:3 aspect ratio.
        int logoWidth = Math.max(8, Math.min(320, Math.min(screenWidth - 24, (height / 4) * 8 / 3)));
        int logoHeight = logoWidth * 3 / 8;
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(GUNGANG_LOGO, (screenWidth - logoWidth) / 2, 8,
                logoWidth, logoHeight, 0.0F, 0.0F, 1600, 600, 1600, 600);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
