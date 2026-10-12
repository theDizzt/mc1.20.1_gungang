package dev.gungang.acoustics.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

final class HudHealthBars {
    // Entity identity isolates respawns/world changes; weak keys release removed players.
    private static final Map<LivingEntity, HealthBarAnimation> ANIMATIONS = new WeakHashMap<>();

    private static final Map<LivingEntity, HealthBarAnimation> ABSORPTION = new WeakHashMap<>();

    record Layers(float health, float healthTrail, float absorptionEnd, float absorptionTrailEnd) {
        int colorAt(float point, int healthColor) {
            if (point < health) return healthColor;
            if (point < absorptionEnd) return 0xFFFFD36B;
            if (point < absorptionTrailEnd) return 0xFFB9903F;
            if (point < healthTrail) return 0xFF45494F;
            return 0x8029353E;
        }
        float[] boundaries() {
            float[] edges = {0, health, healthTrail, absorptionEnd, absorptionTrailEnd, 1};
            java.util.Arrays.sort(edges);
            return edges;
        }
    }

    static Layers layers(LivingEntity entity, float partialTick) {
        float health = fraction(entity, partialTick);
        double seconds = (entity.tickCount + (double) partialTick) / 20.0;
        float maximum = Math.max(1, entity.getMaxHealth());
        float amount = Math.max(0, entity.getAbsorptionAmount());
        HealthBarAnimation animation = ABSORPTION.computeIfAbsent(entity, ignored -> {
            var created = new HealthBarAnimation();
            created.update(0, 1, seconds);
            return created;
        });
        // Animate a bounded ratio with a fixed maximum, so absorption changes do not reset easing.
        float ratio = animation.update(amount / (maximum + amount), 1, seconds);
        float trailRatio = animation.trail(seconds);
        return compose(health, trail(entity, partialTick), ratio, trailRatio);
    }

    static Layers compose(float health, float healthTrail, float ratio, float trailRatio) {
        float bonus = ratio / Math.max(0.000001f, 1 - ratio);
        float bonusTrail = trailRatio / Math.max(0.000001f, 1 - trailRatio);
        float capacity = 1 + bonusTrail;
        float base = health / capacity;
        return new Layers(base, healthTrail / capacity,
                Math.min(1, base + bonus / capacity), Math.min(1, base + bonusTrail / capacity));
    }

    static void render(GuiGraphics g, int x, int y, int width, int height, Layers layers, int healthColor) {
        float[] edges = layers.boundaries();
        for (int i = 1; i < edges.length; i++) {
            float start = edges[i - 1], end = edges[i];
            if (end <= start) continue;
            int color = layers.colorAt((start + end) / 2, healthColor);
            if (color == 0x8029353E) continue; // The panel already supplies the translucent background.
            g.pose().pushPose();
            g.pose().translate(x + width * start, y, 0);
            g.pose().scale(end - start, 1, 1);
            g.fill(0, 0, width, height, color);
            g.pose().popPose();
        }
    }

    static float fraction(LivingEntity player, float partialTick) {
        double seconds = (player.tickCount + (double) partialTick) / 20.0;
        return ANIMATIONS.computeIfAbsent(player, ignored -> new HealthBarAnimation())
                .update(player.getHealth(), player.getMaxHealth(), seconds);
    }

    static float trail(LivingEntity entity, float partialTick) {
        var animation = ANIMATIONS.get(entity);
        return animation == null ? HudHealth.fraction(entity.getHealth(), entity.getMaxHealth())
                : animation.trail((entity.tickCount + (double) partialTick) / 20.0);
    }

    static void stripes(GuiGraphics graphics, int x, int y, int width, int height, float fraction, double seconds) {
        int filled = Math.round(width * fraction);
        double phase = seconds * 10 % 18;
        for (int row = 0; row < height; row++) {
            for (int start = -height - 18; start < filled + 18; start += 18) {
                int left = (int) Math.floor(start + row + phase);
                int a = Math.max(0, left), b = Math.min(filled, left + 5);
                if (b > a) graphics.fill(x + a, y + row, x + b, y + row + 1, 0x18232D38);
            }
        }
    }

    static void fill(GuiGraphics graphics, int x, int y, int width, int height, float fraction, int color) {
        if (fraction <= 0) return;
        // Transform the rectangle instead of rounding its edge to whole HUD units.
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(fraction, 1, 1);
        graphics.fill(0, 0, width, height, color);
        graphics.pose().popPose();
    }

    private HudHealthBars() { }
}
