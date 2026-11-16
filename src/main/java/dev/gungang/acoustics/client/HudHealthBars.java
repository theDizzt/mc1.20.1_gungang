package dev.gungang.acoustics.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

final class HudHealthBars {
    // Entity identity isolates respawns/world changes; weak keys release removed players.
    private static final Map<LivingEntity, HealthBarAnimation> ANIMATIONS = new WeakHashMap<>();

    private static final Map<LivingEntity, HealthBarAnimation> ABSORPTION = new WeakHashMap<>();
    private static final Map<LivingEntity, Edges> EDGES = new WeakHashMap<>();

    /**
     * Eased ends of the green heal ghost and the purple status share. Their goals come from the health the bar is
     * heading to (not the eased value), so a heal or status tick does not jerk them while the bar is still animating,
     * and the easing hides the frame or two between the health update and the pending-amount packet.
     */
    private static final class Edges {
        /** A rise of the green end larger than this share of the bar is a new heal and appears without easing. */
        private static final float HEAL_JUMP = .03f;
        private float healEnd, statusStart;
        private double last = -1;
        /**
         * {@code healing} / {@code ailing}: whether a gadget heal or a status ailment is running. Without one the edge
         * sits exactly on health, so plain damage (explosions, shocks, gunfire) and natural regeneration, which only
         * animate the bar, never show a purple or green band.
         */
        void update(float health, float healGoal, boolean healing, float statusGoal, boolean ailing, double seconds) {
            double dt = seconds - last;
            boolean reset = last < 0 || dt < 0 || dt > 1;
            float k = reset ? 1 : (float) (1 - Math.exp(-dt * 14));
            // Green shows how far a heal pack / recovery potion will take health. A heal pack's jump shows its full
            // amount at once; small steps (a potion tick landing a packet before its pending amount) ease. When
            // health drops the end follows at once, so the damage trail keeps its own grey instead of turning green.
            healEnd = !healing ? health : healGoal < healEnd || healGoal - healEnd > HEAL_JUMP ? healGoal
                    : healEnd + (healGoal - healEnd) * k;
            statusStart = !ailing ? health : statusStart + (statusGoal - statusStart) * k;
            last = seconds;
        }
    }

    /** Health a running status ailment will still take: the end of health in this colour. */
    static final int STATUS = 0xFFA45CF0;
    /** Health a heal is still restoring (filling in, or a recovery potion still running): the pastel green ghost after health. */
    static final int HEAL = 0xFFB2EBC2;
    /**
     * {@code statusStart}: where the share of health that status ailments will still take begins (purple to {@code health}).
     * {@code healEnd}: where health will be once running heals finish (green from {@code health}); absorption follows it.
     */
    record Layers(float health, float healthTrail, float absorptionEnd, float absorptionTrailEnd, float statusStart, float healEnd,
                  int effects, boolean styled, boolean inverted) {
        Layers(float health,float healthTrail,float absorptionEnd,float absorptionTrailEnd,float statusStart,float healEnd){
            this(health,healthTrail,absorptionEnd,absorptionTrailEnd,statusStart,healEnd,0,false,false);
        }
        Layers(float health, float healthTrail, float absorptionEnd, float absorptionTrailEnd, float statusStart) { this(health, healthTrail, absorptionEnd, absorptionTrailEnd, statusStart, health); }
        Layers(float health, float healthTrail, float absorptionEnd, float absorptionTrailEnd) { this(health, healthTrail, absorptionEnd, absorptionTrailEnd, health, health); }
        int colorAt(float point, int healthColor) {
            int color=baseColor(point,healthColor);
            return inverted?(color&0xFF000000)|(~color&0xFFFFFF):color;
        }
        private float effectStart(){return statusStart<health?statusStart:0;}
        private int baseColor(float point,int healthColor){
            if(styled&&point<health){
                int count=Integer.bitCount(effects&15);
                float start=effectStart();
                if(count==0||point<start)return healthColor;
                int index=Math.min(count-1,(int)((point-start)/Math.max(.000001f,health-start)*count));
                for(int bit=0;bit<4;bit++)if((effects&(1<<bit))!=0&&index--==0)
                    return switch(bit){case 0->0xFFFF7900;case 1->0xFFD52BFF;case 2->0xFF8B101C;default->0xFF62FF18;};
            }
            if (point < statusStart) return healthColor;
            if (point < health) return styled?healthColor:STATUS;
            if (point < healEnd) return HEAL;
            if (point < absorptionEnd) return 0xFFFFD36B;
            if (point < absorptionTrailEnd) return 0xFFB9903F;
            if (point < healthTrail) return 0xFF45494F;
            return 0x8029353E;
        }
        float[] boundaries() {
            int count=styled?Integer.bitCount(effects&15):0;
            float[] edges = new float[8+Math.max(0,count-1)];
            float[] base={0, statusStart, health, healEnd, healthTrail, absorptionEnd, absorptionTrailEnd, 1};
            System.arraycopy(base,0,edges,0,8);
            for(int i=1;i<count;i++)edges[7+i]=effectStart()+(health-effectStart())*i/count;
            java.util.Arrays.sort(edges);
            return edges;
        }
    }

