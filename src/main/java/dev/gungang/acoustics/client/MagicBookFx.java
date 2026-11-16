package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.GungangNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 38 magic book beams: the holy water stream (pale blue) and the ruin beam (violet with a dark core), drawn from the
 * book in the caster's hand to where the server's trace ended. The server refreshes them every other tick.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class MagicBookFx {
    private record Beam(int owner,int kind,Vec3 from,Vec3 to,long born){}
    private static final List<Beam> BEAMS=new ArrayList<>();
    private static final int LIFE=3;
    /** Per kind, outermost layer first: half width, alpha, RGB. */
    private static final float[][][] LAYERS={
            {{.16f,.15f,.45f,.75f,1f},{.08f,.35f,.6f,.85f,1f},{.03f,.85f,.92f,.97f,1f}},
            {{.22f,.2f,.45f,.1f,.7f},{.11f,.45f,.55f,.2f,.85f},{.04f,.95f,.12f,.02f,.18f}}};

    public static void receive(GungangNetwork.BookBeam beam){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        BEAMS.removeIf(b->b.owner()==beam.owner());
        BEAMS.add(new Beam(beam.owner(),Math.max(0,Math.min(1,beam.kind())),new Vec3(beam.fx(),beam.fy(),beam.fz()),new Vec3(beam.tx(),beam.ty(),beam.tz()),mc.level.getGameTime()));
        if(BEAMS.size()>64)BEAMS.remove(0);
    }
    /** From the held book, not the eye: a little right of and below the view, ahead of the body. */
    private static Vec3 hand(LivingEntity owner,Vec3 eye,Vec3 to){
        var dir=to.subtract(eye);if(dir.lengthSqr()<1e-6)return eye;dir=dir.normalize();
        var right=new Vec3(-dir.z,0,dir.x);if(right.lengthSqr()<1e-6)return eye;
        boolean firstPerson=owner==Minecraft.getInstance().player&&Minecraft.getInstance().options.getCameraType().isFirstPerson();
        return eye.add(right.normalize().scale(firstPerson?.25:.35)).add(0,firstPerson?-.25:-.3,0).add(dir.scale(.5));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||BEAMS.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null){BEAMS.clear();return;}
        long now=mc.level.getGameTime();float partial=event.getPartialTick();
        BEAMS.removeIf(b->now-b.born()>LIFE||now<b.born());
        if(BEAMS.isEmpty())return;
        var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        var buffer=buffers.getBuffer(RenderType.lightning());var matrix=event.getPoseStack().last().pose();var eye=new org.joml.Vector3f();
        for(var beam:BEAMS){
            var owner=mc.level.getEntity(beam.owner());
            var start=beam.from();
            // The caster's beam follows their current eye, so it does not lag behind a turning view.
            if(owner instanceof LivingEntity living)start=hand(living,living.getEyePosition(partial),beam.to());
            var from=new org.joml.Vector3f((float)(start.x-camera.x),(float)(start.y-camera.y),(float)(start.z-camera.z));
            var to=new org.joml.Vector3f((float)(beam.to().x-camera.x),(float)(beam.to().y-camera.y),(float)(beam.to().z-camera.z));
            var dir=new org.joml.Vector3f(to).sub(from);if(dir.lengthSquared()<1e-4f)continue;dir.normalize();
            float flicker=.85f+.15f*(float)Math.sin((now+partial)*1.7+beam.owner());
            for(float[] layer:LAYERS[beam.kind()])
                PropObjectRenderer.strip(buffer,matrix,from,to,dir,eye,layer[0]*flicker,layer[2],layer[3],layer[4],layer[1]);
        }
        buffers.endBatch(RenderType.lightning());
    }
    private MagicBookFx(){}
}
