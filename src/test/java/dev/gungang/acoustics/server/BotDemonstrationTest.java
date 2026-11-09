package dev.gungang.acoustics.server;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BotDemonstrationTest {
    @Test void observedDirectionIsRelativeToTheInitiallyVisibleTarget(){
        var enemy=new Vec3(10,0,0);
        assertEquals("APPROACH",BotDemonstration.movement(Vec3.ZERO,new Vec3(1,0,0),enemy,null));
        assertEquals("WITHDRAW",BotDemonstration.movement(Vec3.ZERO,new Vec3(-1,0,0),enemy,null));
        assertEquals("LATERAL",BotDemonstration.movement(Vec3.ZERO,new Vec3(0,0,1),enemy,null));
        assertEquals("HOLD",BotDemonstration.movement(Vec3.ZERO,new Vec3(.1,0,0),enemy,null));
        assertEquals("OTHER",BotDemonstration.movement(Vec3.ZERO,new Vec3(1,0,0),null,null));
        assertEquals("TO_OBJECTIVE",BotDemonstration.movement(Vec3.ZERO,new Vec3(1,0,0),null,enemy));
    }
    @Test void unknownTelemetryAndForcedMovementDoNotCreateNegativeLabels(){
        var window=new BotDemonstration(Map.of(),Vec3.ZERO,null,null,0,"test",false);
        window.observe(false,false,false);
        var targets=window.targets(new Vec3(1,0,0));
        assertTrue(targets.isEmpty());
        window.action("fire");
        assertEquals(Map.of("fire","YES"),window.targets(Vec3.ZERO));
    }
}
