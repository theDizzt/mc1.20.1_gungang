package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
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
    private static final int WIDTH = 140; // 68px portrait gap + two slots (34 + 4 + 34).
    private static final int HEIGHT = 64;
    private static final int HEALTH_WIDTH = Math.round(72 * 3.2f);
    private static final int HEALTH_HEIGHT = Math.round(14 * 1.6f);

    public static boolean visible(Minecraft mc) {
        return mc.player != null && mc.level != null
                && !mc.options.hideGui && HudVisibility.enabled() && AcousticConfig.SHOOTER_HUD.get();
    }

    @SubscribeEvent
    public static void hideVanilla(RenderGuiOverlayEvent.Pre event) {
        if (!visible(Minecraft.getInstance())) return;
        if (event.getOverlay().id().equals(VanillaGuiOverlay.POTION_ICONS.id())
                || event.getOverlay().id().equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                || event.getOverlay().id().equals(VanillaGuiOverlay.AIR_LEVEL.id())) event.setCanceled(true);
        if (Minecraft.getInstance().player.isSpectator()) return;
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())) {
            event.setCanceled(true);
        }
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) return;
        var player = mc.player;
        // Two framebuffer pixels per HUD unit, independent of the Minecraft GUI scale.
        float scale = (float) (2.0 / mc.getWindow().getGuiScale());
        screenWidth = mc.getWindow().getWidth() / 2;
        screenHeight = mc.getWindow().getHeight() / 2;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        int x = Math.min(AcousticConfig.HUD_OFFSET_X.get(), Math.max(0, screenWidth - WIDTH));
        int y = Math.max(0, screenHeight - HEIGHT - AcousticConfig.HUD_OFFSET_Y.get());
        gui.setupOverlayRenderState(true, false);
        int team = TeamHud.teamColor(player);
        portrait(graphics, x, y + 2, 62, team);
        PlayerFaceRenderer.draw(graphics, player.getSkinTextureLocation(), x + 3, y + 5, 56,
                player.isModelPartShown(PlayerModelPart.HAT), false);
        RespawnPortrait.render(graphics, player.getUUID(), x + 3, y + 5, 56);
        int detailX = x + 68;
        ProgressionHud.draw(graphics,detailX,y+7,WIDTH-68);
        HudText.draw(graphics, player.getName().getString(), detailX, y + 19, WIDTH - 68, 0xFFFFFFFF);
        // Slot 1 is always inventory index 0, independent of the player's selected slot.
        slot(graphics, player.getInventory().getItem(0), detailX, y + 32, player.getInventory().selected == 0, team);
        slot(graphics, player.getOffhandItem(), detailX + 38, y + 32, false, team);
        UtilityHud.placements(graphics,x+WIDTH+8,y+35);

        float health = player.getHealth(), maximum = player.getMaxHealth();
        float fraction = HudHealth.fraction(health, maximum);
        int color = fraction <= 0.25f ? 0xFFFF9292 : fraction <= 0.5f ? 0xFFFFDC98 : 0xFFF5F5F0;
        String value = HudHealth.label(health, maximum,player.getAbsorptionAmount());
        String mode = mc.gameMode != null && mc.gameMode.getPlayerMode() != net.minecraft.world.level.GameType.SURVIVAL
                ? net.minecraft.network.chat.Component.translatable("gameMode." + mc.gameMode.getPlayerMode().getName()).getString() : "";
        int sideSpace = mode.isEmpty() ? 8 : Math.round(mc.font.width(mode) * 1.25f) + 12;
        int barWidth = Math.min(HEALTH_WIDTH, Math.max(4, screenWidth - sideSpace * 2));
        int barX = (screenWidth - barWidth) / 2;
        int barY = Math.max(0, screenHeight - HEALTH_HEIGHT - AcousticConfig.HUD_OFFSET_Y.get());
        // On narrow windows keep the centered bar clear of the portrait and item slots.
        if (barX < x + WIDTH + 8 + UtilityHud.placementWidth() && barX + barWidth > x)
            barY = Math.max(0, y - HEALTH_HEIGHT - 8);
        CompassHud.render(graphics, barX + barWidth / 2, barY, barWidth, partialTick);
        // Above the compass heading (or right above the bar when the compass is off).
        int captionY = AcousticConfig.COMPASS.get() ? barY - 44 : barY - 14;
        // Any mode: name of the player whose camera is being watched; the disguise caption stacks above it.
        var watched = PropHuntClient.watched();
        var info = watched == null || watched.equals(player.getUUID()) || mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(watched);
        if (info != null) {
            graphics.drawCenteredString(mc.font, "관전 중: " + info.getProfile().getName(), barX + barWidth / 2, captionY, 0xFFFFFFFF);
            captionY -= 11;
        }
        String disguise = PropHuntClient.caption();
        if (disguise != null) {
            int captionColor = disguise.equals("술래") ? PropHuntHud.SEEKER : PropHuntHud.HIDER;
            graphics.drawCenteredString(mc.font, disguise, barX + barWidth / 2, captionY, captionColor);
        }
        UtilityHud.gauges(graphics,barX+barWidth/2,barY,barWidth/2,HEALTH_HEIGHT/2,partialTick);
        card(graphics, barX, barY, barWidth, HEALTH_HEIGHT, BAR_EDGE, SURFACE, 0);
        var layers = HudHealthBars.layers(player, partialTick);
        float displayed = layers.absorptionEnd();
        float filled = (barWidth - 2) * displayed;
        HudHealthBars.render(graphics, barX + 1, barY + 1, barWidth - 2, HEALTH_HEIGHT - 2, layers, color);
        HudHealthBars.stripes(graphics, barX + 1, barY + 1, barWidth - 2, HEALTH_HEIGHT - 2, displayed, (player.tickCount + partialTick) / 20.0);
        finish(graphics, barX + 1, barY + 1, barWidth - 2, HEALTH_HEIGHT - 2, displayed);
        int airPercent = Math.round(100f * Math.max(0, Math.min(player.getMaxAirSupply(), player.getAirSupply()))
                / Math.max(1, player.getMaxAirSupply()));
        String airLabel = airPercent + "%";
        int airSpace = player.isInWater()
                ? Math.min(Math.max(0, barWidth - 6), mc.font.width("100%") + 4) : 0;
        int healthTextWidth = Math.max(0, barWidth - 6 - airSpace);
        HudText.health(graphics, value, barX + 3, barY + 1, healthTextWidth, HEALTH_HEIGHT - 2, 0xFFFFFFFF);
        if (filled > 0) {
            graphics.enableScissor(Math.round((barX + 1) * scale), Math.round((barY + 1) * scale),
                    Math.round((barX + 1 + filled) * scale), Math.round((barY + HEALTH_HEIGHT - 1) * scale));
            HudText.health(graphics, value, barX + 3, barY + 1, healthTextWidth, HEALTH_HEIGHT - 2, 0xFF111820);
            graphics.disableScissor();
        }
        if (airSpace > 4) {
            float airScale = Math.min(1f, (airSpace - 4f) / Math.max(1, mc.font.width(airLabel)));
            graphics.pose().pushPose();
            graphics.pose().translate(barX + barWidth - 4, barY + (HEALTH_HEIGHT - 8 * airScale) / 2f, 0);
            graphics.pose().scale(airScale, airScale, 1);
            int left = -mc.font.width(airLabel);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) graphics.drawString(mc.font, airLabel, left + dx, dy, 0xFF000000, false);
            graphics.drawString(mc.font, airLabel, left, 0, 0xFF55AAFF, false);
            graphics.pose().popPose();
        }
        int effectX = barX + barWidth - 15 - airSpace;
        var effects = player.getActiveEffects().stream().filter(e -> e.showIcon())
                .sorted(java.util.Comparator.comparingInt(e -> net.minecraft.world.effect.MobEffect.getId(e.getEffect()))).toList();
        int numberSpace = Math.round(mc.font.width(value) * 1.8f) + 10;
        for (var effect : effects) {
            if (effectX < barX + numberSpace) break;
            graphics.blit(effectX, barY + (HEALTH_HEIGHT - 12) / 2, 0, 12, 12,
                    mc.getMobEffectTextures().get(effect.getEffect()));
            effectX -= 14;
        }
        if (player.getAbsorptionAmount() > 0) {
            HudText.draw(graphics, "+" + HudHealth.number(player.getAbsorptionAmount()), x + 3, y + 50, 56, 0xFFFFD36B);
        }
        if (!mode.isEmpty()) {
            int modeX = barX + barWidth + 6;
            int space = screenWidth - modeX - 4;
            // Fit the complete mode label on smaller windows instead of truncating it away.
            float textScale = Math.min(1.25f, Math.max(0.5f, (float) space / Math.max(1, mc.font.width(mode))));
            graphics.pose().pushPose();
            graphics.pose().translate(modeX, barY + (HEALTH_HEIGHT - 9 * textScale) / 2f, 0);
            graphics.pose().scale(textScale, textScale, 1);
            graphics.drawString(mc.font, mode, 0, 0, 0xFFFFFFFF, false);
            graphics.pose().popPose();
        }
        TeamHud.render(graphics, x, y, screenWidth, screenHeight, partialTick);
        graphics.flush();
        graphics.pose().popPose();
    }

    private static void slot(GuiGraphics graphics, ItemStack stack, int x, int y, boolean selected, int team) {
        Minecraft mc = Minecraft.getInstance();
        if (selected) {
            card(graphics, x, y, 34, 32, team | 0xFF000000, SURFACE, team | 0xFF000000);
            graphics.fillGradient(x + 1, y + 2, x + 33, y + 29, team & 0xFFFFFF, (team & 0xFFFFFF) | 0x40000000);
        } else card(graphics, x, y, 34, 32, EDGE, SURFACE, 0);
        if (!stack.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 5, y + 4, 0);
            graphics.pose().scale(1.5f, 1.5f, 1);
            graphics.renderItem(stack, 0, 0);
            graphics.renderItemDecorations(mc.font, stack, 0, 0);
            graphics.pose().popPose();
            long cooldown=dev.gungang.acoustics.server.SecondaryRuntime.remaining(mc.player,stack);
            if(cooldown>0) {
                graphics.flush();
                graphics.pose().pushPose();
                // Item models and decorations write depth up to z=200; keep both overlay layers above them.
                graphics.pose().translate(0,0,300);
                int total=Math.max(1,stack.getTag().getInt("GunGangCooldownTicks"));
                graphics.fill(x+1,y+31-Math.round(30*Math.min(1,cooldown/(float)total)),x+33,y+31,0xAA101820);
                String seconds=String.format(java.util.Locale.ROOT,"%.1f",cooldown/20.0);
                float textScale=Math.min(1f,30f/Math.max(1,mc.font.width(seconds)));
                graphics.pose().translate(x+17,y+16-4*textScale,1);
                graphics.pose().scale(textScale,textScale,1);
                graphics.drawString(mc.font,seconds,-mc.font.width(seconds)/2,0,0xFFFFFFFF,true);
                graphics.flush();
                graphics.pose().popPose();
            }
        }
    }
    static final int SURFACE = 0xFF0B121C, EDGE = 0xFF2A3A50, BAR_EDGE = 0xFF465264;
    /** Opaque card: soft edge, faint top highlight and an optional accent underline inside the border. */
    static void card(GuiGraphics g, int x, int y, int width, int height, int edge, int background, int accent) {
        panel(g, x, y, width, height, edge, background);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, 0x14FFFFFF);
        if (accent != 0) g.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, accent);
    }
    /** Portrait frame: dark tile with a team tint fading in toward the bottom and a solid team underline below the face inset. */
    static void portrait(GuiGraphics g, int x, int y, int size, int team) {
        int rgb = team & 0xFFFFFF, line = size >= 40 ? 2 : 1;
        panel(g, x, y, size, size, team | 0xFF000000, SURFACE);
        g.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1, rgb | 0x10000000, rgb | 0x58000000);
        g.fill(x + 1, y + size - 1 - line, x + size - 1, y + size - 1, team | 0xFF000000);
    }
    /** Gloss line on the filled part, drawn over a bar fill. */
    static void finish(GuiGraphics g, int x, int y, int width, int height, float fraction) {
        HudHealthBars.fill(g, x, y, width, 1, Math.max(0, Math.min(1, fraction)), 0x40FFFFFF);
    }
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height,
                              int border, int background) {
        // Keep the opaque border out of the translucent interior.
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y + 1, x + 1, y + height - 1, border);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, background);
    }

    private ShooterHud() { }
}
