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
    private record Wait(long until,int duration) { }
    private static final Map<UUID,Wait> SERVER = new HashMap<>();
    private static net.minecraft.client.CameraType previousCamera;
    private static long clock;

    public static void sync(java.util.List<dev.gungang.acoustics.server.MatchSnapshot.Respawn> states) {
        SERVER.clear();
        if(states!=null) for(var state:states) {
            UUID id=UUID.fromString(state.id());
            SERVER.put(id,new Wait(clock+state.remainingTicks(),Math.max(1,state.totalTicks())));
            STARTED.remove(id);
        }
    }
    public static boolean waiting(UUID id) { return SERVER.containsKey(id); }

    @SubscribeEvent public static void overlay(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null||mc.options.hideGui||!HudVisibility.enabled()) return;
        var state=SERVER.get(mc.player.getUUID());
        if(state==null) return;
        var g=event.getGuiGraphics();
        String label="부활까지 "+Math.max(0,(state.until()-clock+19)/20)+"초 · ["+LobbyClient.LOADOUT.getTranslatedKeyMessage().getString()+"] 무기 변경";
        int width=mc.font.width(label),x=(g.guiWidth()-width)/2,y=g.guiHeight()/2+45;
        g.fill(x-8,y-5,x+width+8,y+14,0xCE101A27);
        g.drawString(mc.font,label,x,y,0xFFFFFFFF,true);
    }

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
        if (id != null && !MatchHud.active()) STARTED.putIfAbsent(id, clock);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        clock++;
        int duration=LobbyClient.options.respawnSeconds()*20;
        STARTED.entrySet().removeIf(entry -> clock - entry.getValue() >= duration);
        boolean spectating=mc.player!=null&&waiting(mc.player.getUUID());
        if(spectating) {
            if(previousCamera==null) previousCamera=mc.options.getCameraType();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        } else if(previousCamera!=null) {
            mc.options.setCameraType(previousCamera); previousCamera=null;
        }
        for (Player player : mc.level.players()) {
            UUID id = player.getUUID();
            if (player.isDeadOrDying()) {
                if (DEAD.add(id) && !MatchHud.active()) STARTED.putIfAbsent(id, clock);
            } else DEAD.remove(id);
        }
        DEAD.removeIf(id -> mc.level.getPlayerByUUID(id) == null);
    }

    static boolean render(GuiGraphics g, UUID id, int x, int y, int size) {
        Minecraft mc = Minecraft.getInstance();
        Long start = STARTED.get(id);
        Wait server=SERVER.get(id);
        if (start == null && server == null) return false;
        int duration=server==null?LobbyClient.options.respawnSeconds()*20:server.duration();
        float partial=mc.isPaused()?0:mc.getFrameTime();
        float elapsed=server==null?clock-start+partial:duration-Math.max(0,server.until()-clock-partial);
        if (elapsed >= duration && server==null) return false;
        int cleared = (int) (size * Math.max(0, Math.min(duration,elapsed)) / duration);
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
        String label = Component.translatable("gungang.hud.respawning", (int) Math.ceil((duration - elapsed) / 20)).getString();
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
        STARTED.clear(); DEAD.clear(); SERVER.clear(); clock = 0;
        if(previousCamera!=null) Minecraft.getInstance().options.setCameraType(previousCamera);
        previousCamera=null;
    }
    private RespawnPortrait() { }
}
