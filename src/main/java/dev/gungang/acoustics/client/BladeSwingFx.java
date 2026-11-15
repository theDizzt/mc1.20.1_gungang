package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.GungangNetwork;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Animated, continuous blade ribbons. No particles: visible regardless of the particle setting. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class BladeSwingFx {
    private record Swing(int entity,int motion,Vec3 forward,Vec3 right,Vec3 up,double born){}
    private static final ArrayList<Swing> SWINGS=new ArrayList<>();
    private static ClientLevel world;

    public static void receive(GungangNetwork.BladeSwing packet){
        var mc=Minecraft.getInstance();
        if(world!=mc.level){SWINGS.clear();world=mc.level;}
        if(world==null||packet.motion()<1||packet.motion()>8)return;
        var forward=Vec3.directionFromRotation(packet.motion()==8?0:packet.pitch(),packet.yaw());
        var right=Vec3.directionFromRotation(0,packet.yaw()+90);
        var up=forward.cross(right).normalize();
        SWINGS.add(new Swing(packet.entity(),packet.motion(),forward,right,up,world.getGameTime()+mc.getFrameTime()));
        if(SWINGS.size()>128)SWINGS.remove(0);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();
        if(world!=mc.level){SWINGS.clear();world=mc.level;}
        if(world==null||SWINGS.isEmpty())return;
        double now=world.getGameTime()+event.getPartialTick();
        SWINGS.removeIf(s->now-s.born()>life(s)||now<s.born());
        var buffers=mc.renderBuffers().bufferSource();
        var buffer=buffers.getBuffer(RenderType.lightning());
        var matrix=event.getPoseStack().last().pose();
        for(var swing:SWINGS){
            if(!(world.getEntity(swing.entity()) instanceof LivingEntity owner)||!owner.isAlive()||owner.isInvisible())continue;
            // The pack already supplies its own first-person blade animation.
            if(owner==mc.getCameraEntity()&&mc.options.getCameraType().isFirstPerson())continue;
            double progress=Math.max(0,Math.min(1,(now-swing.born())/life(swing)));
            var origin=owner.getEyePosition(event.getPartialTick()).add(0,-.35,0).subtract(event.getCamera().getPosition());
            if(swing.motion()==6){
                thrust(buffer,matrix,origin,swing,progress);
            }else{
                double tilt=switch(swing.motion()){case 1->20;case 2->-160;case 3->50;case 4->-130;case 5->35;case 7->-25;default->0;};
                arc(buffer,matrix,origin,swing,progress,tilt);
                if(swing.motion()==5)arc(buffer,matrix,origin,swing,progress,-35);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static int life(Swing s){return s.motion()==8?10:6;}

    private static void arc(VertexConsumer buffer,Matrix4f matrix,Vec3 origin,Swing s,double progress,double tilt){
        double radians=Math.toRadians(tilt);
        var side=s.right().scale(Math.cos(radians)).add(s.up().scale(Math.sin(radians)));
        double sweep=s.motion()==8?Math.PI*2:Math.toRadians(140);
        double head=Math.min(1,progress*1.4),tail=Math.max(0,head-.55);
        float fade=(float)Math.min(1,(1-progress)*3);
        for(int i=0;i<40;i++){
            double t0=tail+(head-tail)*i/40,t1=tail+(head-tail)*(i+1)/40;
            double a0=-sweep/2+sweep*t0,a1=-sweep/2+sweep*t1;
            var d0=s.forward().scale(Math.cos(a0)).add(side.scale(Math.sin(a0)));
            var d1=s.forward().scale(Math.cos(a1)).add(side.scale(Math.sin(a1)));
            float strength=(float)((i+1)/40.0)*fade;
            double radius=s.motion()==8?2.1:1.9;
            // A broad translucent blade face and a narrow bright cutting edge.
            band(buffer,matrix,origin,d0,d1,radius-.42,radius,.32f*strength);
            band(buffer,matrix,origin,d0,d1,radius-.065,radius,.9f*strength);
        }
    }

    private static void band(VertexConsumer b,Matrix4f m,Vec3 o,Vec3 a,Vec3 c,double inner,double outer,float alpha){
        vertex(b,m,o.add(a.scale(inner)),0);
        vertex(b,m,o.add(a.scale(outer)),alpha);
        vertex(b,m,o.add(c.scale(outer)),alpha);
        vertex(b,m,o.add(c.scale(inner)),0);
    }

    private static void thrust(VertexConsumer b,Matrix4f m,Vec3 origin,Swing s,double progress){
        var tip=origin.add(s.forward().scale(.8+2.3*Math.min(1,progress*1.5)));
        var back=tip.subtract(s.forward().scale(1.3));
        float alpha=(float)(1-progress);
        for(var side:new Vec3[]{s.right(),s.up()}){
            vertex(b,m,back,0);
            vertex(b,m,tip.subtract(s.forward().scale(.4)).add(side.scale(.13)),alpha);
            vertex(b,m,tip,alpha);
            vertex(b,m,tip.subtract(s.forward().scale(.4)).subtract(side.scale(.13)),alpha);
        }
    }

    private static void vertex(VertexConsumer b,Matrix4f m,Vec3 p,float alpha){
        b.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(.75f,.9f,1f,alpha).endVertex();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){SWINGS.clear();world=null;}
    private BladeSwingFx(){}
}
