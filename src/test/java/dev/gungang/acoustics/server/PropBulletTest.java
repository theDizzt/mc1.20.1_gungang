package dev.gungang.acoustics.server;

import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropBulletTest {
    @Test void thinPartsPassInEveryOrientation(){
        assertTrue(PropBullet.blockingShape(Shapes.box(0,0,0,1,1,.125)).isEmpty());
        assertTrue(PropBullet.blockingShape(Shapes.box(0,0,0,.25,1,1)).isEmpty());
        assertTrue(PropBullet.blockingShape(Shapes.box(0,0,0,1,.499,1)).isEmpty());
    }
    @Test void halfBlocksAndFullBlocksStillStopBullets(){
        var half=Shapes.box(0,0,0,1,.5,1);
        assertSame(half,PropBullet.blockingShape(half));
        assertSame(Shapes.block(),PropBullet.blockingShape(Shapes.block()));
    }
    @Test void thinRailDoesNotRemoveThickPost(){
        var post=Shapes.box(0,0,0,.5,1,.5);
        var combined=Shapes.or(post,Shapes.box(.5,.4,0,1,.6,.2));
        var result=PropBullet.blockingShape(combined);
        assertFalse(result.isEmpty());
        assertEquals(.5,result.bounds().maxX);
        assertEquals(1,result.bounds().maxY);
    }
}
