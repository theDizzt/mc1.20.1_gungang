package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * LAS-5 (27): the gun pack draws its laser only in the first-person view model, so in third person (and for everyone
 * watching the shooter) a shot showed nothing. Each shot the server broadcasts ({@link dev.gungang.acoustics.server.Las5Damage#beam})
 * draws a short yellow beam from the shooter's gun to what the shot hits, for every nearby player; the shooter's own
 * first-person view keeps the pack's laser alone.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class Las5Beam {
    private record Beam(Vec3 from,Vec3 to,long born,boolean m95){}
    private static final List<Beam> BEAMS=new ArrayList<>();
    /** Beam life in ticks. */
    private static final int LIFE=3;
    private static final int M95_LIFE=10; // 0.5 seconds at 20 ticks per second.
    /** Layers, outermost first: half width, alpha, core (pale) or glow (yellow). */
    private static final float[][] LAYERS={{.09f,.18f,0},{.045f,.45f,0},{.018f,.9f,1}};

    public static void receive(dev.gungang.acoustics.server.GungangNetwork.Las5Shot shot){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        // The own shot in first person already shows the pack's laser.
        if(mc.player!=null&&shot.owner()==mc.player.getId()&&mc.getCameraEntity()==mc.player&&mc.options.getCameraType().isFirstPerson())return;
        var start=new Vec3(shot.fx(),shot.fy(),shot.fz());var end=new Vec3(shot.tx(),shot.ty(),shot.tz());
        var dir=end.subtract(start);if(dir.lengthSqr()<1e-6)return;
        var owner=shot.owner()<0?null:mc.level.getEntity(shot.owner());
        BEAMS.add(new Beam(muzzle(owner,start,dir.normalize()),end,mc.level.getGameTime(),false));
        if(BEAMS.size()>64)BEAMS.remove(0);
    }
    public static void receive(dev.gungang.acoustics.server.GungangNetwork.M95Shot shot){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 from=new Vec3(shot.fx(),shot.fy(),shot.fz()),to=new Vec3(shot.tx(),shot.ty(),shot.tz());
        if(from.distanceToSqr(to)<1e-8)return;
        BEAMS.add(new Beam(muzzle(mc.level.getEntity(shot.owner()),from,to.subtract(from).normalize()),to,mc.level.getGameTime(),true));
        if(BEAMS.size()>64)BEAMS.remove(0);
    }
    /** Roughly the gun's muzzle in the shooter's right hand, not the eye the bullet starts from. */
    private static Vec3 muzzle(Entity owner,Vec3 start,Vec3 dir){
        if(!(owner instanceof LivingEntity))return start;
        var right=new Vec3(-dir.z,0,dir.x);if(right.lengthSqr()<1e-6)return start;
        return start.add(right.normalize().scale(.3)).add(0,-.22,0).add(dir.scale(.6));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||BEAMS.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null){BEAMS.clear();return;}
        long now=mc.level.getGameTime();float partial=event.getPartialTick();
        BEAMS.removeIf(b->now-b.born()>=(b.m95()?M95_LIFE:LIFE+1)||now<b.born());
        if(BEAMS.isEmpty())return;
        var camera=event.getCamera().getPosition();var pose=event.getPoseStack();
        var buffers=mc.renderBuffers().bufferSource();var buffer=buffers.getBuffer(RenderType.lightning());var matrix=pose.last().pose();
        var eye=new org.joml.Vector3f();
        for(var beam:BEAMS){
            float fade=Math.max(0,1-(now-beam.born()+partial)/(beam.m95()?M95_LIFE:LIFE+1));
            var from=new org.joml.Vector3f((float)(beam.from().x-camera.x),(float)(beam.from().y-camera.y),(float)(beam.from().z-camera.z));
            var to=new org.joml.Vector3f((float)(beam.to().x-camera.x),(float)(beam.to().y-camera.y),(float)(beam.to().z-camera.z));
            var dir=new org.joml.Vector3f(to).sub(from);if(dir.lengthSquared()<1e-4f)continue;dir.normalize();
            for(float[] layer:LAYERS){
                boolean core=layer[2]>0;
                PropObjectRenderer.strip(buffer,matrix,from,to,dir,eye,layer[0],beam.m95()?133/255f:1,beam.m95()?219/255f:core?1:.86f,beam.m95()?217/255f:core?.75f:.1f,layer[1]*fade);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }
    private Las5Beam(){}
}
