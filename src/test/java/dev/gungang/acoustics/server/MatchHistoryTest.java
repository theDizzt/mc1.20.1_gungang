package dev.gungang.acoustics.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MatchHistoryTest {
    @Test void parsesTeamMatch() {
        var m=MatchHistory.parse(List.of(
                "GunGang match result",
                "Date / started: 2026-11-13T23:35:02.165812100+09:00[Asia/Seoul]",
                "Map: 영국 - 왕의 길 (08)",
                "Match mode: ESCORT",
                "Play time: 07:05",
                "Winner: BRAVO",
                "Score ALPHA / BRAVO: 1 / 2",
                "",
                "Player | UUID | Team | K / D / A | Damage | Play time",
                "준나니 | 5f552aea-04fa-4473-9811-dc98c531cef4 | ALPHA | 15 / 8 / 2 | 1657.9 | 07:05",
                "돌석이네 막국수 | 9e0905e7-8131-4de6-b981-d2bac903753e | BRAVO | 17 / 8 / 8 | 2279.5 | 07:05"));
        assertEquals("2026-11-13 23:35",m.started());
        assertEquals("영국 - 왕의 길",m.map());
        assertEquals("ESCORT",m.mode());
        assertEquals("BRAVO",m.winner());
        assertEquals(1,m.alpha());assertEquals(2,m.bravo());
        assertEquals(425,m.seconds());
        assertEquals(2,m.players().size());
        var p=m.players().get(1);
        assertEquals("돌석이네 막국수",p.name());
        assertEquals(17,p.kills());assertEquals(8,p.deaths());assertEquals(8,p.assists());
        assertEquals(2279.5,p.damage(),1e-9);
    }
    @Test void uuidOnlyResults() {
        var m=MatchHistory.parse(List.of(
                "Match mode: FREE_FOR_ALL",
                "UUID | Team | K / D / A | Damage | Play time",
                "f5fec233-f2f8-43aa-949e-23f1a7f3cad7 | f5fec233-f2f8-43aa-949e-23f1a7f3cad7 | 12 / 6 / 3 | 1438.9 | 09:59",
                "",
                "개인전 순위 | UUID | 점수",
                "1 | f5fec233-f2f8-43aa-949e-23f1a7f3cad7 | 12"));
        var p=m.players().get(0);
        assertEquals("",p.name());assertEquals("f5fec233-f2f8-43aa-949e-23f1a7f3cad7",p.id());
        assertEquals(12,p.kills());assertEquals(3,p.assists());assertEquals(599,p.seconds());assertEquals(12,p.score());
    }
    @Test void oldResultsWithoutModeAndFreeForAllScores() {
        var m=MatchHistory.parse(List.of(
                "Date / started: 2026-10-25T00:11:40.1+09:00[Asia/Seoul]",
                "Map: 사찰 (busan)",
                "Winner: a",
                "Player | UUID | Team | K / D / A | Damage | Play time",
                "NIJJU__ | a | a | 12 / 6 / 3 | 1438.9 | 09:59",
                "",
                "개인전 순위 | 플레이어 | 점수",
                "1 | NIJJU__ | 12"));
        assertEquals("CUSTOM",m.mode());
        assertEquals(12,m.players().get(0).score());
    }
}
