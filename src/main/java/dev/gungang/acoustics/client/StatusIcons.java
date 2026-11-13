package dev.gungang.acoustics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gungang.acoustics.server.GimmickBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Status effect icons inside the health bar, right-aligned: GunGang's own icons for 재생, 지휘 깃발, 신속, 투명화 (buffs)
 * and 화상, 중독, 감전, 출혈, 피폭, 멀미 (debuffs), then every other visible effect with its vanilla icon. Timed effects
 * blink in their last three seconds. While the 83 stun baton's 감전 lasts, its slowness and glowing show only as 감전. Statuses only the server knows (flag aura, fallout, recovery potion) arrive with
 * the utility HUD ({@code status_*} gauges) and exist for the local player only.
 */
public final class StatusIcons {
    public enum Icon {
        HEAL, FLAG, SWIFT, INVISIBLE, BURN, POISON, SHOCK, BLEED, EXPOSURE, NAUSEA;
        final ResourceLocation texture = new ResourceLocation("gungang", "textures/gui/effect/" + name().toLowerCase(java.util.Locale.ROOT) + ".png");
    }
    /** One icon: GunGang's ({@code icon}) or a vanilla effect's ({@code effect}); {@code ticks} left, -1 for no end. */
    record Entry(Icon icon, MobEffect effect, int ticks) { }
    /** Gap between icons, and the icons' inset from the bar's top and bottom edges. */
    static final int GAP = 2, INSET = 2;
    /** Blink while this many ticks or fewer remain. */
    static final int ENDING = 60;

    /**
     * Draws the icons right-aligned against {@code right}, leaving {@code left} clear (the health number); icons that do
     * not fit are dropped from the end of the list (vanilla extras before GunGang's own).
     */
    public static void render(GuiGraphics g, LivingEntity entity, int left, int right, int y, int height, float partial) {
        var mc = Minecraft.getInstance();
        if (entity == null || !entity.isAlive()) return;
        var entries = entries(entity, entity == mc.player);
        int size = height - INSET * 2;
        if (entries.isEmpty() || size < 6) return;
        int fit = Math.max(0, (right - left + GAP) / (size + GAP));
        if (fit == 0) return;
        if (entries.size() > fit) entries = entries.subList(0, fit);
        int x = right - entries.size() * (size + GAP) + GAP, top = y + INSET;
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        RenderSystem.enableBlend();
        for (var entry : entries) {
            float alpha = blink(entry.ticks(), time, partial);
            g.setColor(1, 1, 1, alpha);
            if (entry.icon() != null) g.blit(entry.icon().texture, x, top, size, size, 0f, 0f, 64, 64, 64, 64);
            else g.blit(x, top, 0, size, size, mc.getMobEffectTextures().get(entry.effect()));
            g.setColor(1, 1, 1, 1);
            x += size + GAP;
        }
        RenderSystem.disableBlend();
    }
    /** Full alpha, or a soft pulse during the last {@link #ENDING} ticks. */
    static float blink(int ticks, long time, float partial) {
        if (ticks < 0 || ticks > ENDING) return 1;
        return .45f + .55f * (float) (.5 + .5 * Math.cos((time + partial) * Math.PI / 5));
    }

    /** Effects GunGang draws with its own icons; every other visible effect keeps its vanilla icon. */
    private static Set<MobEffect> owned() {
        return Set.of(MobEffects.REGENERATION, MobEffects.MOVEMENT_SPEED, MobEffects.INVISIBILITY, MobEffects.POISON,
                MobEffects.CONFUSION, GimmickBlocks.ACID_POISON.get(), GimmickBlocks.BLEEDING.get());
    }
    /** Buffs, then debuffs, then the rest. {@code self}: the local player, who also has the server-only statuses. */
    static List<Entry> entries(LivingEntity entity, boolean self) {
        var list = new ArrayList<Entry>();
        int regen = ticks(entity, MobEffects.REGENERATION);
        if (regen != Integer.MIN_VALUE || self && UtilityHud.status("heal")) list.add(new Entry(Icon.HEAL, null, regen == Integer.MIN_VALUE ? -1 : regen));
        if (self && UtilityHud.status("flag")) list.add(new Entry(Icon.FLAG, null, -1));
        add(list, entity, Icon.SWIFT, MobEffects.MOVEMENT_SPEED);
        add(list, entity, Icon.INVISIBLE, MobEffects.INVISIBILITY);
        if (entity.isOnFire()) list.add(new Entry(Icon.BURN, null, -1));
        int poison = Math.max(ticks(entity, MobEffects.POISON), ticks(entity, GimmickBlocks.ACID_POISON.get()));
        if (poison != Integer.MIN_VALUE) list.add(new Entry(Icon.POISON, null, poison));
        // 감전: an ARC-12 arc, or the 83 stun baton (whose slowness and glowing are then shown as this one icon).
        boolean stun = self && UtilityHud.status("shock");
        if (self && ArcShockClient.shocked() || stun) list.add(new Entry(Icon.SHOCK, null, -1));
        add(list, entity, Icon.BLEED, GimmickBlocks.BLEEDING.get());
        if (self && UtilityHud.status("exposure")) list.add(new Entry(Icon.EXPOSURE, null, -1));
        add(list, entity, Icon.NAUSEA, MobEffects.CONFUSION);
        var owned = owned();
        for (var instance : entity.getActiveEffects()) {
            if (owned.contains(instance.getEffect()) || !instance.showIcon()) continue;
            if (stun && (instance.getEffect() == MobEffects.MOVEMENT_SLOWDOWN || instance.getEffect() == MobEffects.GLOWING)) continue;
            list.add(new Entry(null, instance.getEffect(), instance.isInfiniteDuration() ? -1 : instance.getDuration()));
        }
        return list;
    }
    private static void add(List<Entry> list, LivingEntity entity, Icon icon, MobEffect effect) {
        int t = ticks(entity, effect);
        if (t != Integer.MIN_VALUE) list.add(new Entry(icon, null, t));
    }
    /** Ticks left of {@code effect} (-1: endless), or {@link Integer#MIN_VALUE} when it is not active. */
    private static int ticks(LivingEntity entity, MobEffect effect) {
        var instance = entity.getEffect(effect);
        if (instance == null) return Integer.MIN_VALUE;
        return instance.isInfiniteDuration() ? -1 : instance.getDuration();
    }
    private StatusIcons() { }
}
