package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class RespawnPortrait {
    private static final Map<UUID, Long> STARTED = new HashMap<>();
    private static final Set<UUID> DEAD = new HashSet<>();
    private static long clock;

    public static void death(Component name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        UUID id = null;
        HoverEvent hover = name.getStyle().getHoverEvent();
        if (hover != null) {
            var entity = hover.getValue(HoverEvent.Action.SHOW_ENTITY);
            if (entity != null) id = entity.id;
        }
        if (id == null) {
            var info = mc.getConnection().getPlayerInfo(name.getString());
            if (info != null) id = info.getProfile().getId();
        }
        if (id != null) STARTED.putIfAbsent(id, clock);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        clock++;
        STARTED.entrySet().removeIf(entry -> clock - entry.getValue() >= 60);
        for (Player player : mc.level.players()) {
            UUID id = player.getUUID();
            if (player.isDeadOrDying()) {
                if (DEAD.add(id)) STARTED.putIfAbsent(id, clock);
            } else DEAD.remove(id);
        }
        DEAD.removeIf(id -> mc.level.getPlayerByUUID(id) == null);
    }

    static boolean render(GuiGraphics g, UUID id, int x, int y, int size) {
        Minecraft mc = Minecraft.getInstance();
        Long start = STARTED.get(id);
        if (start == null) return false;
        float elapsed = clock - start + (mc.isPaused() ? 0 : mc.getFrameTime());
        if (elapsed >= 60) return false;
        int cleared = (int) (size * Math.max(0, elapsed) / 60f);
        g.fill(x, y + cleared, x + size, y + size, 0xEE000000);
        int crossSize = Math.max(5, size / 5);
        int cx = x + size / 2, cy = y + size / 3;
        for (int i = -crossSize / 2; i <= crossSize / 2; i++) {
            g.fill(cx + i - 1, cy + i - 1, cx + i + 2, cy + i + 2, 0xFF000000);
            g.fill(cx + i - 1, cy - i - 1, cx + i + 2, cy - i + 2, 0xFF000000);
        }
        for (int i = -crossSize / 2; i <= crossSize / 2; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, 0xFFFFFFFF);
            g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, 0xFFFFFFFF);
        }
        String label = Component.translatable("gungang.hud.respawning", (int) Math.ceil((60 - elapsed) / 20)).getString();
        float scale = Math.min(1f, (size - 2f) / Math.max(1, mc.font.width(label)));
        g.pose().pushPose();
        g.pose().translate(cx, y + size * 0.65f, 0);
        g.pose().scale(scale, scale, 1);
        int left = -mc.font.width(label) / 2;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            if (dx != 0 || dy != 0) g.drawString(mc.font, label, left + dx, dy, 0xFF000000, false);
        g.drawString(mc.font, label, left, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
        return true;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STARTED.clear(); DEAD.clear(); clock = 0;
    }
    private RespawnPortrait() { }
}
