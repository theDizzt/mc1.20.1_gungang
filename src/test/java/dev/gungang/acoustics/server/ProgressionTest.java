package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionTest {
    @TempDir Path directory;
    private static MatchSnapshot.Row row(String id,String team,int kills,int deaths,double damage,int seconds) {
        return new MatchSnapshot.Row(id,"player"+id.substring(0,4),team,kills,deaths,0,damage,seconds);
    }
    @Test void levelBoundariesAndCap() {
        var table=XpTable.defaults();assertEquals(1,table.progress(0).level());assertEquals(1,table.progress(80).level());
        assertEquals(2,table.progress(81).level());assertEquals(0,table.progress(81).current());
        assertEquals(120,table.progress(81).required());assertEquals(3,table.progress(201).level());
        long cap=267909;
        assertEquals(90,table.progress(cap-1).level());assertEquals(91,table.progress(cap).level());
        assertEquals(1f,table.progress(cap+1000).fraction());
        for(int i=1;i<91;i++) {
            long threshold=table.cumulativeXp().get(i);
            assertEquals(i,table.progress(threshold-1).level());
            assertEquals(i+1,table.progress(threshold).level());
        }
        assertEquals(.5f,table.progress(141).fraction());
    }
    @Test void rejectsMalformedRequirementsAndRewards() {
        assertThrows(IllegalArgumentException.class,()->new XpTable(List.of(100L)));
        assertThrows(IllegalArgumentException.class,()->new XpTable(Collections.nCopies(91,0L)));
        assertThrows(IllegalArgumentException.class,()->new ExperienceRules(-1,null,null,null,null,null,null));
        assertThrows(IllegalArgumentException.class,()->new ExperienceRules(null,null,null,null,null,null,0d));
    }
    @Test void rewardRangesAndTeamShare() {
        var rules=ExperienceRules.defaults();
        assertEquals(32,rules.reward(true,0,0));assertEquals(56,rules.reward(true,1,1));
        assertEquals(16,rules.reward(false,0,0));assertEquals(32,rules.reward(false,1,1));
        assertEquals(44,rules.reward(true,1,2));assertEquals(24,rules.reward(false,1,2));
        assertEquals(56,rules.reward(true,10,1));
    }
    @Test void matchPersistsTotalsAndXpAcrossRestart() throws Exception {
        Path file=directory.resolve("player-db.json");var db=new PlayerDatabase(file);
        String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
        var rows=List.of(row(a,"ALPHA",4,2,1234.5,600),row(b,"BRAVO",2,4,500,590));
        var rewards=db.match(rows,"ALPHA",ExperienceRules.defaults());assertEquals(56,rewards.get(a));assertEquals(32,rewards.get(b));
        db=new PlayerDatabase(file);var saved=db.get(a,"renamed");
        assertEquals(1,saved.matches());assertEquals(4,saved.kills());assertEquals(2,saved.deaths());
        assertEquals(1234.5,saved.damage());assertEquals(600,saved.playSeconds());assertEquals(56,saved.experience());
        db.match(rows,"ALPHA",ExperienceRules.defaults());assertEquals(2,db.get(a,"a").matches());
        assertTrue(Files.exists(directory.resolve("player-db.json.bak")));
    }
    @Test void legacyRecordsGainPresetFieldsWithoutChangingStats() throws Exception {
        Path file=directory.resolve("legacy.json");String id=UUID.randomUUID().toString();
        Files.writeString(file,"{\"version\":1,\"players\":[{\"id\":\""+id+"\",\"name\":\"old\",\"experience\":300,\"matches\":4,\"kills\":9,\"deaths\":3,\"damage\":120.5,\"playSeconds\":900,\"wins\":2}]}");
        var db=new PlayerDatabase(file);var row=db.get(id,"old");
        assertEquals(new PlayerRecord(id,"old",300,4,9,3,120.5,900,2),row);assertTrue(row.presets().isEmpty());
        String text=Files.readString(file);assertTrue(text.contains("\"presets\"")&&text.contains("\"defaultPreset\""));
        assertTrue(Files.readString(directory.resolve("legacy.json.bak")).contains("\"experience\":300"));
    }
    @Test void presetsPersistRenameDeleteAndDefault() throws Exception {
        Path file=directory.resolve("presets.json");var db=new PlayerDatabase(file);String id=UUID.randomUUID().toString();
        var r=PlayerRecord.empty(id,"p").withPreset(new PlayerRecord.Preset(2,"","ak","landmine")).withPreset(new PlayerRecord.Preset(1,"Rush","smg","shield")).withDefault(2);
        db.update(r);var saved=new PlayerDatabase(file).get(id,"p");
        assertEquals(List.of(1,2),saved.presets().stream().map(PlayerRecord.Preset::slot).toList());
        assertEquals("프리셋 2",saved.preset(2).name());assertEquals("ak",saved.defaultLoadout().primary());
        saved=saved.withPreset(new PlayerRecord.Preset(2,"Long",saved.preset(2).primary(),saved.preset(2).secondary()));
        assertEquals("Long",saved.defaultLoadout().name());
        saved=saved.withoutPreset(2);assertNull(saved.defaultLoadout());assertEquals(0,saved.defaultPreset());
        assertThrows(IllegalArgumentException.class,()->new PlayerRecord.Preset(6,"x","ak","shield"));
        assertThrows(IllegalArgumentException.class,()->new PlayerRecord.Preset(1,"x".repeat(21),"ak","shield"));
        assertThrows(IllegalArgumentException.class,()->new PlayerRecord.Preset(1,"x","AK 47","shield"));
    }
    @Test void abortHasNoXpSpectatorsAndNonParticipantsAreExcluded() throws Exception {
        var db=new PlayerDatabase(directory.resolve("db.json"));String a=UUID.randomUUID().toString(),s=UUID.randomUUID().toString(),empty=UUID.randomUUID().toString();
        db.match(List.of(row(a,"ALPHA",1,1,100,50),row(s,"SPECTATOR",0,0,0,50),row(empty,"BRAVO",0,0,0,0)),"ABORTED",ExperienceRules.defaults());
        assertEquals(1,db.all().size());assertEquals(0,db.get(a,"a").experience());assertEquals(1,db.get(a,"a").matches());
    }
    @Test void administratorEditsPersistAndCannotGoNegative() throws Exception {
        Path file=directory.resolve("db.json");var db=new PlayerDatabase(file);String id=UUID.randomUUID().toString();
        var r=PlayerRecord.empty(id,"test").set("xp","250").set("kills","12").set("damage","12.5").set("playSeconds","100");
        db.update(r);assertEquals(r,new PlayerDatabase(file).get(id,"test"));
        assertThrows(IllegalArgumentException.class,()->r.set("xp","-1"));
        assertThrows(IllegalArgumentException.class,()->r.set("damage","NaN"));
    }
    @Test void corruptDatabaseIsNotOverwritten() throws Exception {
        Path file=directory.resolve("db.json");Files.writeString(file,"not json");
        assertThrows(Exception.class,()->new PlayerDatabase(file));assertEquals("not json",Files.readString(file));
    }
    @Test void oldOptionsDefaultNewSettings() {
        var options=ServerFiles.GSON.fromJson("{\"respawnSeconds\":5}",TeamOptions.class);
        assertEquals(ExperienceRules.defaults(),options.experience());assertEquals(DeathmatchRules.defaults(),options.deathmatch());
    }
    @Test void winsCountOnlyWinningTeamAndPersist() throws Exception {
        Path file=directory.resolve("wins.json");var db=new PlayerDatabase(file);
        String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
        var rows=List.of(row(a,"ALPHA",1,1,10,60),row(b,"BRAVO",1,1,10,60));
        for(String result:List.of("ALPHA","DRAW","ABORTED","BRAVO","ALPHA"))db.match(rows,result,ExperienceRules.defaults());
        db=new PlayerDatabase(file);
        assertEquals(2,db.get(a,"a").wins());assertEquals(1,db.get(b,"b").wins());
        assertEquals(5,db.get(a,"a").matches());
        db.update(db.get(a,"a").set("wins","10"));
        assertEquals(10,new PlayerDatabase(file).get(a,"a").wins());
        assertThrows(IllegalArgumentException.class,()->PlayerRecord.empty(a,"a").set("wins","-1"));
    }
    @Test void legacyDatabaseDefaultsWinsToZeroWithoutLosingStats() throws Exception {
        String id=UUID.randomUUID().toString();Path file=directory.resolve("legacy.json");
        Files.writeString(file,"{\"version\":1,\"players\":[{\"id\":\""+id+"\",\"name\":\"old\",\"experience\":201,\"matches\":8,\"kills\":12,\"deaths\":4,\"damage\":123.5,\"playSeconds\":90}]}");
        var db=new PlayerDatabase(file);var r=db.get(id,"old");
        assertEquals(0,r.wins());assertEquals(201,r.experience());assertEquals(8,r.matches());assertEquals(12,r.kills());
        db.update(r.named("renamed"));assertEquals(0,new PlayerDatabase(file).get(id,"renamed").wins());
    }
}
