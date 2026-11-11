package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EffectColorsTest {
    @TempDir Path directory;
    @Test void effectColorsPersistThroughRecordUpdates() throws Exception {
        String id=UUID.randomUUID().toString();Path file=directory.resolve("colors.json");
        var db=new PlayerDatabase(file);
        var record=PlayerRecord.empty(id,"p").withEffectColor("hermes_boots",new PlayerRecord.EffectColor("hermes_boots",0xFF4D4D,0x4D9BFF))
                .withEffectColor("rapier",new PlayerRecord.EffectColor("rapier",0xFFFFFF,-1))
                .withPreset(new PlayerRecord.Preset(1,"x","ak","74")).withNickname("이름").set("xp","10");
        db.update(record);
        var saved=new PlayerDatabase(file).get(id,"p");
        assertEquals(0x4D9BFF,saved.effectColor("hermes_boots").second());
        assertEquals(-1,saved.effectColor("rapier").second());
        saved=saved.withEffectColor("rapier",null);
        assertNull(saved.effectColor("rapier"));assertNotNull(saved.effectColor("hermes_boots"));
    }
    @Test void legacyRecordsHaveNoEffectColors() throws Exception {
        Path file=directory.resolve("legacy.json");String id=UUID.randomUUID().toString();
        Files.writeString(file,"{\"version\":1,\"players\":[{\"id\":\""+id+"\",\"name\":\"old\",\"experience\":1,\"matches\":0,\"kills\":0,\"deaths\":0,\"damage\":0,\"playSeconds\":0,\"wins\":0,\"presets\":[],\"defaultPreset\":0}]}");
        assertTrue(new PlayerDatabase(file).get(id,"old").effectColors().isEmpty());
    }
    @Test void invalidEffectColorsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new PlayerRecord.EffectColor("Bad Key",0,-1));
        assertThrows(IllegalArgumentException.class,()->new PlayerRecord.EffectColor("rapier",0x1000000,-1));
        assertThrows(IllegalArgumentException.class,()->new PlayerRecord.EffectColor("rapier",0,-2));
    }
    @Test void savedColorsPersistMoveToEndAndCapAtSixteen() throws Exception {
        String id=UUID.randomUUID().toString();Path file=directory.resolve("saved.json");
        var record=PlayerRecord.empty(id,"p");
        for(int i=0;i<20;i++)record=record.withSavedColor(i);
        assertEquals(PlayerRecord.SAVED_COLORS,record.savedColors().size());assertEquals(4,record.savedColors().get(0));
        record=record.withSavedColor(4).withoutSavedColor(19).withNickname("저장").set("xp","5");
        assertEquals(4,record.savedColors().get(record.savedColors().size()-1));assertFalse(record.savedColors().contains(19));
        new PlayerDatabase(file).update(record);
        assertEquals(record.savedColors(),new PlayerDatabase(file).get(id,"p").savedColors());
        assertThrows(IllegalArgumentException.class,()->PlayerRecord.empty(id,"p").withSavedColor(0x1000000));
    }
    @Test void blendRunsFromFirstToSecond() {
        int[] pair={0xFF0000,0x0000FF};
        assertEquals(0xFF0000,EffectColors.at(pair,0));assertEquals(0x0000FF,EffectColors.at(pair,1));
        assertEquals(0x800080,EffectColors.at(pair,.5f));
        assertEquals(0x123456,EffectColors.at(new int[]{0x123456,-1},.7f));
        assertEquals(0xFF0DD9,EffectColors.server(null,"orbital_strike")[0]);
    }
}
