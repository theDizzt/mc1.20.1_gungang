package dev.gungang.acoustics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.ArrayDeque;
import java.util.Comparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class TeamHud {
    /** A kill log row; y glides toward its slot so rows reflow smoothly when one leaves. */
    private static final class Kill {
        final DeathNotice notice; final long started; long expires; float y = Float.NaN;
        Kill(DeathNotice notice, long started, long expires) { this.notice = notice; this.started = started; this.expires = expires; }
    }
    private static final long ENTER_NS = 280_000_000L, EXIT_NS = 400_000_000L, LIFE_NS = 8_000_000_000L;
    private static long lastFrame;
    private static final ArrayDeque<Kill> KILLS = new ArrayDeque<>();
    public static int rosterBottom = 8;

    public static boolean teammate(Player other) {
        var self = Minecraft.getInstance().player;
        return self != null && other != self && self.getTeam() != null
                && self.getTeam().isAlliedTo(other.getTeam()) && !other.isSpectator();
    }

    public static int teamColor(Player player) {
        var ffa=FreeForAllHud.row(player.getUUID());if(ffa!=null)return 0xFF000000|ffa.color();
        if(player.getTeam()!=null) {
            if(player.getTeam().getName().equals("Red")) return LobbyClient.options.color("ALPHA");
            if(player.getTeam().getName().equals("Blue")) return LobbyClient.options.color("BRAVO");
        }
        Integer color = player.getTeam() == null ? null : player.getTeam().getColor().getColor();
        return 0xFF000000 | (color == null ? 0xFFFFFF : color);
    }

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent.System event) {
        DeathNotice notice = DeathNotice.parse(event.getMessage());
        if (event.isOverlay() || notice == null) return;
        RespawnPortrait.death(notice.victim());
        if (ShooterHud.visible(Minecraft.getInstance()) && AcousticConfig.KILL_LOG.get()) event.setCanceled(true);
        long now = System.nanoTime();
        KILLS.addLast(new Kill(notice, now, now + LIFE_NS));
        // Rows pushed past the newest five play their exit instead of vanishing.
        int extra = KILLS.size() - 5;
        for (Kill kill : KILLS) { if (extra-- <= 0) break; kill.expires = Math.min(kill.expires, now + EXIT_NS); }
        while (KILLS.size() > 8) KILLS.removeFirst();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { KILLS.clear(); rosterBottom = 8; lastFrame = 0; }

    public static void render(GuiGraphics g, int x, int mainY, int width, int height, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        rosterBottom = 8;
        if (AcousticConfig.TEAM_HUD.get()) {
            var members = mc.getConnection().getOnlinePlayers().stream()
                    .filter(info -> !info.getProfile().getId().equals(mc.player.getUUID())
                            && mc.player.getTeam() != null
                            && mc.player.getTeam().isAlliedTo(info.getTeam())
                            && (info.getGameMode() != net.minecraft.world.level.GameType.SPECTATOR || RespawnPortrait.waiting(info.getProfile().getId())))
                    .sorted(Comparator.comparing(info -> info.getProfile().getName())).toList();
            int capacity = Math.max(0, Math.min(height / 3, mainY - 20) / 34);
            int count = Math.min(capacity, members.size());
            for (int i = 0; i < count; i++) {
                var info = members.get(i);
                Player p = mc.level.getPlayerByUUID(info.getProfile().getId());
                int y = 8 + i * 34;
                ShooterHud.portrait(g, x, y, 30, teamColor(mc.player));
                PlayerFaceRenderer.draw(g, info.getSkinLocation(), x + 2, y + 2, 26);
                RespawnPortrait.render(g, info.getProfile().getId(), x + 2, y + 2, 26);
                HudText.draw(g, info.getProfile().getName(), x + 34, y + 1, 80, 0xFFFFFFFF);
                ShooterHud.card(g, x + 34, y + 16, 80, 14, ShooterHud.BAR_EDGE, ShooterHud.SURFACE, 0);
                ShooterHud.card(g, x + 118, y + 8, 22, 22, ShooterHud.EDGE, ShooterHud.SURFACE, 0);
                if (p != null) {
                    var layers = HudHealthBars.layers(p, partialTick);
                    float fraction = layers.absorptionEnd();
                    HudHealthBars.render(g, x + 35, y + 17, 78, 12, layers, 0xFFF5F5F0);
                    ShooterHud.finish(g, x + 35, y + 17, 78, 12, fraction);
                    HudText.health(g, HudHealth.label(p.getHealth(), p.getMaxHealth(),p.getAbsorptionAmount()), x + 36, y + 16, 76, 14, 0xFFFFFFFF, 0.78f);
                    float scale = (float) (2.0 / mc.getWindow().getGuiScale());
                    g.enableScissor(Math.round((x + 35) * scale), Math.round((y + 17) * scale),
                            Math.round((x + 35 + 78 * fraction) * scale), Math.round((y + 29) * scale));
                    HudText.health(g, HudHealth.label(p.getHealth(), p.getMaxHealth(),p.getAbsorptionAmount()), x + 36, y + 16, 76, 14, 0xFF111820, 0.78f);
                    g.disableScissor();
                    g.renderItem(p.getMainHandItem(), x + 121, y + 11);
                } else {
                    HudText.draw(g, "--", x + 36, y + 18, 76, 0xFFAAAAAA);
                }
            }
            rosterBottom = 8 + count * 34;
            if (members.size() > count) {
                g.drawString(mc.font, "+" + (members.size() - count), x + 120, rosterBottom, 0xFFFFFFFF);
                rosterBottom += 12;
            }
        }
        long now = System.nanoTime();
        KILLS.removeIf(k -> k.expires < now);
        float dt = lastFrame == 0 ? 0 : Math.min(.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (AcousticConfig.KILL_LOG.get()) {
            int slot = 8;
            for (Kill kill : KILLS) {
                if (slot + 16 > height / 2) break;
                if (Float.isNaN(kill.y)) kill.y = slot;
                kill.y += (slot - kill.y) * Math.min(1, dt * 14);
                // Enter: slide in from the right with a slight overshoot and fade in. Exit: fade and drift right.
                float enter = Math.min(1, (now - kill.started) / (float) ENTER_NS);
                float exit = Math.max(0, 1 - (kill.expires - now) / (float) EXIT_NS);
                float c = enter - 1, eased = 1 + 2.2f * c * c * c + 1.2f * c * c;
                float offset = (1 - eased) * 48 + exit * exit * 32;
                float alpha = Math.min(1, enter * 1.6f) * (1 - exit);
                if (alpha <= .02f) { slot += 22; continue; }
                g.flush();
                RenderSystem.enableBlend();
                g.setColor(1, 1, 1, alpha);
                g.pose().pushPose();
                g.pose().translate(offset, kill.y - Math.round(kill.y), 0);
                int y = Math.round(kill.y), right = width - 8;
                skull(g, right - 10, y + 3);
                right = actor(g, kill.notice.victim(), right - 16, y);
                String verb = kill.notice.killer() == null ? "DIED" : "KILLED";
                int verbWidth = mc.font.width(verb);
                g.drawString(mc.font, verb, right - verbWidth - 5, y + 4, 0xFFE2E6E8, false);
                if (kill.notice.killer() != null) actor(g, kill.notice.killer(), right - verbWidth - 10, y);
                g.pose().popPose();
                // Text is batched; flush while the fade colour is still bound.
                g.flush();
                g.setColor(1, 1, 1, 1);
                slot += 22;
            }
        }
    }

    private static int actor(GuiGraphics g, Component name, int right, int y) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = null;
        HoverEvent hover = name.getStyle().getHoverEvent();
        if (hover != null) {
            var entity = hover.getValue(HoverEvent.Action.SHOW_ENTITY);
            if (entity != null) info = mc.getConnection().getPlayerInfo(entity.id);
        }
        if (info == null) info = mc.getConnection().getPlayerInfo(name.getString());
        String label = mc.font.plainSubstrByWidth(name.getString(), 62);
        int left = right - mc.font.width(label) - 20;
        var skin = info == null ? DefaultPlayerSkin.getDefaultSkin() : info.getSkinLocation();
        var team = info == null ? mc.level.getScoreboard().getPlayersTeam(name.getString()) : info.getTeam();
        Integer teamRgb = team == null ? null : team.getColor().getColor();
        int color = 0xFF000000 | (teamRgb == null ? 0xFFFFFF : teamRgb);
        if(team!=null&&team.getName().equals("Red"))color=LobbyClient.options.color("ALPHA");
        if(team!=null&&team.getName().equals("Blue"))color=LobbyClient.options.color("BRAVO");
        g.fill(left, y, left + 16, y + 16, color);
        PlayerFaceRenderer.draw(g, skin, left + 1, y + 1, 14);
        g.drawString(mc.font, label, left + 20, y + 4, color, false);
        return left;
    }

    private static void skull(GuiGraphics g, int x, int y) {
        // Pixel skull/crossbones stays available even when a TTF lacks the emoji glyph.
        g.fill(x + 1, y, x + 9, y + 6, 0xFFF3F5F6);
        g.fill(x + 3, y + 6, x + 7, y + 8, 0xFFF3F5F6);
        g.fill(x + 2, y + 2, x + 4, y + 4, 0xFF202830);
        g.fill(x + 6, y + 2, x + 8, y + 4, 0xFF202830);
        g.fill(x, y + 8, x + 3, y + 10, 0xFFF3F5F6);
        g.fill(x + 7, y + 8, x + 10, y + 10, 0xFFF3F5F6);
    }
    private TeamHud() { }
}
