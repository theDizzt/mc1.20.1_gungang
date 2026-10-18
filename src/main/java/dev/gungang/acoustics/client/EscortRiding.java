package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.EscortCart;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Carries the local player on the payload cart; the client owns its position, so walking and aiming stay free while riding. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class EscortRiding {
    private static int cart=-1;
    private static Vec3 last;
    private static float lastYaw;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.START)return;
        var mc=Minecraft.getInstance();var p=mc.player;
        if(p==null||mc.level==null){cart=-1;return;}
        if(mc.isPaused())return;
        var found=mc.level.getEntitiesOfClass(EscortCart.class,p.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
        if(found==null){cart=-1;return;}
        if(found.getId()!=cart){cart=found.getId();last=found.position();lastYaw=found.getYRot();return;}
        Vec3 now=found.position();float turn=net.minecraft.util.Mth.wrapDegrees(found.getYRot()-lastYaw);
        Vec3 old=last;last=now;lastYaw=found.getYRot();
        if(!found.carries(p)){shove(p,found);return;}
        if(old.distanceToSqr(now)<1e-10&&Math.abs(turn)<1e-4)return;
        // Same displacement the cart applied: translate with it and swing around its centre when it turns.
        double rotation=Math.toRadians(turn),cos=Math.cos(rotation),sin=Math.sin(rotation);
        Vec3 offset=p.position().subtract(old),target=now.add(offset.x*cos-offset.z*sin,offset.y,offset.x*sin+offset.z*cos);
        p.move(MoverType.SELF,target.subtract(p.position()));
        p.setYRot(p.getYRot()+turn);p.yRotO+=turn;p.fallDistance=0;
    }
    /**
     * A cart moving into the player (rolling back onto a defender, swinging through a turn) would otherwise swallow them:
     * collision ignores shapes already overlapped. Push out the shortest way, stepping up onto the deck when that is short.
     */
    private static void shove(net.minecraft.client.player.LocalPlayer p,EscortCart cart){
        var box=cart.getBoundingBox();var body=p.getBoundingBox();
        if(p.isSpectator()||!box.deflate(1e-3).intersects(body))return;
        double east=box.maxX-body.minX,west=body.maxX-box.minX,south=box.maxZ-body.minZ,north=body.maxZ-box.minZ,up=box.maxY-body.minY;
        double side=Math.min(Math.min(east,west),Math.min(south,north));
        Vec3 out=up<=.6&&up<=side?new Vec3(0,up+.01,0):side==east?new Vec3(east+.01,0,0):side==west?new Vec3(-west-.01,0,0):side==south?new Vec3(0,0,south+.01):new Vec3(0,0,-north-.01);
        p.move(MoverType.SELF,out);
    }
    private EscortRiding(){}
}
