package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.LobbySnapshot;
import net.minecraft.client.gui.GuiGraphics;

final class LobbyScreen extends ArenaScreen {
    private LobbySnapshot state;
    private boolean maps;
    private int page;
    private String target;
    LobbyScreen(LobbySnapshot state) { super("GUNGANG / MATCH LOBBY"); this.state = state; target = state.you(); }
    void update(LobbySnapshot next) { state = next; if (state.members().stream().noneMatch(m -> m.id().equals(target))) target = state.you(); rebuildWidgets(); }
    private int capacity() { return Math.max(1, (height - 186) / 27); }
    @Override protected void init() {
        int x = left() + 12, w = body() - 24, half = (w - 8) / 2;
        button("플레이어 / 팀", x, 57, half, 22, true, !maps ? ACCENT : MUTED, () -> { maps = false; page = 0; rebuildWidgets(); });
        button("맵 선택", x + half + 8, 57, half, 22, true, maps ? ACCENT : MUTED, () -> { maps = true; page = 0; rebuildWidgets(); });
        int count = maps ? state.maps().size() : state.members().size();
        page = Math.min(page, Math.max(0, (count - 1) / capacity()));
        for (int i = page * capacity(); i < Math.min(count, (page + 1) * capacity()); i++) {
            int y = 85 + (i - page * capacity()) * 27;
            if (maps) {
                var map = state.maps().get(i);
                button((state.selectedMap().equals(map.id()) ? "● " : "") + map.name() + " · " + map.seconds() + "s · " + map.gameMode(), x, y, w, 24,
                        state.administrator(), ACCENT, () -> LobbyClient.action("map", map.id(), ""));
            } else {
                var member = state.members().get(i);
                int color = member.team().equals("ALPHA") ? 0xFFEF6868 : member.team().equals("BRAVO") ? 0xFF659EF2 : MUTED;
                button((member.id().equals(target) ? "› " : "") + member.name() + "  /  " + member.team() + (member.ready() ? "  READY" : "  대기"), x, y, w, 24,
                        state.administrator() || member.id().equals(state.you()), color, () -> { target = member.id(); rebuildWidgets(); });
            }
        }
        int y = height - 90;
        button("‹", x, y, 28, 20, page > 0, MUTED, () -> { page--; rebuildWidgets(); });
        button("›", x + 34, y, 28, 20, (page + 1) * capacity() < count, MUTED, () -> { page++; rebuildWidgets(); });
        button("랜덤 팀 배치", x + 70, y, Math.max(50,w - 140), 20, state.administrator(), ACCENT, () -> LobbyClient.action("random", "", ""));
        button("닫기", x + w - 62, y, 62, 20, true, MUTED, this::onClose);
        int third = (w - 12) / 3;
        button("Team ALPHA", x, height - 64, third, 20, !maps, 0xFFEF6868, () -> LobbyClient.action("team", "ALPHA", target));
        button("Team Bravo", x + third + 6, height - 64, third, 20, !maps, 0xFF659EF2, () -> LobbyClient.action("team", "BRAVO", target));
        button("관전자", x + (third + 6) * 2, height - 64, third, 20, !maps, MUTED, () -> LobbyClient.action("team", "SPECTATOR", target));
        var self = state.members().stream().filter(m -> m.id().equals(state.you())).findFirst().orElse(null);
        button(self != null && self.ready() ? "준비 취소" : "준비 완료", x, height - 38, w, 20,
                self != null && (self.team().equals("ALPHA") || self.team().equals("BRAVO")) && !state.selectedMap().isEmpty(), ACCENT, () -> LobbyClient.action("ready", "", ""));
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        String map = state.maps().stream().filter(m -> m.id().equals(state.selectedMap())).map(LobbySnapshot.MapView::name).findFirst().orElse("활성 맵 없음 · 서버 config/gungang/server/maps의 enabled 확인");
        shell(g, map + "  /  전원 준비 → 팀 스폰 · 30초 무기 선택");
        if (maps) {
            var selected = state.maps().stream().filter(m -> m.id().equals(state.selectedMap())).findFirst().orElse(null);
            if (selected != null) g.drawString(font, font.plainSubstrByWidth(selected.description(), Math.max(1,body() - 24)), left() + 12, height - 103, MUTED, false);
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
