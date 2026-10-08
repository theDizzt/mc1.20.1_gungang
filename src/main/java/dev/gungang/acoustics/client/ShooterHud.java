package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class ShooterHud {
    private static final int WIDTH = 180;
    private static final int HEIGHT = 88;

    private static boolean visible(Minecraft mc) {
        return mc.player != null && mc.level != null && !mc.player.isSpectator()
                && !mc.options.hideGui && AcousticConfig.SHOOTER_HUD.get();
    }

    @SubscribeEvent
    public static void hideVanilla(RenderGuiOverlayEvent.Pre event) {
        if (!visible(Minecraft.getInstance())) return;
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) {
            event.setCanceled(true);
        }
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) return;
        var player = mc.player;
        // On narrow GUI scales, leave the central food/armor/XP area unobstructed.
        int bottomPadding = screenWidth < 560 ? 64 : 8;
        int x = 8, y = screenHeight - HEIGHT - bottomPadding;
        gui.setupOverlayRenderState(true, false);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xD9101720);
        graphics.fill(x, y, x + 2, y + HEIGHT, 0xFF62D9C6);
        graphics.fill(x + 8, y + 12, x + 48, y + 52, 0xFF36434F);
        PlayerFaceRenderer.draw(graphics, player.getSkinTextureLocation(), x + 10, y + 14, 36,
                player.isModelPartShown(PlayerModelPart.HAT), false);

        // Slot 1 is always inventory index 0, independent of the player's selected slot.
        slot(graphics, player.getInventory().getItem(0), x + 64, y + 20,
                Component.translatable("gungang.hud.slotOne"), player.getInventory().selected == 0);
        slot(graphics, player.getOffhandItem(), x + 108, y + 20,
                Component.translatable("gungang.hud.offhand"), false);

        float health = player.getHealth(), maximum = player.getMaxHealth();
        float fraction = HudHealth.fraction(health, maximum);
        int color = fraction <= 0.25f ? 0xFFFF6262 : fraction <= 0.5f ? 0xFFFFC45E : 0xFF62D9C6;
        String value = HudHealth.label(health, maximum);
        graphics.drawString(mc.font, Component.translatable("gungang.hud.health"), x + 10, y + 59, 0xFFCED8DF);
        if (player.getAbsorptionAmount() > 0) {
            graphics.drawString(mc.font, "+" + HudHealth.number(player.getAbsorptionAmount()), x + 32, y + 59, 0xFFFFD36B);
        }
        graphics.drawString(mc.font, value, x + WIDTH - 10 - mc.font.width(value), y + 59, 0xFFFFFFFF);
        graphics.fill(x + 10, y + 74, x + WIDTH - 10, y + 80, 0xFF35424E);
        int filled = Math.round((WIDTH - 20) * fraction);
        if (filled > 0) graphics.fill(x + 10, y + 74, x + 10 + filled, y + 80, color);
    }

    private static void slot(GuiGraphics graphics, ItemStack stack, int x, int y, Component label, boolean selected) {
        Minecraft mc = Minecraft.getInstance();
        graphics.drawCenteredString(mc.font, label, x + 14, y - 12, 0xFFCCD7E0);
        graphics.fill(x, y, x + 28, y + 28, selected ? 0xFF62D9C6 : 0xFF465360);
        graphics.fill(x + 1, y + 1, x + 27, y + 27, 0xFF1C2833);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 6, y + 6);
            graphics.renderItemDecorations(mc.font, stack, x + 6, y + 6);
        }
    }
    private ShooterHud() { }
}
