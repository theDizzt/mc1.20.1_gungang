package dev.gungang.acoustics.server;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BotDatabaseTest {
    @TempDir Path directory;
    @Test void identityAndCareerSurviveRestartAndRename()throws Exception{
        Path file=directory.resolve("bot-db.json");
        var db=new BotDatabase(file);
        db.update(db.profile().named("연습봇").skinned("gungang:textures/bot/training.png"));
        var row=new MatchSnapshot.Row(BotManager.ID.toString(),db.profile().displayName(),"ALPHA",3,2,1,125.5,45);
        var human=new MatchSnapshot.Row("e82b199c-3dad-47d1-9af5-d9c496964df8","Human","BRAVO",2,3,0,50,45);
        db.finish(new MatchSnapshot(false,true,"test","RESULT",0,false,"ALPHA",3,2,List.of(row,human)));
        db=new BotDatabase(file);
        assertEquals("연습봇",db.profile().name());
        assertEquals("연습봇",db.profile().displayName());assertEquals("연습봇",db.profile().statistics().name());
        assertEquals("gungang:textures/bot/training.png",db.profile().skin());
        var stats=db.profile().statistics();
        assertEquals(1,stats.matches());assertEquals(1,stats.wins());assertEquals(3,stats.kills());
        assertEquals(2,stats.deaths());assertEquals(125.5,stats.damage());assertEquals(45,stats.playSeconds());
        // Won, and the only ALPHA contributor: full win XP under default rules (32 base + 24 share).
        assertEquals(56,stats.experience());
        db.update(db.profile().named("다른이름"));
        assertEquals(3,new BotDatabase(file).profile().statistics().kills());
        assertTrue(Files.exists(directory.resolve("bot-db.json.bak")));
    }
    @Test void malformedProfileDoesNotOverwriteSavedFile()throws Exception{
        Path file=directory.resolve("bot-db.json");
        var db=new BotDatabase(file);String original=Files.readString(file);
        assertThrows(IllegalArgumentException.class,()->db.update(db.profile().named("§cFake")));
        assertThrows(IllegalArgumentException.class,()->db.update(db.profile().skinned("https://example.com/skin.png")));
        assertThrows(IllegalArgumentException.class,()->db.update(db.profile().skinned("gungang:textures/../skin.png")));
        assertEquals(original,Files.readString(file));
        Files.writeString(file,"{}");
        assertThrows(Exception.class,()->new BotDatabase(file));
        assertEquals("{}",Files.readString(file));
    }
    @Test void singleBotFileBecomesSlotOneAndEachBotKeepsItsOwnCareer()throws Exception{
        Path file=directory.resolve("bot-db.json");
        // The pre-multi-bot format: one profile object.
        Files.writeString(file,ServerFiles.GSON.toJson(BotDatabase.defaults().named("Legacy")));
        var db=new BotDatabase(file);
        assertEquals("Legacy",db.profile(0).name());assertEquals("Rookie 2",db.profile(1).name());
        db.update(db.profile(2).named("Third").skinned("file:third.png"));
        var second=new MatchSnapshot.Row(BotDatabase.id(1).toString(),"Rookie 2","BRAVO",4,1,0,80,30);
        db.finish(new MatchSnapshot(false,true,"test","RESULT",0,false,"BRAVO",0,4,List.of(second)));
        db=new BotDatabase(file);
        assertEquals("Legacy",db.profile(0).name());assertEquals(0,db.profile(0).statistics().kills());
        assertEquals(4,db.profile(1).statistics().kills());assertEquals(1,db.profile(1).statistics().wins());
        assertEquals("Third",db.profile(2).name());assertEquals("file:third.png",db.profile(2).skin());
        assertEquals(BotManager.MAX,BotDatabase.MAX);assertEquals(-1,BotDatabase.slot(java.util.UUID.randomUUID()));
    }
    @Test void documentedExamplesLoad()throws Exception{
        Path examples=Path.of("docs/server-examples/config/gungang");
        Path copy=directory.resolve("bot-db.json");Files.copy(examples.resolve("bot-db.json"),copy);
        var db=new BotDatabase(copy);
        assertEquals("Rookie",db.profile(0).displayName());assertEquals("Rookie 4",db.profile(3).name());
        var settings=ServerFiles.GSON.fromJson(Files.readString(examples.resolve("server/bot.json")),BotSettings.class);
        assertEquals(BotSettings.defaults(),settings);
    }
    @Test void absentOrUnusedBotDoesNotGainMatch()throws Exception{
        var db=new BotDatabase(directory.resolve("bot-db.json"));
        var row=new MatchSnapshot.Row(BotManager.ID.toString(),"Rookie","ALPHA",0,0,0,0,0);
        db.finish(new MatchSnapshot(false,true,"test","RESULT",0,false,"BRAVO",0,1,List.of(row)));
        assertEquals(0,db.profile().statistics().matches());
    }
}
