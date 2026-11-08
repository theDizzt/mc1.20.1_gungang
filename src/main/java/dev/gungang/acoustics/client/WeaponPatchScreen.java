package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Live weapon patch ({@code /gungang modify ui}): the primary / secondary tabs and weapon list on the left, the selected
 * weapon's GunGang equipment, display specs, item effect settings (secondaries) and TaCZ gun stats on the right.
 * Changed boxes are marked and sent together on apply.
 */
public final class WeaponPatchScreen extends ArenaScreen {
    private static final int TOP = 60, ROW = 22, LIST = 150, BOX = 96;
    private static final Map<String, String> GROUPS = Map.ofEntries(
            Map.entry("gear", "GunGang 장비 · 장착 중인 플레이어에게 즉시 적용"),
            Map.entry("secondary", "GunGang 보조무기 · 로비에서 적용, 다음 지급부터"),
            Map.entry("display", "무기 선택 화면 표시 정보"),
            Map.entry("melee", "근접 공격 · 근접 무기 팩 파일 (저장 시 총기팩을 다시 읽음)"),
            Map.entry("item", "아이템 효과 · 실제 성능 (item 설정, 쿨타임은 이 값이 우선)"),
            Map.entry("element", "속성 피해 · 화상/위더/독 1회당 피해, 폭발 배수"),
            Map.entry("general", "전역 설정 · 로비에서 적용, 즉시 반영"),
            Map.entry("tacz", "TaCZ 총기 데이터 · 실제 성능 (총기팩 파일)"));
    /** Tab row above the weapon list. */
    private static final int TABS = 22;
    private WeaponPatch.View view;
    /** Typed values by row key; rows without an entry show the server's value. */
    private final Map<String, String> edits = new HashMap<>();
    private final List<Object> lines = new ArrayList<>(); // String header, WeaponPatch.Row or WEIGHT
    /** Marker line: the selected weapon's weight and resulting movement speed. */
    private static final Object WEIGHT = new Object();
    private int scroll, listScroll;
    private boolean waiting;

    private WeaponPatchScreen(WeaponPatch.View view) { super("무기 실시간 패치"); this.view = view; resetCurve(); }