    static Layers layers(LivingEntity entity, float partialTick) {
        double seconds = clock();
        var healthAnimation = ANIMATIONS.computeIfAbsent(entity, ignored -> new HealthBarAnimation());
        float health = healthAnimation.update(entity.getHealth(), entity.getMaxHealth(), seconds);
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
        // Green only for heal packs and the recovery potion; natural regeneration fills the bar plainly.
        boolean healing = StatusDamageClient.gadgetHealing(entity);
        float healGoal = HudHealth.fraction(StatusDamageClient.healEnd(entity), maximum);
        float pending = StatusDamageClient.pending(entity);
        float statusGoal = HudHealth.fraction(StatusDamageClient.statusEnd(entity), maximum);
        var edges = EDGES.computeIfAbsent(entity, ignored -> new Edges());
        edges.update(health, healGoal, healing, statusGoal, pending > 0, seconds);
        boolean styled=dev.gungang.acoustics.AcousticConfig.STATUS_HEALTH_BAR.get();
        var composed=compose(health, healthAnimation.trail(seconds), ratio, trailRatio,
                styled ? Math.max(0, health - edges.statusStart) : 0, Math.max(0, edges.healEnd - health));
        int effects=styled?StatusDamageClient.effects(entity):0;
        double gameSeconds=(entity.level().getGameTime()+partialTick)/20.0;
        boolean inverted=styled&&(effects&dev.gungang.acoustics.server.StatusBarEffects.SHOCK)!=0&&((long)Math.floor(gameSeconds*6)&1)==0;
        return new Layers(composed.health(),composed.healthTrail(),composed.absorptionEnd(),composed.absorptionTrailEnd(),
                composed.statusStart(),composed.healEnd(),effects,styled,inverted);
    }

    static Layers compose(float health, float healthTrail, float ratio, float trailRatio) { return compose(health, healthTrail, ratio, trailRatio, 0); }
    static Layers compose(float health, float healthTrail, float ratio, float trailRatio, float status) { return compose(health, healthTrail, ratio, trailRatio, status, 0); }
    /**
     * {@code status}: the share of maximum health running status ailments will still take.
     * {@code heal}: the share of maximum health running heals will still restore (drawn green, never past full health).
     */
    static Layers compose(float health, float healthTrail, float ratio, float trailRatio, float status, float heal) {
        float bonus = ratio / Math.max(0.000001f, 1 - ratio);
        float bonusTrail = trailRatio / Math.max(0.000001f, 1 - trailRatio);
        float capacity = 1 + bonusTrail;
        float base = health / capacity;
        float healEnd = Math.max(base, Math.min(1 / capacity, base + Math.max(0, heal) / capacity));
        return new Layers(base, healthTrail / capacity,
                Math.min(1, healEnd + bonus / capacity), Math.min(1, healEnd + bonusTrail / capacity),
                Math.max(0, base - Math.max(0, status) / capacity), healEnd);
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
        double seconds = clock();
        return ANIMATIONS.computeIfAbsent(player, ignored -> new HealthBarAnimation())
                .update(player.getHealth(), player.getMaxHealth(), seconds);
    }

    static float trail(LivingEntity entity, float partialTick) {
        var animation = ANIMATIONS.get(entity);
        return animation == null ? HudHealth.fraction(entity.getHealth(), entity.getMaxHealth())
                : animation.trail(clock());
    }

    /**
     * Wall-clock seconds. Entity tick counts stop for players in chunks the client does not tick (far teammates),
     * which froze their bars mid-animation even though health kept arriving.
     */
    private static double clock() { return net.minecraft.Util.getMillis() / 1000.0; }

    static void stripes(GuiGraphics graphics, int x, int y, int width, int height, float fraction, double seconds) {
        stripes(graphics, x, y, width, height, 0, fraction, seconds);
    }
    /** Stripes on the bar between {@code from} and {@code to} only, still phased from the bar's left edge. */
    static void stripes(GuiGraphics graphics, int x, int y, int width, int height, float from, float to, double seconds) {
        int skip = Math.round(width * Math.max(0, from)), filled = Math.round(width * to);
        if (filled <= skip) return;
        double phase = seconds * 10 % 18;
        for (int row = 0; row < height; row++) {
            for (int start = -height - 18; start < filled + 18; start += 18) {
                int left = (int) Math.floor(start + row + phase);
                int a = Math.max(skip, left), b = Math.min(filled, left + 5);
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
