package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class LoadoutScreen extends ArenaScreen {
    private EquipmentSnapshot state;
    private boolean primary = true;
    private int page, scroll;
    private String selected;
    LoadoutScreen(EquipmentSnapshot state) { super("GUNGANG / LOADOUT"); this.state = state; selected = state.primary(); }
    void update(EquipmentSnapshot next) { state = next; rebuildWidgets(); }
    private List<Loadouts.Weapon> weapons() { return primary ? state.catalog().primary() : state.catalog().secondary(); }
    private int capacity() { return Math.max(1, (height - 140) / 27); }
    private Loadouts.Weapon selected() { return weapons().stream().filter(w -> w.id().equals(selected)).findFirst().orElse(weapons().get(0)); }
    @Override protected void init() {
        int x = left() + 12, w = body() - 24, listWidth = Math.max(90, w / 3);
        button("주무기 · 핫바 1", x, 57, (w - 8) / 2, 22, true, primary ? ACCENT : MUTED, () -> { primary = true; selected = state.primary(); page = scroll = 0; rebuildWidgets(); });
        button("보조무기 · 왼손", x + (w + 8) / 2, 57, (w - 8) / 2, 22, true, !primary ? ACCENT : MUTED, () -> { primary = false; selected = state.secondary(); page = scroll = 0; rebuildWidgets(); });
        page = Math.min(page, Math.max(0, (weapons().size() - 1) / capacity()));
        for (int i = page * capacity(); i < Math.min(weapons().size(), (page + 1) * capacity()); i++) {
            var weapon = weapons().get(i);
            String equipped = primary ? state.primary() : state.secondary();
            button((equipped.equals(weapon.id()) ? "● " : "") + "[" + weapon.category() + "] " + weapon.name(), x, 85 + (i - page * capacity()) * 27, listWidth, 24,
                    true, weapon.id().equals(selected) ? ACCENT : MUTED, () -> { selected = weapon.id(); scroll = 0; rebuildWidgets(); });
        }
        button("‹", x, height - 48, 28, 22, page > 0, MUTED, () -> { page--; rebuildWidgets(); });
        button("›", x + 34, height - 48, 28, 22, (page + 1) * capacity() < weapons().size(), MUTED, () -> { page++; rebuildWidgets(); });
        button("선택한 무기 장착", x + listWidth + 10, height - 48, Math.max(40,w - listWidth - 82), 22, state.allowed(), ACCENT,
                () -> LobbyClient.action(primary ? "primary" : "secondary", selected().id(), ""));
        button("닫기", x + w - 64, height - 48, 64, 22, true, MUTED, this::onClose);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        shell(g, state.team() + "  /  " + (state.preparationSeconds() > 0 ? "출격까지 " + state.preparationSeconds() + "초" : state.spawnHint() ? "팀 스폰 구역" : "자유 무기 선택") + "  /  " + LobbyClient.LOADOUT.getTranslatedKeyMessage().getString() + "로 다시 열기");
        int x = left() + 12, w = body() - 24, listWidth = Math.max(90, w / 3), detailX = x + listWidth + 10, detailWidth = Math.max(20,w - listWidth - 20);
        g.fill(detailX, 85, x + w, height - 57, 0xFF101A28);
        var weapon = selected();
        List<String> text = new ArrayList<>(); text.add(weapon.name()); text.add("카테고리 · " + weapon.category()); text.add(""); text.add(weapon.description());
        if (primary && weapon.specs() != null) {
            var specs = weapon.specs(); text.add(""); text.add("피해량   " + specs.damage()); text.add("명중률   " + specs.accuracy());
            text.add("안정성   " + specs.stability()); text.add("사거리   " + specs.range()); text.add("연사력   " + specs.fireRate());
        }
        text.add(""); text.add("사용방법"); text.add(weapon.usage());
        var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (String paragraph : text) for (String line : WeaponText.lines(paragraph)) {
            if (line.isEmpty()) lines.add(Component.empty().getVisualOrderText());
            else lines.addAll(font.split(Component.literal(line), Math.max(1,detailWidth - 12)));
        }
        int visible = Math.max(1,(height - 157) / 12); scroll = Math.min(scroll,Math.max(0,lines.size() - visible));
        g.enableScissor(detailX,85,x + w,height - 57);
        for (int i = scroll; i < Math.min(lines.size(),scroll + visible); i++) g.drawString(font,lines.get(i),detailX + 8,94 + (i - scroll) * 12,i == 0 ? INK : MUTED,false);
        g.disableScissor();
        super.render(g,mouseX,mouseY,partial);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) { scroll = Math.max(0,scroll - (int)Math.signum(delta) * 3); return true; }
}
