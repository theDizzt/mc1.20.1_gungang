package dev.gungang.acoustics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import dev.gungang.acoustics.server.BotDatabase;
import dev.gungang.acoustics.server.BotEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
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
    /** Softer charcoal than the card surface behind a teammate's missing health. */
    private static final int BAR_TRACK = 0x99292B24;
    /** Teammate item slot: the card surface, see-through like the bar track. */
    private static final int SLOT_SURFACE = 0x99000000 | (ShooterHud.SURFACE & 0xFFFFFF);
    private static final long ENTER_NS = 280_000_000L, EXIT_NS = 400_000_000L, LIFE_NS = 8_000_000_000L;
    private static long lastFrame;
    private static final ArrayDeque<Kill> KILLS = new ArrayDeque<>();
    public static int rosterBottom = 8;

    public static boolean teammate(LivingEntity other) {
        var self = Minecraft.getInstance().player;
        return self != null && other != self && self.getTeam() != null
                && self.getTeam().isAlliedTo(other.getTeam()) && !other.isSpectator();
    }

    public static int teamColor(net.minecraft.world.entity.LivingEntity player) {
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
            var members = members(mc);
            // Fill the left column down to just above the player's own card; when some still don't fit,
            // give up a row if needed so the "+N" overflow line has room too.
            int space = Math.max(0, mainY - 8 - 8);
            int capacity = space / 34;
            if (members.size() > capacity) capacity = Math.max(0, (space - 12) / 34);
            int count = Math.min(capacity, members.size());
            for (int i = 0; i < count; i++) {
                var member = members.get(i);
                int y = 8 + i * 34;
                // A dead teammate's row is drawn offscreen and composited see-through; the respawn countdown on
                // the portrait stays fully opaque on top.
                boolean dead = dead(member);
                if (dead) FadeLayer.begin(g);
                if (AcousticConfig.LEGACY_TEAM_HUD.get()) legacy(g, mc, member, x, y, partialTick);
                else compact(g, mc, member, x, y, partialTick);
                if (dead) FadeLayer.end(g, DEAD_OPACITY);
                if (RespawnPortrait.render(g, member.id(), x + 2, y + 2, 26) && AcousticConfig.LEGACY_TEAM_HUD.get())
                    TeamPointHud.badge(g, member.id(), x + 2, y + 2, 26);
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
        // The mission card sits at the top right; the kill log lines up below it.
        int killTop = MissionHud.render(g, width);
        if (AcousticConfig.KILL_LOG.get()) {
            int slot = killTop;
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
                String verb = kill.notice.killer() == null ? "DIED" : kill.notice.found() ? "FOUND" : "KILLED";
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

    private record Member(UUID id, String name, ResourceLocation skin, LivingEntity entity) { }

    /** Allied players plus allied bots (from the match rows, as bots have no PlayerInfo), sorted by name. */
    private static List<Member> members(Minecraft mc) {
        var team = mc.player.getTeam();
        List<Member> members = new ArrayList<>();
        if (team == null) return members;
        for (var info : mc.getConnection().getOnlinePlayers()) {
            UUID id = info.getProfile().getId();
            if (id.equals(mc.player.getUUID()) || !team.isAlliedTo(info.getTeam())
                    || (info.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR && !RespawnPortrait.waiting(id))) continue;
            members.add(new Member(id, ClientNames.of(id), info.getSkinLocation(), mc.level.getPlayerByUUID(id)));
        }
        var state = MatchHud.state();
        String side = team.getName().equals("Red") ? "ALPHA" : team.getName().equals("Blue") ? "BRAVO" : "";
        if (state != null && state.active()) for (var row : state.players()) {
            UUID id;
            try { id = UUID.fromString(row.id()); } catch (IllegalArgumentException notUuid) { continue; }
            if (BotDatabase.slot(id) < 0) continue;
            BotEntity bot = null;
            for (var entity : mc.level.entitiesForRendering())
                if (entity instanceof BotEntity found && found.getUUID().equals(id)) { bot = found; break; }
            // A dead bot is discarded and leaves the scoreboard team until it respawns; its match row still names the side.
            if (!(bot != null ? team.isAlliedTo(bot.getTeam()) : row.team().equals(side))) continue;
            String skin = row.skin() != null && !row.skin().isEmpty() ? row.skin() : bot != null ? bot.skin() : BotDatabase.DEFAULT_SKIN;
            members.add(new Member(id, row.name(), BotRenderer.texture(skin), bot != null && bot.isAlive() ? bot : null));
        }
        // Roster number order (the badge on the portrait); anyone without a number goes last, by name.
        members.sort(Comparator.comparingInt((Member m) -> { int n = TeamPointHud.number(m.id()); return n > 0 ? n : Integer.MAX_VALUE; })
                .thenComparing(Member::name));
        return members;
    }

    /** Teammate rows draw at 30% opacity (70% transparent) while the teammate is dead or, in prop hunt, found. */
    private static final float DEAD_OPACITY = .3f;
    private static boolean dead(Member member) {
        return RespawnPortrait.active(member.id()) || member.entity() != null && member.entity().isDeadOrDying()
                || PropHuntClient.found(member.id());
    }

    /** The older wide teammate row: portrait, name, long health bar and an item slot. */
    private static void legacy(GuiGraphics g, Minecraft mc, Member member, int x, int y, float partialTick) {
        LivingEntity p = member.entity();
        ShooterHud.portrait(g, x, y, 30, teamColor(mc.player));
        PlayerFaceRenderer.draw(g, member.skin(), x + 2, y + 2, 26);
        TeamPointHud.badge(g,member.id(),x+2,y+2,26);
        Integer level = PlayerLevels.level(member.id());
        if (level != null) LevelEmblem.draw(g, level, x + 34, y + 1, 12);
        int nameX = level != null ? x + 49 : x + 34;
        HudText.drawFit(g, member.name(), nameX, y + 1, x + 114 - nameX, 0xFFFFFFFF);
        ShooterHud.card(g, x + 34, y + 16, 80, 14, ShooterHud.BAR_EDGE, BAR_TRACK, 0);
        ShooterHud.card(g, x + 118, y + 8, 22, 22, ShooterHud.EDGE, SLOT_SURFACE, 0);
        if (p != null) {
            var layers = HudHealthBars.layers(p, partialTick);
            float fraction = layers.absorptionEnd();
            HudHealthBars.render(g, x + 35, y + 17, 78, 12, layers, 0xFFF5F5F0);
            ShooterHud.finish(g, x + 35, y + 17, 78, 12, fraction);
            HudText.healthNumber(g, HudHealth.label(p.getHealth(), p.getMaxHealth(),p.getAbsorptionAmount()), x + 36, y + 16, 76, 14, 0xFFFFFFFF, 0.78f);
            float scale = (float) (2.0 / mc.getWindow().getGuiScale());
            g.enableScissor(Math.round((x + 35) * scale), Math.round((y + 17) * scale),
                    Math.round((x + 35 + 78 * fraction) * scale), Math.round((y + 29) * scale));
            HudText.healthNumber(g, HudHealth.label(p.getHealth(), p.getMaxHealth(),p.getAbsorptionAmount()), x + 36, y + 16, 76, 14, 0xFF111820, 0.78f);
            g.disableScissor();
            if (!disguise(g, member.id(), x + 118, y + 8)) g.renderItem(p.getMainHandItem(), x + 121, y + 11);
        } else if (TeamPointHud.vital(member.id()) != null) {
            // Out of tracking range: the health the server reports for the team.
            var vital = TeamPointHud.vital(member.id());
            float maximum = Math.max(1, vital.maximum()), fraction = Math.min(1, (vital.health() + vital.absorption()) / maximum);
            HudHealthBars.fill(g, x + 35, y + 17, 78, 12, Math.min(1, vital.health() / maximum), 0xFFF5F5F0);
            ShooterHud.finish(g, x + 35, y + 17, 78, 12, fraction);
            String label = HudHealth.label(vital.health(), maximum, vital.absorption());
            HudText.healthNumber(g, label, x + 36, y + 16, 76, 14, 0xFFFFFFFF, 0.78f);
            float scale = (float) (2.0 / mc.getWindow().getGuiScale());
            g.enableScissor(Math.round((x + 35) * scale), Math.round((y + 17) * scale),
                    Math.round((x + 35 + 78 * Math.min(1, vital.health() / maximum)) * scale), Math.round((y + 29) * scale));
            HudText.healthNumber(g, label, x + 36, y + 16, 76, 14, 0xFF111820, 0.78f);
            g.disableScissor();
        } else {
            HudText.draw(g, "--", x + 36, y + 18, 76, 0xFFAAAAAA);
        }
        if (p == null && !disguise(g, member.id(), x + 118, y + 8)) {
            // Out of tracking range: the held item the server reports.
            var held = TeamPointHud.held(member.id());
            if (!held.isEmpty()) g.renderItem(held, x + 121, y + 11);
        }
        if (PropHuntClient.disguiseOf(member.id()) == null && AcousticConfig.TEAM_WEAPON_NUMBERS.get()) {
            g.pose().pushPose(); g.pose().translate(0, 0, 180);
            String[] weapons = TeamPointHud.weapons(member.id());
            if (weapons != null) {
                if (!weapons[0].isEmpty()) HudText.smallNumber(g, weapons[0], x + 139, y + 9, .62f, PRIMARY_NUMBER);
                if (!weapons[1].isEmpty()) HudText.smallNumber(g, weapons[1], x + 139, y + 23, .62f, SECONDARY_NUMBER);
            }
            g.pose().popPose();
        }
    }

    /**
     * The compact teammate row (legacy layout off): the portrait, then beside it the roster number badge and the held
     * item (a hider's disguise icon and catalog number in prop hunt), a short health bar, and the health below it.
     */
    private static void compact(GuiGraphics g, Minecraft mc, Member member, int x, int y, float partialTick) {
        LivingEntity p = member.entity();
        ShooterHud.portrait(g, x, y, 30, teamColor(mc.player));
        PlayerFaceRenderer.draw(g, member.skin(), x + 2, y + 2, 26);
        int left = x + 34, barW = 48;
        TeamPointHud.square(g, member.id(), left, y + 1, 11);
        // Item slot right of the badge: no box of its own, only the item (or the disguise and its number).
        int slotX = left + 15;
        var row = PropHuntClient.disguiseOf(member.id());
        if (row != null) {
            var state = row.state() != 0 ? net.minecraft.world.level.block.Block.stateById(row.state()) : dev.gungang.acoustics.server.PropCatalog.state(row.block());
            PropAbilityHud.icon(g, row.block(), state, slotX, y, 12);
            g.pose().pushPose(); g.pose().translate(slotX + 14, y + 3, 180); g.pose().scale(.8f, .8f, 1);
            HudText.drawNumber(g, HudText.number(dev.gungang.acoustics.server.PropCatalog.number(row.block())), 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
        } else {
            // Out of tracking range (p == null): the held item the server reports, so a far teammate keeps their weapon.
            var held = p != null ? p.getMainHandItem() : TeamPointHud.held(member.id());
            if (!held.isEmpty()) {
                g.pose().pushPose(); g.pose().translate(slotX, y, 0); g.pose().scale(.75f, .75f, 1);
                g.renderItem(held, 0, 0); g.pose().popPose();
            }
            if (AcousticConfig.TEAM_WEAPON_NUMBERS.get()) weaponNumbers(g, member.id(), slotX + 14, y);
        }
        int barY = y + 16;
        // Drop shadow one pixel down-right, only where it shows past the bar; then the track (its frame comes last).
        g.fill(left + barW, barY + 1, left + barW + 1, barY + 7, 0x66000000);
        g.fill(left + 1, barY + 6, left + barW, barY + 7, 0x66000000);
        g.fill(left, barY, left + barW, barY + 6, BAR_TRACK);
        String label;
        if (p != null) {
            var layers = HudHealthBars.layers(p, partialTick);
            HudHealthBars.render(g, left + 1, barY + 1, barW - 2, 4, layers, 0xFFF5F5F0);
            ShooterHud.finish(g, left + 1, barY + 1, barW - 2, 4, layers.absorptionEnd());
            label = HudHealth.label(p.getHealth(), p.getMaxHealth(), p.getAbsorptionAmount());
        } else if (TeamPointHud.vital(member.id()) != null) {
            // Out of tracking range: the health the server reports for the team.
            var vital = TeamPointHud.vital(member.id());
            float maximum = Math.max(1, vital.maximum());
            HudHealthBars.fill(g, left + 1, barY + 1, barW - 2, 4, Math.min(1, vital.health() / maximum), 0xFFF5F5F0);
            ShooterHud.finish(g, left + 1, barY + 1, barW - 2, 4, Math.min(1, (vital.health() + vital.absorption()) / maximum));
            label = HudHealth.label(vital.health(), maximum, vital.absorption());
        } else label = null;
        hairline(g, left, barY, barW, 6, ShooterHud.BAR_EDGE);
        if (label != null) {
            // A translucent black drop shadow one pixel down-right, then the white number.
            HudText.healthNumber(g, label, left + 1, y + 24, barW + 20, 9, 0x80000000, 0.78f);
            HudText.healthNumber(g, label, left, y + 23, barW + 20, 9, 0xFFFFFFFF, 0.78f);
        }
        else HudText.draw(g, "--", left, y + 23, barW, 0xFFAAAAAA);
    }

    static final int PRIMARY_NUMBER = 0xFFFFFFFF, SECONDARY_NUMBER = 0xFFFFD84A;
    /**
     * Where a hider's block number goes: the chosen primary weapon number (white) on top and the secondary (yellow)
     * below, each with the number font's translucent black shadow.
     */
    private static void weaponNumbers(GuiGraphics g, UUID id, int x, int y) {
        String[] weapons = TeamPointHud.weapons(id);
        if (weapons == null) return;
        g.pose().pushPose(); g.pose().translate(x, y, 180); g.pose().scale(.62f, .62f, 1);
        if (!weapons[0].isEmpty()) HudText.drawNumber(g, HudText.number(weapons[0]), 0, 0, PRIMARY_NUMBER);
        if (!weapons[1].isEmpty()) HudText.drawNumber(g, HudText.number(weapons[1]), 0, 10, SECONDARY_NUMBER);
        g.pose().popPose();
    }

    /** A frame half a GUI pixel thick around (x, y, w, h). */
    private static void hairline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.pose().pushPose(); g.pose().scale(.5f, .5f, 1);
        int x2 = x * 2, y2 = y * 2, w2 = w * 2, h2 = h * 2;
        g.fill(x2, y2, x2 + w2, y2 + 1, color); g.fill(x2, y2 + h2 - 1, x2 + w2, y2 + h2, color);
        g.fill(x2, y2 + 1, x2 + 1, y2 + h2 - 1, color); g.fill(x2 + w2 - 1, y2 + 1, x2 + w2, y2 + h2 - 1, color);
        g.pose().popPose();
    }

    /**
     * Prop hunt hider: the 22px item slot at (x, y) shows the disguise icon and its catalog number instead of the
     * held item. False when the member is not a disguised hider.
     */
    private static boolean disguise(GuiGraphics g, UUID id, int x, int y) {
        var row = PropHuntClient.disguiseOf(id);
        if (row == null) return false;
        var state = row.state() != 0 ? net.minecraft.world.level.block.Block.stateById(row.state()) : dev.gungang.acoustics.server.PropCatalog.state(row.block());
        PropAbilityHud.icon(g, row.block(), state, x + 3, y + 3, 16);
        // Above the item (z 150), like the disguise tiles at the top of the screen.
        g.pose().pushPose();
        g.pose().translate(0, 0, 180);
        HudText.smallNumber(g, dev.gungang.acoustics.server.PropCatalog.number(row.block()), x + 21, y + 14, .62f, 0xFFFFFFFF);
        g.pose().popPose();
        return true;
    }

    private static int actor(GuiGraphics g, Component name, int right, int y) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = null;
        UUID id = null;
        HoverEvent hover = name.getStyle().getHoverEvent();
        if (hover != null) {
            var entity = hover.getValue(HoverEvent.Action.SHOW_ENTITY);
            if (entity != null) { id = entity.id; info = mc.getConnection().getPlayerInfo(entity.id); }
        }
        if (info == null) info = mc.getConnection().getPlayerInfo(name.getString());
        // A player's nickname from the Tab list, whatever name the server's death message carried.
        String shown = info != null ? ClientNames.of(info.getProfile().getId()) : name.getString();
        int left = right - Math.min(62, mc.font.width(shown)) - 20;
        // Bots have no PlayerInfo: take their profile skin from the match rows, else from the loaded entity.
        String botSkin = info == null && id != null ? botSkin(mc, id) : null;
        var skin = botSkin != null ? BotRenderer.texture(botSkin)
                : info == null ? DefaultPlayerSkin.getDefaultSkin() : info.getSkinLocation();
        var team = info != null ? info.getTeam()
                : mc.level.getScoreboard().getPlayersTeam(id != null && botSkin != null ? id.toString() : name.getString());
        Integer teamRgb = team == null ? null : team.getColor().getColor();
        int color = 0xFF000000 | (teamRgb == null ? 0xFFFFFF : teamRgb);
        if(team!=null&&team.getName().equals("Red"))color=LobbyClient.options.color("ALPHA");
        if(team!=null&&team.getName().equals("Blue"))color=LobbyClient.options.color("BRAVO");
        g.fill(left, y, left + 16, y + 16, color);
        PlayerFaceRenderer.draw(g, skin, left + 1, y + 1, 14);
        Marquee.draw(g, mc.font, shown, left + 20, y + 4, 62, color, false);
        return left;
    }

    static String botSkin(Minecraft mc, UUID id) {
        var state = MatchHud.state();
        if (state != null) for (var row : state.players())
            if (row.id().equals(id.toString()) && row.skin() != null && !row.skin().isEmpty()) return row.skin();
        if(mc.level!=null)for (var entity : mc.level.entitiesForRendering())
            if (entity instanceof BotEntity bot && bot.getUUID().equals(id)) return bot.skin();
        return null;
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