    public static void receive(String json) {
        var next = ServerFiles.GSON.fromJson(json, WeaponPatch.View.class);
        if (next == null) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof WeaponPatchScreen screen) screen.update(next);
        else mc.setScreen(new WeaponPatchScreen(next));
    }
    private void update(WeaponPatch.View next) {
        boolean switched = !next.id().equals(view.id()) || !kind(next).equals(kind(view));
        // A rejected apply keeps what was typed; a switch or a successful patch shows the saved values.
        boolean saved = switched || next.error() == null || next.error().isEmpty();
        if (saved) { edits.clear(); pendingFalloff = null; }
        if (switched) scroll = 0;
        view = next; waiting = false;
        if (saved) resetCurve();
        rebuildWidgets();
    }

    // ---- Distance falloff auto-fill ----------------------------------------------------------------------------

    /** Panel lines: curve/value pickers, distances and values, strength or equation, then the graph rows. */
    private static final Object CURVE_TYPE = new Object(), CURVE_RANGE = new Object(), CURVE_SHAPE = new Object(), GRAPH = new Object(), GRAPH_FILL = new Object();
    private static final int GRAPH_ROWS = 8;
    private int curveIndex;
    private String curveMode = DamageFalloff.DAMAGE;
    private final Map<String, String> curveInput = new HashMap<>();
    /** The falloff queued by 채우기, sent with the other changes on apply. */
    private String pendingFalloff;
    private String curveNote = "";
    /** This frame's graph rectangle {x, y, w, h} (null: not visible) and its x range in blocks. */
    private int[] graphRect;
    private int graphBlocks = 1;

    private boolean hasCurve() {
        return view.falloff() != null && !kind(view).equals(WeaponPatch.GENERAL) && view.rows().stream().anyMatch(r -> r.key().equals("tacz:bullet.damage"));
    }
    private double base() { return typed("tacz:bullet.damage", 0); }
    private static String round(double value) { return number(Math.round(value * 100) / 100.0); }
    /** Fills the inputs from the gun's current damage_adjust list (or a starting suggestion). */
    private void resetCurve() {
        curveInput.clear(); curveIndex = 0; curveMode = DamageFalloff.DAMAGE; curveNote = "";
        double base = base();
        var bands = view.falloff() == null ? List.<double[]>of() : view.falloff();
        double start = 16, end = 48, vs = base, ve = Math.round(base / 2);
        if (bands.size() >= 2 && bands.get(0)[0] >= 0) {
            start = bands.get(0)[0]; vs = bands.get(1)[1];
            double last = start;
            for (var band : bands) if (band[0] >= 0) last = band[0];
            end = last > start ? last : start + 16;
            ve = bands.get(bands.size() - 1)[1];
        }
        curveInput.put("start", round(start)); curveInput.put("startValue", round(vs));
        curveInput.put("end", round(end)); curveInput.put("endValue", round(ve));
        curveInput.put("k", "4"); curveInput.put("expression", "vs+(ve-vs)*t");
    }
    private String curve() { return DamageFalloff.CURVES.get(curveIndex); }
    private double input(String key) {
        try { return Double.parseDouble(curveInput.getOrDefault(key, "").strip()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("숫자를 입력하세요: " + switch (key) { case "start" -> "시작 거리"; case "end" -> "끝 거리"; case "startValue" -> "시작 값"; case "endValue" -> "임계 값"; default -> "강도 k"; }); }
    }
    /** The typed falloff, validated; throws with the reason. */
    private DamageFalloff spec() {
        String curve = curve();
        double k = curve.equals("linear") || curve.equals("z") || curve.equals("custom") ? 1 : input("k");
        var spec = new DamageFalloff(curve, curveMode, input("start"), input("startValue"), input("end"), input("endValue"), k, curveInput.getOrDefault("expression", ""));
        spec.validate();
        return spec;
    }
    /** Damage of the gun's current list at a distance, as TaCZ applies it. */
    private double currentAt(double d, double base) {
        if (view.falloff() == null) return base;
        for (var band : view.falloff()) if (band[0] < 0 || d < band[0]) return band[1];
        return base;
    }
    /** Switching damage ↔ reduction keeps the same curve, converting the start and threshold values. */
    private void toggleMode() {
        double base = base();
        boolean toReduction = curveMode.equals(DamageFalloff.DAMAGE);
        for (String key : List.of("startValue", "endValue")) {
            try {
                double v = Double.parseDouble(curveInput.getOrDefault(key, "").strip());
                curveInput.put(key, round(toReduction ? (base <= 0 ? 0 : (1 - v / base) * 100) : base * (1 - v / 100)));
            } catch (NumberFormatException ignored) { }
        }
        curveMode = toReduction ? DamageFalloff.REDUCTION : DamageFalloff.DAMAGE;
    }
    private void curveBox(String key, int x, int y, int w, boolean numeric, String hint) {
        var box = new TacticalEditBox(font, x, y, w, 18, Component.literal(key));
        box.setMaxLength(numeric ? 12 : 200);
        if (numeric) box.setFilter(s -> s.matches("-?[0-9]*\\.?[0-9]*"));
        box.setValue(curveInput.getOrDefault(key, ""));
        if (!hint.isEmpty()) box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        box.setResponder(s -> { curveInput.put(key, s); curveNote = ""; });
        addRenderableWidget(box);
    }
    /** Widgets of one panel line at height y. */
    private void curveWidgets(Object line, int x, int y, int w) {
        if (line == CURVE_TYPE) {
            button("곡선: " + DamageFalloff.CURVE_NAMES.get(curve()) + " ▸", x + 7, y, 120, 18, !waiting, ACCENT, () -> { curveIndex = (curveIndex + 1) % DamageFalloff.CURVES.size(); curveNote = ""; rebuildWidgets(); });
            button(curveMode.equals(DamageFalloff.DAMAGE) ? "값: 피해량 ▸" : "값: 감소율 % ▸", x + 131, y, 100, 18, !waiting, ACCENT, () -> { toggleMode(); rebuildWidgets(); });
            button("현재값 불러오기", x + w - 96, y, 96, 18, !waiting, MUTED, () -> { resetCurve(); pendingFalloff = null; rebuildWidgets(); });
        } else if (line == CURVE_RANGE) {
            int seg = (w - 7) / 4, box = Math.min(48, seg - 8);
            String[] keys = {"start", "startValue", "end", "endValue"};
            for (int i = 0; i < 4; i++) curveBox(keys[i], x + 7 + seg * (i + 1) - box - 4, y, box, true, "");
        } else if (line == CURVE_SHAPE) {
            int fill = 64, room = w - 7 - fill - 8 - (pendingFalloff != null ? 48 : 0);
            if (curve().equals("custom")) curveBox("expression", x + 50, y, room - 43, false, "d t base vs ve k start end");
            else if (!curve().equals("linear") && !curve().equals("z")) curveBox("k", x + 50, y, 48, true, "4");
            if (pendingFalloff != null) button("해제", x + w - fill - 52, y, 48, 18, !waiting, MUTED, () -> { pendingFalloff = null; curveNote = ""; rebuildWidgets(); });
            button("채우기", x + w - fill, y, fill, 18, !waiting, ACCENT, () -> {
                try {
                    var spec = spec();
                    int bands = spec.bands(base()).size();
                    pendingFalloff = ServerFiles.GSON.toJson(spec);
                    curveNote = "채움 대기 · " + bands + "개 구간 · 적용을 누르면 TaCZ 데이터에 저장";
                } catch (IllegalArgumentException invalid) { pendingFalloff = null; curveNote = "! " + invalid.getMessage(); }
                rebuildWidgets();
            });
        }
    }
    /** Labels of one panel line; the graph itself is drawn by {@link #drawGraph}. */
    private void curveLabels(GuiGraphics g, Object line, int x, int y, int w) {
        if (line == CURVE_RANGE) {
            int seg = (w - 7) / 4;
            boolean reduction = curveMode.equals(DamageFalloff.REDUCTION);
            String[] labels = {"시작 거리", reduction ? "시작 감소%" : "시작 피해", "끝 거리", reduction ? "임계 감소%" : "임계 피해"};
            for (int i = 0; i < 4; i++) g.drawString(font, labels[i], x + 7 + seg * i, y + 5, MUTED, false);
        } else if (line == CURVE_SHAPE) {
            String label = curve().equals("custom") ? "f(d) =" : curve().equals("linear") || curve().equals("z") ? "" : "강도 k";
            g.drawString(font, label, x + 7, y + 5, MUTED, false);
            String note = !curveNote.isEmpty() ? curveNote : curve().equals("custom") ? "" : curve().equals("linear") || curve().equals("z") ? "강도 없음 · 채우기로 1블록 단위 구간 생성" : "";
            int noteX = curve().equals("custom") ? x + w - 64 - (pendingFalloff != null ? 56 : 8) - font.width(note) : x + (curve().equals("linear") || curve().equals("z") ? 7 : 104);
            if (!note.isEmpty() && !curve().equals("custom")) g.drawString(font, font.plainSubstrByWidth(note, x + w - 64 - (pendingFalloff != null ? 56 : 8) - noteX), noteX, y + 5, note.startsWith("!") ? 0xFFFF6666 : pendingFalloff != null ? READY : DIM, false);
        }
    }
    private void drawGraph(GuiGraphics g, int x, int y, int w) {
        int gx = x + 34, gy = y + 4, gw = w - 40, gh = GRAPH_ROWS * ROW - 20;
        double base = base();
        DamageFalloff spec = null; String error = "";
        try { spec = spec(); } catch (IllegalArgumentException invalid) { error = invalid.getMessage(); }
        double startD = spec == null ? 0 : spec.start(), endD = spec == null ? 32 : spec.end();
        int blocks = (int) Math.ceil(Math.max(24, Math.max(endD * 1.25, endD + 8)));
        var at = spec == null ? null : spec.evaluator();
        double top = Math.max(1, base);
        for (int d = 0; d <= blocks; d++) {
            top = Math.max(top, currentAt(d, base));
            if (at != null) top = Math.max(top, at.applyAsDouble(d, base));
        }
        top *= 1.1;
        g.fill(gx, gy, gx + gw, gy + gh, 0x40000000);
        for (int i = 0; i <= 4; i++) {
            int ly = gy + gh - gh * i / 4;
            g.fill(gx, ly, gx + gw, ly + 1, 0x18FFFFFF);
            String label = number(Math.round(top * i / 4));
            g.drawString(font, label, gx - 4 - font.width(label), ly - 4, DIM, false);
        }
        for (double mark : new double[]{startD, endD}) if (spec != null) {
            int px = gx + (int) Math.round(mark / blocks * gw);
            g.fill(px, gy, px + 1, gy + gh, 0x40FFFFFF);
        }
        for (int d : new int[]{0, (int) startD, (int) endD, blocks}) {
            String label = d + "m";
            int px = gx + (int) Math.round((double) d / blocks * gw);
            g.drawString(font, label, Math.min(gx + gw - font.width(label), Math.max(gx, px - font.width(label) / 2)), gy + gh + 3, DIM, false);
        }
        plot(g, gx, gy, gw, gh, blocks, top, d -> currentAt(d, base), 0xFF7A8290);
        if (at != null) { var f = at; plot(g, gx, gy, gw, gh, blocks, top, d -> f.applyAsDouble(Math.floor(d), base), pendingFalloff != null ? READY : ACCENT); }
        else g.drawString(font, font.plainSubstrByWidth("! " + error, gw - 8), gx + 4, gy + 4, 0xFFFF6666, false);
        g.drawString(font, "— 현재", gx + gw - 92, gy + 4, 0xFF7A8290, false);
        g.drawString(font, "— 미리보기", gx + gw - 52, gy + 4, pendingFalloff != null ? READY : ACCENT, false);
        graphRect = new int[]{gx, gy, gw, gh}; graphBlocks = blocks;
    }
    private void plot(GuiGraphics g, int gx, int gy, int gw, int gh, int blocks, double top, java.util.function.DoubleUnaryOperator f, int color) {
        int previous = -1;
        for (int px = 0; px < gw; px++) {
            double value = f.applyAsDouble((double) px / gw * blocks);
            int py = gy + gh - 1 - (int) Math.round(Math.max(0, Math.min(1, value / top)) * (gh - 1));
            int a = previous < 0 ? py : Math.min(previous, py), b = previous < 0 ? py : Math.max(previous, py);
            g.fill(gx + px, a, gx + px + 1, b + 1, color);
            previous = py;
        }
    }
    /** Hover readout: the damage after falloff at the whole block under the cursor. */
    private void graphTooltip(GuiGraphics g, int mx, int my) {
        if (graphRect == null) return;
        int gx = graphRect[0], gy = graphRect[1], gw = graphRect[2], gh = graphRect[3];
        if (mx < gx || mx >= gx + gw || my < gy || my >= gy + gh || my < TOP || my > viewBottom()) return;
        int d = (int) Math.floor((double) (mx - gx) / gw * graphBlocks);
        double base = base();
        int px = gx + (int) Math.round((d + .5) / graphBlocks * gw);
        g.fill(px, Math.max(gy, TOP), px + 1, Math.min(gy + gh, viewBottom()), 0x80FFFFFF);
        String now = "현재 " + round(currentAt(d, base));
        String line;
        try {
            double value = spec().damageAt(d, base);
            line = d + "블록 · 적용 후 " + round(value) + (base > 0 ? " (" + Math.round(value / base * 100) + "%)" : "");
        } catch (IllegalArgumentException invalid) { line = d + "블록"; }
        int tw = Math.max(font.width(line), font.width(now)) + 8, tx = Math.min(mx + 10, width - tw - 4), ty = Math.max(4, my - 28);
        g.pose().pushPose(); g.pose().translate(0, 0, 400);
        g.fill(tx, ty, tx + tw, ty + 24, 0xF0101418);
        g.fill(tx, ty, tx + tw, ty + 1, ACCENT);
        g.drawString(font, line, tx + 4, ty + 4, INK, false);
        g.drawString(font, now, tx + 4, ty + 14, MUTED, false);
        g.pose().popPose();
    }

    /** "weapon" or "secondary"; views from an older server are primaries. */
    private static String kind(String kind) { return WeaponPatch.SECONDARY.equals(kind) || WeaponPatch.GENERAL.equals(kind) ? kind : WeaponPatch.PRIMARY; }
    private static String kind(WeaponPatch.View view) { return kind(view.kind()); }
    private static String kind(WeaponPatch.Entry entry) { return kind(entry.kind()); }
    /** Secondaries and the 공통 tab save in the lobby only; primaries patch live. */
    private boolean secondary() { return !kind(view).equals(WeaponPatch.PRIMARY); }
    /** The current tab's weapons. */
    private List<WeaponPatch.Entry> listed() { return view.weapons().stream().filter(w -> kind(w).equals(kind(view))).toList(); }
    private WeaponPatch.Entry selected() {
        return listed().stream().filter(w -> w.id().equals(view.id())).findFirst().orElse(null);
    }
    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    private static String kg(double weight) { return number(weight) + "kg"; }
    /** Typed value of a row when it parses, else its saved value; the fallback when the row does not exist. */
    private double typed(String key, double fallback) {
        for (var row : view.rows()) if (row.key().equals(key)) {
            try { return Double.parseDouble(current(row).strip()); }
            catch (NumberFormatException e) { try { return Double.parseDouble(row.value()); } catch (NumberFormatException again) { return fallback; } }
        }
        return fallback;
    }
    /** "TaCZ 무게 30kg (이동속도 -45%) · GunGang 80% → 최종 약 44%", following what is typed. */
    private String weightLine(WeaponPatch.Entry entry) {
        if (entry.weight() == null) return "TaCZ 무게: 확인 불가 (TaCZ 총기가 아니거나 데이터 파일 없음)";
        double weight = typed("tacz:weight", entry.weight()), speed = typed("gg:movementSpeed", entry.movementSpeed());
        double loss = weight * view.weightMultiplier();
        String tacz = "TaCZ 무게 " + kg(weight) + (loss == 0 ? " (감속 없음)" : " (이동속도 " + (loss > 0 ? "-" : "+") + number(Math.abs(loss) * 100) + "%)");
        return tacz + " · GunGang " + number(speed) + "% → 최종 약 " + Math.round(Math.max(0, 1 - loss) * speed) + "%";
    }
    private String current(WeaponPatch.Row row) { return edits.getOrDefault(row.key(), row.value()); }
    private boolean changed(WeaponPatch.Row row) { var v = edits.get(row.key()); return v != null && !v.isBlank() && !v.strip().equals(row.value()); }
    private Map<String, String> changes() {
        var result = new LinkedHashMap<String, String>();
        for (var row : view.rows()) if (changed(row)) result.put(row.key(), edits.get(row.key()).strip());
        if (pendingFalloff != null && hasCurve()) result.put(WeaponPatch.FALLOFF, pendingFalloff);
        return result;
    }
    private void request(String action, String id, Map<String, String> changes) { request(action, kind(view), id, changes); }
    private void request(String action, String kind, String id, Map<String, String> changes) {
        if (!LobbyClient.connected()) { status("서버 연결이 끊겨 요청을 보낼 수 없습니다."); return; }
        waiting = true;
        GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.WeaponPatchRequest(action, ServerFiles.GSON.toJson(new WeaponPatch.Request(id, changes, kind))));
    }
    private void status(String error) {
        view = new WeaponPatch.View(view.weapons(), view.id(), view.name(), view.gunId(), view.file(), view.rows(), "", error, view.phase(), view.weightMultiplier(), view.kind(), view.falloff());
    }

    // ---- Layout ----------------------------------------------------------------------------------------------

    private int bottom() { return height - 36; }
    private int viewBottom() { return bottom() - 16; }
    private int listX() { return left() + 12; }
    private int formX() { return listX() + LIST + 10; }
    private int formW() { return left() + body() - 12 - formX(); }
    private int boxX() { return formX() + formW() - BOX - 64; }
    private int maxScroll() { return Math.max(0, lines.size() * ROW - (viewBottom() - TOP)); }
    private int listTop() { return TOP + TABS; }
    private int maxListScroll() { return Math.max(0, listed().size() * 20 - (viewBottom() - listTop())); }

    private void buildLines() {
        lines.clear();
        String group = null;
        // The weight line is about the primary's movement speed; secondaries do not set it.
        if (selected() != null && !secondary()) lines.add(WEIGHT);
        for (var row : view.rows()) {
            if (!row.group().equals(group)) {
                group = row.group();
                // The falloff auto-fill sits right above the TaCZ data it writes.
                if (group.equals("tacz") && hasCurve()) {
                    lines.add("거리별 피해 감소 · 자동 채움 (1블록 단위 구간)");
                    lines.add(CURVE_TYPE); lines.add(CURVE_RANGE); lines.add(CURVE_SHAPE); lines.add(GRAPH);
                    for (int i = 1; i < GRAPH_ROWS; i++) lines.add(GRAPH_FILL);
                }
                lines.add(GROUPS.getOrDefault(group, group));
            }
            lines.add(row);
        }
    }
    @Override protected void init() {
        buildLines();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        listScroll = Math.max(0, Math.min(listScroll, maxListScroll()));
        int tabW = (LIST - 8) / 3;
        tab("주무기", WeaponPatch.PRIMARY, listX(), tabW);
        tab("보조무기", WeaponPatch.SECONDARY, listX() + tabW + 4, tabW);
        tab("전역", WeaponPatch.GENERAL, listX() + (tabW + 4) * 2, LIST - (tabW + 4) * 2);
        var weapons = listed();
        for (int i = 0; i < weapons.size(); i++) {
            int y = listTop() + i * 20 - listScroll;
            if (y < listTop() || y + 18 > viewBottom()) continue;
            var weapon = weapons.get(i);
            boolean selected = weapon.id().equals(view.id());
            listButton(Component.literal((kind(weapon).equals(WeaponPatch.GENERAL) ? weapon.name() : weapon.id() + " · " + weapon.name())), listX(), y, LIST, 18, !waiting, selected ? ACCENT : MUTED, 9, 40, () -> {
                if (weapon.id().equals(view.id())) return;
                if (!changes().isEmpty()) { status("적용하지 않은 변경이 있습니다. 적용하거나 변경 취소 후 다른 무기를 선택하세요."); rebuildWidgets(); return; }
                request("open", weapon.id(), Map.of());
            });
        }
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            int y = TOP + i * ROW - scroll;
            if (y < TOP || y + 18 > viewBottom()) continue;
            if (line == CURVE_TYPE || line == CURVE_RANGE || line == CURVE_SHAPE) { curveWidgets(line, formX(), y, formW()); continue; }
            if (!(line instanceof WeaponPatch.Row row)) continue;
            var box = new TacticalEditBox(font, boxX(), y, BOX, 18, Component.literal(row.label()));
            box.setMaxLength(row.numeric() ? 16 : row.key().endsWith("description") || row.key().endsWith("usage") ? 1000 : 200);
            if (row.numeric()) box.setFilter(s -> s.matches("-?[0-9]*\\.?[0-9]*"));
            box.setValue(current(row));
            box.setHint(Component.literal(row.value()).withStyle(ChatFormatting.DARK_GRAY));
            box.setResponder(s -> edits.put(row.key(), s));
            addRenderableWidget(box);
        }
        int bottom = bottom(), x = listX(), w = left() + body() - 12 - x, third = (w - 8) / 3;
        button("변경 취소", x, bottom, third, 20, !waiting, MUTED, () -> { edits.clear(); pendingFalloff = null; curveNote = ""; status(""); rebuildWidgets(); });
        button(secondary() ? "적용 (로비)" : "실시간 적용", x + third + 4, bottom, third, 20, !waiting, ACCENT, () -> {
            var changes = changes();
            if (changes.isEmpty()) { status("변경한 항목이 없습니다."); rebuildWidgets(); return; }
            request("apply", view.id(), changes); rebuildWidgets();
        });
        closeButton(x + (third + 4) * 2, bottom, w - (third + 4) * 2, 20);
    }
    /** Switches the list to primaries or secondaries (opening that tab's first weapon), unless edits are pending. */
    private void tab(String label, String kind, int x, int w) {
        boolean active = kind(view).equals(kind);
        button(label, x, TOP, w, 18, !waiting, active ? ACCENT : MUTED, () -> {
            if (active) return;
            if (!changes().isEmpty()) { status("적용하지 않은 변경이 있습니다. 적용하거나 변경 취소 후 전환하세요."); rebuildWidgets(); return; }
            listScroll = 0;
            request("open", kind, "", Map.of());
        });
    }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < formX()) {
            int next = Math.max(0, Math.min(maxListScroll(), listScroll - (int) Math.signum(delta) * 20));
            if (next != listScroll) { listScroll = next; rebuildWidgets(); }
        } else {
            int next = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * ROW));
            if (next != scroll) { scroll = next; rebuildWidgets(); }
        }
        return true;
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        String gun = kind(view).equals(WeaponPatch.GENERAL) ? "전역 설정" : view.gunId() == null || view.gunId().isEmpty() ? "TaCZ 총기 아님" : view.gunId();
        String file = view.file() == null || view.file().isEmpty() ? "" : " · " + view.file();
        shell(g, view.name() + " · " + gun + file + (view.phase().equals("LOBBY") ? "" : " · 경기 중 패치"));
        int x = formX(), w = formW();
        g.enableScissor(x - 2, TOP - 2, x + w + 2, viewBottom());
        graphRect = null;
        for (int i = 0; i < lines.size(); i++) {
            int y = TOP + i * ROW - scroll;
            // The graph spans several rows, so it is drawn while any of them is visible.
            if (lines.get(i) == GRAPH && y + GRAPH_ROWS * ROW >= TOP && y <= viewBottom()) { drawGraph(g, x, y, w); continue; }
            if (y + ROW < TOP || y > viewBottom()) continue;
            if (lines.get(i) instanceof String header) {
                int color = header.startsWith("TaCZ") ? 0xFF66B3FF : ACCENT;
                g.fill(x, y + 3, x + 2, y + 15, color);
                g.drawString(font, header, x + 7, y + 5, color, false);
                g.fill(x + 12 + font.width(header), y + 9, x + w, y + 10, TacticalStyle.HAIRLINE);
                continue;
            }
            if (lines.get(i) == GRAPH) { drawGraph(g, x, y, w); continue; }
            if (lines.get(i) == GRAPH_FILL) continue;
            if (lines.get(i) == CURVE_TYPE || lines.get(i) == CURVE_RANGE || lines.get(i) == CURVE_SHAPE) { curveLabels(g, lines.get(i), x, y, w); continue; }
            if (lines.get(i) == WEIGHT) {
                var entry = selected();
                boolean heavy = entry.weight() != null && typed("tacz:weight", entry.weight()) != 0;
                g.drawString(font, font.plainSubstrByWidth(weightLine(entry), w - 7), x + 7, y + 5, heavy ? 0xFFFFAA44 : MUTED, false);
                continue;
            }
            var row = (WeaponPatch.Row) lines.get(i);
            boolean changed = changed(row);
            g.drawString(font, font.plainSubstrByWidth((changed ? "* " : "") + row.label(), boxX() - x - 12), x + 7, y + 5, changed ? ACCENT : INK, false);
            String note = changed ? "기존 " + row.value() : "";
            g.drawString(font, font.plainSubstrByWidth(note, x + w - boxX() - BOX - 6), boxX() + BOX + 6, y + 5, MUTED, false);
        }
        g.disableScissor();
        if (view.rows().isEmpty()) g.drawString(font, "수정할 수 있는 항목이 없습니다.", x + 7, TOP + 5, MUTED, false);
        if (maxScroll() > 0) {
            int track = viewBottom() - TOP, content = lines.size() * ROW, thumb = Math.max(16, track * track / content), pos = TOP + (track - thumb) * scroll / maxScroll();
            g.fill(x + w + 3, TOP, x + w + 5, viewBottom(), 0x30FFFFFF); g.fill(x + w + 3, pos, x + w + 5, pos + thumb, ACCENT);
        }
        g.fill(formX() - 6, TOP, formX() - 5, viewBottom(), TacticalStyle.HAIRLINE);
        int statusY = viewBottom() + 4, statusW = left() + body() - 12 - listX();
        int count = changes().size();
        if (view.error() != null && !view.error().isEmpty()) g.drawString(font, font.plainSubstrByWidth(view.error(), statusW), listX(), statusY, 0xFFFF6666, false);
        else if (waiting) g.drawString(font, "서버에 적용하는 중...", listX(), statusY, MUTED, false);
        else if (count > 0) g.drawString(font, secondary() ? "변경 " + count + "개 · 로비에서 적용하면 저장되고 다음 지급부터 반영됩니다." : "변경 " + count + "개 · 실시간 적용을 누르면 저장 후 바로 반영됩니다.", listX(), statusY, ACCENT, false);
        else if (view.message() != null && !view.message().isEmpty()) g.drawString(font, font.plainSubstrByWidth(view.message(), statusW), listX(), statusY, READY, false);
        else g.drawString(font, "휠로 스크롤 · 빈칸/회색 글씨는 현재값", listX(), statusY, DIM, false);
        super.render(g, mx, my, partial);
        graphTooltip(g, mx, my);
        // Each weapon's TaCZ weight beside its name; a non-zero weight slows the player on top of GunGang's speed.
        g.enableScissor(listX(), listTop(), listX() + LIST, viewBottom());
        var weapons = listed();
        for (int i = 0; i < weapons.size(); i++) {
            int y = listTop() + i * 20 - listScroll;
            if (y < listTop() || y + 18 > viewBottom()) continue;
            var weight = weapons.get(i).weight();
            String text = weight == null ? "-" : kg(weight);
            int color = weight == null ? DIM : weight != 0 ? 0xFFFFAA44 : MUTED;
            g.drawString(font, text, listX() + LIST - 6 - font.width(text), y + 5, color, false);
        }
        g.disableScissor();
    }
}
