package dev.gungang.acoustics.server;

import java.util.*;

/**
 * Distance damage falloff generated for a TaCZ gun's {@code damage_adjust} list ({@code /gungang modify ui}).
 * Closer than {@code start} the gun deals its base damage; from {@code start} to {@code end} the damage moves from
 * {@code startValue} to {@code endValue} along {@code curve}; beyond {@code end} it stays at {@code endValue} (the
 * threshold). Values are damage, or with mode "reduction" a percentage cut from the base damage. TaCZ applies the
 * list in steps, so the curve is written (and previewed) one block at a time: block d uses the curve at d.
 */
public record DamageFalloff(String curve, String mode, double start, double startValue, double end, double endValue, double strength, String expression) {
    public static final List<String> CURVES = List.of("linear", "exponential", "log", "sigmoid", "z", "custom");
    public static final Map<String, String> CURVE_NAMES = Map.of("linear", "선형", "exponential", "지수", "log", "로그", "sigmoid", "시그모이드", "z", "Z곡선", "custom", "자유 방정식");
    public static final String DAMAGE = "damage", REDUCTION = "reduction";
    /** Largest distance (blocks) and span the generator writes. */
    public static final int MAX_DISTANCE = 512, MAX_SPAN = 256;

    /** Throws with a readable message when the settings cannot produce a curve. */
    public void validate() {
        if (!CURVES.contains(curve)) throw new IllegalArgumentException("알 수 없는 곡선: " + curve);
        if (!DAMAGE.equals(mode) && !REDUCTION.equals(mode)) throw new IllegalArgumentException("알 수 없는 값 종류: " + mode);
        if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end > MAX_DISTANCE || end <= start)
            throw new IllegalArgumentException("거리: 0 ≤ 시작 거리 < 끝 거리 ≤ " + MAX_DISTANCE + " 블록으로 입력하세요.");
        if (end - start > MAX_SPAN) throw new IllegalArgumentException("감소 구간은 " + MAX_SPAN + "블록 이하로 입력하세요.");
        double max = REDUCTION.equals(mode) ? 100 : 10000;
        for (double v : new double[]{startValue, endValue})
            if (!Double.isFinite(v) || v < 0 || v > max) throw new IllegalArgumentException((REDUCTION.equals(mode) ? "감소율: 0~100%" : "피해: 0~10000") + " 범위로 입력하세요.");
        if (!curve.equals("linear") && !curve.equals("z") && !curve.equals("custom") && (!Double.isFinite(strength) || strength <= 0 || strength > 100))
            throw new IllegalArgumentException("강도 k: 0 초과 100 이하로 입력하세요.");
        if (curve.equals("custom")) node();
    }
    private FalloffExpression.Node node() { return FalloffExpression.compile(expression); }

    /** A value in damage units. */
    public double damage(double value, double base) { return REDUCTION.equals(mode) ? base * (1 - value / 100) : value; }
    /** 0 at the start, 1 at the end. */
    double shape(double t) {
        double k = strength;
        return switch (curve) {
            case "exponential" -> (1 - Math.exp(-k * t)) / (1 - Math.exp(-k));
            case "log" -> Math.log1p(k * t) / Math.log1p(k);
            case "sigmoid" -> {
                double lo = 1 / (1 + Math.exp(k / 2)), hi = 1 / (1 + Math.exp(-k / 2));
                yield (1 / (1 + Math.exp(-k * (t - .5))) - lo) / (hi - lo);
            }
            case "z" -> t < .5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t);
            default -> t;
        };
    }
    /** An evaluator for repeated use (compiles a free equation once). */
    public java.util.function.DoubleBinaryOperator evaluator() {
        var node = curve.equals("custom") ? node() : null;
        return (d, base) -> {
            if (d < start) return base;
            double vs = damage(startValue, base), ve = damage(endValue, base);
            if (d >= end) return round(ve);
            double t = (d - start) / (end - start), value;
            if (node != null) value = node.eval(new double[]{d, t, base, vs, ve, strength, start, end});
            else value = vs + (ve - vs) * shape(t);
            return round(Double.isFinite(value) ? Math.max(0, Math.min(10000, value)) : 0);
        };
    }
    /** Damage at whole block {@code d}, as the generated list applies it. */
    public double damageAt(double d, double base) { return evaluator().applyAsDouble(Math.floor(d), base); }
    static double round(double value) { return Math.round(value * 100) / 100.0; }

    /**
     * TaCZ bands {distance, damage}: a band applies to distances below its distance; the last one is open-ended
     * (distance {@link Double#POSITIVE_INFINITY}). Neighbouring blocks with the same damage share a band.
     */
    public List<double[]> bands(double base) {
        validate();
        var at = evaluator();
        var out = new ArrayList<double[]>();
        if (start > 0) out.add(new double[]{start, round(base)});
        for (double d = start; d < end; d++) {
            double damage = at.applyAsDouble(d, base), until = Math.min(end, d + 1);
            if (!out.isEmpty() && out.get(out.size() - 1)[1] == damage) out.get(out.size() - 1)[0] = until;
            else out.add(new double[]{until, damage});
        }
        double last = round(damage(endValue, base));
        if (!out.isEmpty() && out.get(out.size() - 1)[1] == last) out.remove(out.size() - 1);
        out.add(new double[]{Double.POSITIVE_INFINITY, last});
        return out;
    }
}
