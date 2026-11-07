package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * LAS-5 (27): the gun pack draws its laser only in the first-person view model, so in third person (and for everyone
 * watching the shooter) a shot showed nothing. Each LAS-5 bullet arriving on the client draws a short yellow beam from
 * the shooter's gun to what the shot hits; the shooter's own first-person view keeps the pack's laser alone.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class Las5Beam {
    private record Beam(Vec3 from,Vec3 to,long born){}
    private static final List<Beam> BEAMS=new ArrayList<>();
    /** Beam life in ticks and reach in blocks. */
    private static final int LIFE=3;
    private static final double RANGE=96;
    /** Layers, outermost first: half width, alpha, core (pale) or glow (yellow). */
    private static final float[][] LAYERS={{.09f,.18f,0},{.045f,.45f,0},{.018f,.9f,1}};

    @SubscribeEvent public static void shot(EntityJoinLevelEvent event){
        if(!event.getLevel().isClientSide()||!(event.getEntity() instanceof Projectile bullet)
                ||!(bullet instanceof dev.gungang.acoustics.server.Las5Damage.BulletAccess access)||!"zeta:las_5".equals(String.valueOf(access.getGunId())))return;
        var mc=Minecraft.getInstance();var owner=bullet.getOwner();
        // The own shot in first person already shows the pack's laser.
        if(owner!=null&&owner==mc.player&&mc.getCameraEntity()==mc.player&&mc.options.getCameraType().isFirstPerson())return;
        var motion=bullet.getDeltaMovement();if(motion.lengthSqr()<1e-6)return;
        var dir=motion.normalize();var start=bullet.position();
        var end=start.add(dir.scale(RANGE));
        var block=bullet.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,bullet));
        if(block.getType()!=HitResult.Type.MISS)end=block.getLocation();
        var hit=ProjectileUtil.getEntityHitResult(bullet.level(),bullet,start,end,bullet.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
                e->e!=owner&&e.isPickable()&&!e.isSpectator(),.3f);
        if(hit!=null)end=hit.getLocation();
        BEAMS.add(new Beam(muzzle(owner,start,dir),end,bullet.level().getGameTime()));
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
        BEAMS.removeIf(b->now-b.born()>LIFE||now<b.born());
        if(BEAMS.isEmpty())return;
        var camera=event.getCamera().getPosition();var pose=event.getPoseStack();
        var buffers=mc.renderBuffers().bufferSource();var buffer=buffers.getBuffer(RenderType.lightning());var matrix=pose.last().pose();
        var eye=new org.joml.Vector3f();
        for(var beam:BEAMS){
            float fade=Math.max(0,1-(now-beam.born()+partial)/(LIFE+1));
            var from=new org.joml.Vector3f((float)(beam.from().x-camera.x),(float)(beam.from().y-camera.y),(float)(beam.from().z-camera.z));
            var to=new org.joml.Vector3f((float)(beam.to().x-camera.x),(float)(beam.to().y-camera.y),(float)(beam.to().z-camera.z));
            var dir=new org.joml.Vector3f(to).sub(from);if(dir.lengthSquared()<1e-4f)continue;dir.normalize();
            for(float[] layer:LAYERS){
                boolean core=layer[2]>0;
                PropObjectRenderer.strip(buffer,matrix,from,to,dir,eye,layer[0],1,core?1:.86f,core?.75f:.1f,layer[1]*fade);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }
    private Las5Beam(){}
}
