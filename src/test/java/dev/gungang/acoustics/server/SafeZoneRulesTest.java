package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class SafeZoneRulesTest {
    @Test void unorderedCornersIncludeWholeBoundaryBlocks(){
        var zone=new SafeZoneRules.Zone("대기실","minecraft:overworld",true,new SafeZoneRules.Point(-1,84,-11),new SafeZoneRules.Point(24,116,-31));
        assertTrue(zone.contains("minecraft:overworld",-1,84,-31));
        assertTrue(zone.contains("minecraft:overworld",24.999,116.999,-10.001));
        assertFalse(zone.contains("minecraft:overworld",25,85,-20));
        assertFalse(zone.contains("minecraft:overworld",0,117,-20));
        assertFalse(zone.contains("minecraft:the_nether",0,85,-20));
    }
    @Test void multipleZonesAndDisabledZones(){
        var p=new SafeZoneRules.Point(1,2,3);var q=new SafeZoneRules.Point(-8,4,-5);
        var rules=new SafeZoneRules(List.of(new SafeZoneRules.Zone("one","minecraft:overworld",false,p,p),new SafeZoneRules.Zone("two","minecraft:overworld",null,q,q)));
        assertFalse(rules.contains("minecraft:overworld",1,2,3));assertTrue(rules.contains("minecraft:overworld",-7.5,4,-4.5));
        assertThrows(IllegalArgumentException.class,()->new SafeZoneRules.Zone("bad","oops",true,p,p));
        assertFalse(SafeZoneRules.empty().contains("minecraft:overworld",0,0,0));
    }
}
