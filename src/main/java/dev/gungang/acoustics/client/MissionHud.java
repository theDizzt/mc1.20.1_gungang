package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.MatchSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** The match objective for the viewer's side, in a card at the top right; the kill log starts below it. */
final class MissionHud {
    private static final int TOP = 8, HEIGHT = 24, PAD = 7;

    /** Draws the mission card and returns the y where the kill log may start. */
    static int render(GuiGraphics g, int width) {
        String mission = mission();
        if (mission == null) return TOP;
        var mc = Minecraft.getInstance();var font = mc.font;
        String mode = LobbyScreen.modeName(MatchHud.state().point().mode());
        int labelWidth = font.width("MISSION") + 6 + font.width(mode);
        int textWidth = Math.min(Math.max(font.width(mission), labelWidth), Math.max(60, width / 2 - 2 * PAD));
        int w = textWidth + 2 * PAD, x = width - 8 - w;
        int accent = TeamHud.teamColor(mc.player) | 0xFF000000;
        ShooterHud.card(g, x, TOP, w, HEIGHT, ShooterHud.EDGE, ShooterHud.SURFACE, accent);
        g.drawString(font, "MISSION", x + PAD, TOP + 3, 0xFFFFD84A, false);
        // The game mode sits beside the label, dimmer, so the objective line stays the focus.
        g.drawString(font, mode, x + PAD + font.width("MISSION") + 6, TOP + 3, 0xFFB8BCC4, false);
        g.pose().pushPose();g.pose().translate(x + PAD, TOP + 12, 0);
        Marquee.draw(g,font,mission,0,0,textWidth,0xFFF2F2EA,true);
        g.pose().popPose();
        return TOP + HEIGHT + 6;
    }

    /** The objective line for this viewer, or null when no match is shown. */
    static String mission() {
        MatchSnapshot s = MatchHud.state();
        if (s == null || !s.active() || s.result() || s.point() == null) return null;
        var mc = Minecraft.getInstance();
        String team = team(s, mc.player == null ? "" : mc.player.getUUID().toString());
        var p = s.point();boolean alpha = team.equals("ALPHA"), bravo = team.equals("BRAVO"), playing = alpha || bravo;
        return switch (p.mode()) {
            case "DEATHMATCH" -> Lang.tr("먼저 ") + p.goal() + Lang.tr("킬을 달성하라");
            case "FREE_FOR_ALL" -> Lang.tr("모두를 쓰러뜨리고 먼저 ") + p.goal() + Lang.tr("점을 달성하라");
            case "CONTROL" -> Lang.tr("거점을 장악해 먼저 ") + p.goal() + Lang.tr("점을 획득하라");
            case "NORMANDY" -> !playing ? p.name() + Lang.tr(" 거점 공방전") : bravo ? p.name() + Lang.tr(" 거점을 점령하라") : p.name() + Lang.tr(" 거점을 끝까지 사수하라");
            case "ESCORT" -> !playing ? Lang.tr("공금 수송 작전") : alpha ? Lang.tr("공금을 안전하게 횡령하라") : Lang.tr("공금을 들고 나르는 자들을 저지하라");
            case "PROP_HUNT" -> !playing ? Lang.tr("숨바꼭질") : alpha ? Lang.tr("블록으로 변신해 끝까지 살아남아라") : Lang.tr("숨어 있는 블록을 모두 찾아내라");
            case "BOMB" -> bomb(s, team);
            default -> null;
        };
    }
    private static String bomb(MatchSnapshot s, String team) {
        var b = s.bomb();
        if (b == null) return Lang.tr("폭탄을 설치하거나 막아라");
        boolean planted = b.plantedSite() != null && !b.plantedSite().isEmpty();
        if (!team.equals("ALPHA") && !team.equals("BRAVO")) return planted ? b.plantedSite() + Lang.tr(" 지점 폭탄 설치됨") : Lang.tr("폭탄 설치 공방전");
        boolean attack = team.equals(b.attacker());
        if (planted) return attack ? b.plantedSite() + Lang.tr(" 지점의 폭탄이 터질 때까지 지켜라") : b.plantedSite() + Lang.tr(" 지점의 폭탄을 해체하라");
        return attack ? Lang.tr("A 또는 B 지점에 폭탄을 설치하라") : Lang.tr("적의 폭탄 설치를 저지하라");
    }
    private static String team(MatchSnapshot s, String id) {
        if (s.players() != null) for (var r : s.players()) if (r.id().equals(id)) return r.team();
        return "SPECTATOR";
    }
    private MissionHud() { }
}
