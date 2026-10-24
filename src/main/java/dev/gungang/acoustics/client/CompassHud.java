package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class CompassHud {
    private record Ping(Vec3 origin, long tick) { }
    private static final LinkedHashMap<UUID, Ping> PINGS = new LinkedHashMap<>();
    private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    public static void soundPlayed(SoundInstance sound) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) { mc.execute(() -> soundPlayed(sound)); return; }
        if (!AcousticConfig.COMPASS.get() || !ShooterHud.visible(mc) || sound.isRelative()
                || sound.getVolume() <= 0 || !sound.canPlaySound()
                || mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || mc.options.getSoundSourceVolume(sound.getSource()) <= 0 || dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.WEAPON_VOLUME) == 0) return;
        Player shooter = null;
        String path = sound.getLocation().getPath();
        if (sound instanceof GunSoundSource source) {
            if (source.gungang$shooter() instanceof Player p) shooter = p;
            if (source.gungang$soundId() != null) path = source.gungang$soundId().getPath();
        }
        if (!CompassMath.gunshot(path)) return;
        Vec3 origin = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        if (shooter == null) {
            double nearest = 4;
            for (Player p : mc.level.players()) {
                double distance = p.position().distanceToSqr(origin);
                if (distance < nearest) { nearest = distance; shooter = p; }
            }
        }
        // Require explicit opposing scoreboard teams; unknown sources are not marked as enemies.
        if (shooter == null || shooter == mc.player || shooter.isSpectator()
                || shooter.getTeam() == null || mc.player.getTeam() == null
                || mc.player.getTeam().isAlliedTo(shooter.getTeam())) return;
        PINGS.put(shooter.getUUID(), new Ping(origin, mc.level.getGameTime()));
        while (PINGS.size() > 32) PINGS.remove(PINGS.keySet().iterator().next());
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { PINGS.clear(); }
    @SubscribeEvent public static void beginFrame(RenderGuiEvent.Pre event) { BossBarHud.beginFrame(); }

    static void render(GuiGraphics g, int center, int bottom, int width, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!AcousticConfig.COMPASS.get()) { PINGS.clear(); return; }
        int half = Math.max(1, Math.min(110, (width - 8) / 2));
        int y = Math.max(22, bottom - 7);
        double heading = CompassMath.normalize(mc.player.getViewYRot(partialTick) + 180);
        g.fill(center - half, y, center + half, y + 1, 0x99FFFFFF);
        // One-degree ticks and eight direction labels; cardinal directions are highlighted.
        double pixelsPerDegree = half / 60.0;
        for (int offset = -60; offset <= 60; offset++) {
            int degree = (int) Math.floor(heading) + offset;
            int normalized = Math.floorMod(degree, 360);
            int x = center + (int) Math.round((degree - heading) * pixelsPerDegree);
            int height = normalized % 15 == 0 ? 4 : normalized % 5 == 0 ? 3 : 1;
            g.fill(x, y - height, x + 1, y, 0xBBFFFFFF);
            if (normalized % 45 == 0) label(g, DIRECTIONS[normalized / 45], x, y - 13,
                    normalized % 90 == 0 ? 0xFFFF5555 : 0xFFFFFFFF);
        }
        label(g, String.format(java.util.Locale.ROOT, "%03d°", CompassMath.heading(mc.player.getViewYRot(partialTick))), center, y - 23, 0xFFFFFFFF);
        g.fill(center, y - 5, center + 1, y + 3, 0xFFFFFFFF);
        TeamPointHud.compass(g,center,y-34,heading,pixelsPerDegree);
        long now = mc.level.getGameTime();
        PINGS.entrySet().removeIf(e -> now - e.getValue().tick > 50 || now < e.getValue().tick);
        for (Ping ping : PINGS.values()) {
            Vec3 delta = ping.origin.subtract(mc.player.getPosition(partialTick));
            double angle = CompassMath.difference(CompassMath.bearing(delta.x, delta.z), heading);
            int x = center + (int) Math.round(Math.max(-60, Math.min(60, angle)) * pixelsPerDegree);
            int alpha = (int) (255 * Math.max(0, 1 - (now + partialTick - ping.tick) / 50.0));
            int color = alpha << 24 | 0xFF654F;
            if (alpha < 8) continue;
            label(g, angle < -60 ? "<" : angle > 60 ? ">" : "!", x, y + 2, color);
        }
    }
    private static void label(GuiGraphics g, String text, int x, int y, int color) {
        var font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        g.pose().popPose();
    }
    private CompassHud() { }
}
