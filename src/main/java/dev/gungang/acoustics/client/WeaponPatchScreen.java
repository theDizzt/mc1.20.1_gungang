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
    private static final Map<String, String> GROUPS = Map.of(
            "gear", "GunGang 장비 · 장착 중인 플레이어에게 즉시 적용",
            "secondary", "GunGang 보조무기 · 로비에서 적용, 다음 지급부터",
            "display", "무기 선택 화면 표시 정보",
            "item", "아이템 효과 · 실제 성능 (item 설정, 쿨타임은 이 값이 우선)",
            "tacz", "TaCZ 총기 데이터 · 실제 성능 (총기팩 파일)");
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

    private WeaponPatchScreen(WeaponPatch.View view) { super("무기 실시간 패치"); this.view = view; }

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
        if (switched || next.error() == null || next.error().isEmpty()) edits.clear();
        if (switched) scroll = 0;
        view = next; waiting = false; rebuildWidgets();
    }

    /** "weapon" or "secondary"; views from an older server are primaries. */
    private static String kind(WeaponPatch.View view) { return WeaponPatch.SECONDARY.equals(view.kind()) ? WeaponPatch.SECONDARY : WeaponPatch.PRIMARY; }
    private static String kind(WeaponPatch.Entry entry) { return WeaponPatch.SECONDARY.equals(entry.kind()) ? WeaponPatch.SECONDARY : WeaponPatch.PRIMARY; }
    private boolean secondary() { return kind(view).equals(WeaponPatch.SECONDARY); }
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
        return result;
    }
    private void request(String action, String id, Map<String, String> changes) { request(action, kind(view), id, changes); }
    private void request(String action, String kind, String id, Map<String, String> changes) {
        if (!LobbyClient.connected()) { status("서버 연결이 끊겨 요청을 보낼 수 없습니다."); return; }
        waiting = true;
        GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.WeaponPatchRequest(action, ServerFiles.GSON.toJson(new WeaponPatch.Request(id, changes, kind))));
    }
    private void status(String error) {
        view = new WeaponPatch.View(view.weapons(), view.id(), view.name(), view.gunId(), view.file(), view.rows(), "", error, view.phase(), view.weightMultiplier(), view.kind());
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
            if (!row.group().equals(group)) { group = row.group(); lines.add(GROUPS.getOrDefault(group, group)); }
            lines.add(row);
        }
    }
    @Override protected void init() {
        buildLines();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        listScroll = Math.max(0, Math.min(listScroll, maxListScroll()));
        int half = (LIST - 4) / 2;
        tab("주무기", WeaponPatch.PRIMARY, listX(), half);
        tab("보조무기", WeaponPatch.SECONDARY, listX() + half + 4, LIST - half - 4);
        var weapons = listed();
        for (int i = 0; i < weapons.size(); i++) {
            int y = listTop() + i * 20 - listScroll;
            if (y < listTop() || y + 18 > viewBottom()) continue;
            var weapon = weapons.get(i);
            boolean selected = weapon.id().equals(view.id());
            listButton(Component.literal(weapon.id() + " · " + weapon.name()), listX(), y, LIST, 18, !waiting, selected ? ACCENT : MUTED, 9, 40, () -> {
                if (weapon.id().equals(view.id())) return;
                if (!changes().isEmpty()) { status("적용하지 않은 변경이 있습니다. 적용하거나 변경 취소 후 다른 무기를 선택하세요."); rebuildWidgets(); return; }
                request("open", weapon.id(), Map.of());
            });
        }
        for (int i = 0; i < lines.size(); i++) {
            if (!(lines.get(i) instanceof WeaponPatch.Row row)) continue;
            int y = TOP + i * ROW - scroll;
            if (y < TOP || y + 18 > viewBottom()) continue;
            var box = new TacticalEditBox(font, boxX(), y, BOX, 18, Component.literal(row.label()));
            box.setMaxLength(row.numeric() ? 16 : 80);
            if (row.numeric()) box.setFilter(s -> s.matches("-?[0-9]*\\.?[0-9]*"));
            box.setValue(current(row));
            box.setHint(Component.literal(row.value()).withStyle(ChatFormatting.DARK_GRAY));
            box.setResponder(s -> edits.put(row.key(), s));
            addRenderableWidget(box);
        }
        int bottom = bottom(), x = listX(), w = left() + body() - 12 - x, third = (w - 8) / 3;
        button("변경 취소", x, bottom, third, 20, !waiting, MUTED, () -> { edits.clear(); status(""); rebuildWidgets(); });
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
        String gun = view.gunId() == null || view.gunId().isEmpty() ? "TaCZ 총기 아님" : view.gunId();
        String file = view.file() == null || view.file().isEmpty() ? "" : " · " + view.file();
        shell(g, view.name() + " · " + gun + file + (view.phase().equals("LOBBY") ? "" : " · 경기 중 패치"));
        int x = formX(), w = formW();
        g.enableScissor(x - 2, TOP - 2, x + w + 2, viewBottom());
        for (int i = 0; i < lines.size(); i++) {
            int y = TOP + i * ROW - scroll;
            if (y + ROW < TOP || y > viewBottom()) continue;
            if (lines.get(i) instanceof String header) {
                int color = header.startsWith("TaCZ") ? 0xFF66B3FF : ACCENT;
                g.fill(x, y + 3, x + 2, y + 15, color);
                g.drawString(font, header, x + 7, y + 5, color, false);
                g.fill(x + 12 + font.width(header), y + 9, x + w, y + 10, TacticalStyle.HAIRLINE);
                continue;
            }
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
