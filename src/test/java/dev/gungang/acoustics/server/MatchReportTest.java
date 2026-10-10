package dev.gungang.acoustics.server;

import java.nio.file.*;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MatchReportTest {
    @TempDir Path directory;
    @Test void savesDateMapStatisticsAndDoesNotOverwriteSameSecond() throws Exception {
        var start=ZonedDateTime.parse("2026-10-10T14:30:00+09:00[Asia/Seoul]");
        var row=new MatchSnapshot.Row("test-id","플레이어","ALPHA",3,2,1,129.5,1164);
        var result=new MatchSnapshot(false,true,"Example Arena","RUNNING",1164,true,"ALPHA",100,20,List.of(row));
        var a=MatchReport.save(directory,ArenaMap.example(),result,start,start.plusSeconds(1164));
        var b=MatchReport.save(directory,ArenaMap.example(),result,start,start.plusSeconds(1164));
        assertNotEquals(a,b);assertTrue(Files.exists(a));assertTrue(Files.exists(b));
        var text=Files.readString(a);
        assertTrue(text.contains("2026-10-10T14:30+09:00[Asia/Seoul]"));
        assertTrue(text.contains("Map: Example Arena (example)"));assertTrue(text.contains("Play time: 19:24"));
        assertTrue(text.contains("플레이어 | test-id | ALPHA | 3 / 2 / 1 | 129.5 | 19:24"));
        assertEquals("00:00",MatchReport.time(0));
    }
}
