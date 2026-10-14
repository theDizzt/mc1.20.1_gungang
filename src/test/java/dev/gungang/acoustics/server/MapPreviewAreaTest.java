package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapPreviewAreaTest {
    @Test void northUpProjectionAndLimits(){
        var a=new MapPreviewArea(-16,-64,-32,15,64,31);a.validate();
        assertEquals(0,a.u(-16));assertEquals(.5,a.u(0));assertEquals(.5,a.v(0));
        assertThrows(IllegalArgumentException.class,()->new MapPreviewArea(0,0,0,512,32,16).validate());
        assertThrows(IllegalArgumentException.class,()->new MapPreviewArea(4,0,0,0,32,16).validate());
        assertThrows(IllegalArgumentException.class,()->new MapPreviewArea(0,32,0,16,0,16).validate());
    }
    @Test void configSurvivesDeathmatchConversion(){
        var json=new com.google.gson.Gson().toJsonTree(ArenaMap.example()).getAsJsonObject();
        json.add("previewArea",new com.google.gson.Gson().toJsonTree(new MapPreviewArea(-16,0,-16,63,100,31)));
        var map=new com.google.gson.Gson().fromJson(json,ArenaMap.class).validate();
        assertEquals(map.previewArea(),DeathmatchRules.defaults().arena(map).previewArea());
    }
}
