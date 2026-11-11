package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.EffectColors;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Short, travelling laser flashes; hit detection remains entirely server-authoritative. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class TurretLaser {
    private static final double SPEED=12, LENGTH=2.4;
    private static final int LIMIT=128;
    private record Bolt(Vec3 from,Vec3 direction,double distance,long born,int[] colors){}
    private static final List<Bolt> BOLTS=new ArrayList<>();
    private static ClientLevel world;
    private static final float[][] LAYERS={{.085f,.18f},{.04f,.5f},{.014f,1f}};

    public static void shot(Vec3 from,Vec3 to,UUID owner){
        var level=Minecraft.getInstance().level;if(level==null)return;
        if(world!=level){BOLTS.clear();world=level;}
        var delta=to.subtract(from);double distance=delta.length();
        if(!Double.isFinite(distance)||distance<1e-5)return;
        if(BOLTS.size()>=LIMIT)BOLTS.remove(0);
        BOLTS.add(new Bolt(from,delta.scale(1/distance),distance,level.getGameTime(),EffectColors.client(owner,"turret").clone()));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){BOLTS.clear();world=null;}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=world){BOLTS.clear();world=mc.level;}
        if(world==null||BOLTS.isEmpty())return;
        long now=world.getGameTime();float partial=event.getPartialTick();
        // At least two ticks keep nearby shots visible, while every segment stays inside the server hit point.
        BOLTS.removeIf(b->now<b.born()||now-b.born()>=Math.max(2,(b.distance()+LENGTH)/SPEED));
        var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        var buffer=buffers.getBuffer(RenderType.lightning());var matrix=event.getPoseStack().last().pose();
        var eye=new org.joml.Vector3f();
        for(var bolt:BOLTS){
            double life=Math.max(2,(bolt.distance()+LENGTH)/SPEED),age=now-bolt.born()+partial;
            double head=Math.min(bolt.distance(),age*SPEED+LENGTH);
            double tail=Math.min(Math.max(0,bolt.distance()-LENGTH),Math.max(0,age*SPEED));
            var from=bolt.from().add(bolt.direction().scale(tail)).subtract(camera).toVector3f();
            var to=bolt.from().add(bolt.direction().scale(head)).subtract(camera).toVector3f();
            int glow=EffectColors.at(bolt.colors(),(float)(head/bolt.distance()));
            int core=EffectColors.at(new int[]{glow,0xFFFFFF},.7f);
            float fade=(float)Math.max(0,1-age/life);
            for(int i=0;i<LAYERS.length;i++){
                int color=i==2?core:glow;
                PropObjectRenderer.strip(buffer,matrix,from,to,bolt.direction().toVector3f(),eye,LAYERS[i][0],
                        EffectColors.red(color),EffectColors.green(color),EffectColors.blue(color),LAYERS[i][1]*fade);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }
    private TurretLaser(){}
}
