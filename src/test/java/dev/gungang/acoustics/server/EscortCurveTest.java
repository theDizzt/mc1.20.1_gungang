package dev.gungang.acoustics.server;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EscortCurveTest {
    @Test void rightAngleIsASmoothTurnThroughTheCheckpoint(){
        var points=List.of(new Vec3(0,0,0),new Vec3(10,0,0),new Vec3(10,0,10),new Vec3(10,2,20));var curve=new EscortCurve(points);
        assertEquals(points.get(1),curve.position(0,1));assertEquals(points.get(1),curve.position(1,0));
        assertTrue(curve.direction(0,.9999).dot(curve.direction(1,.0001))>.9999);
        var tangent=curve.direction(0,1);assertTrue(tangent.x>.6&&tangent.z>.6,"The corner tangent turns diagonally instead of snapping 90 degrees");
    }
    @Test void UnevenSpacingPreservesDirectionContinuity(){
        var points=List.of(new Vec3(0,0,0),new Vec3(1,0,0),new Vec3(1,5,30),new Vec3(2,5,30));var curve=new EscortCurve(points);
        for(int i=0;i<2;i++)assertTrue(curve.direction(i,1).dot(curve.direction(i+1,0))>.999999);
        for(int i=0;i<3;i++)for(int t=0;t<=100;t++){assertTrue(Double.isFinite(curve.position(i,t/100d).lengthSqr()));assertEquals(1,curve.direction(i,t/100d).length(),1e-6);}
    }
    @Test void straightLineDoesNotWobble(){var curve=new EscortCurve(List.of(Vec3.ZERO,new Vec3(0,0,2),new Vec3(0,0,30)));for(int i=0;i<2;i++)for(int t=0;t<=100;t++){var p=curve.position(i,t/100d);assertEquals(0,p.x);assertEquals(0,p.y);assertEquals(1,curve.direction(i,t/100d).z,1e-6);}}
}
