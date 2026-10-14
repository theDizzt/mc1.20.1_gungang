package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeathZoneTest {
    @Test void bodyIntersectionAndOutsideEdges(){
        var z=new DeathZone(0,0,0,1,.1,1);z.validate();
        assertTrue(z.intersects(.1,.05,.1,.7,1.85,.7));assertFalse(z.intersects(1,0,0,2,2,1));
        assertFalse(z.intersects(0,.1,0,1,2,1));
    }
    @Test void multipleZonesRoundtripAndDeathmatchPreservesThem(){
        var gson=new com.google.gson.Gson();var json=gson.toJsonTree(BombRules.example()).getAsJsonObject();
        json.add("deathZones",com.google.gson.JsonParser.parseString("[{\"minX\":0,\"minY\":-64,\"minZ\":0,\"maxX\":10,\"maxY\":0,\"maxZ\":10},{\"minX\":30,\"minY\":0,\"minZ\":30,\"maxX\":32,\"maxY\":1,\"maxZ\":32}]"));
        var map=gson.fromJson(json,ArenaMap.class).validate();assertEquals(2,map.deathZones().size());
        assertEquals(map.deathZones(),DeathmatchRules.defaults().arena(map).deathZones());
        assertTrue(ArenaMap.example().deathZones().isEmpty());
    }
    @Test void suicideCannotAwardRecentAttackerOrAssist(){
        var ledger=new MatchLedger();var a=java.util.UUID.randomUUID();var b=java.util.UUID.randomUUID();
        ledger.add(a,"a","ALPHA");ledger.add(b,"b","BRAVO");ledger.damage(a,b,99,1);ledger.death(b,b,2,true);
        assertEquals(0,ledger.kills("ALPHA"));assertEquals(0,ledger.kills("BRAVO"));
        assertEquals(1,ledger.rows().stream().filter(r->r.id().equals(b.toString())).findFirst().orElseThrow().deaths());
        assertTrue(ledger.rows().stream().allMatch(r->r.kills()==0&&r.assists()==0));
    }
    @Test void invalidBoundsRejected(){
        assertThrows(IllegalArgumentException.class,()->new DeathZone(1,0,0,0,1,1).validate());
        assertThrows(IllegalArgumentException.class,()->new DeathZone(0,0,0,1,0,1).validate());
        assertThrows(IllegalArgumentException.class,()->new DeathZone(Double.NaN,0,0,1,1,1).validate());
    }
}
