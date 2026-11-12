package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Brief ARC-12 disorientation, independent of the longer glowing outline. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class ArcShock {
    public static final int TICKS=6;
    private record Shock(long start,long end,float yaw,float pitch,Vec3 push){}
    private static final Map<LivingEntity,Shock> ACTIVE=new WeakHashMap<>();
    static void apply(LivingEntity body,Vec3 from){
        if(!body.isAlive()||body.isSpectator())return;
        long now=body.level().getGameTime();
        var direction=body.position().subtract(from).multiply(1,0,1);
        if(direction.lengthSqr()<1e-6)direction=body.getLookAngle().multiply(-1,0,-1);
        var push=direction.normalize().scale(.18).add(0,.08,0);
        ACTIVE.put(body,new Shock(now,now+TICKS,body.getYRot(),body.getXRot(),push));
        if(body instanceof ServerPlayer player)GungangNetwork.send(player,new GungangNetwork.ShockLook(TICKS,body.getYRot(),body.getXRot()));
    }
    static boolean justHit(LivingEntity body){var shock=ACTIVE.get(body);return shock!=null&&shock.start()==body.level().getGameTime();}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var iterator=ACTIVE.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();var body=entry.getKey();var shock=entry.getValue();long now=body.level().getGameTime();
            if(body.isRemoved()||!body.isAlive()||body.isSpectator()||LobbyManager.respawning(body.getUUID())||now>=shock.end()||now<shock.start()){iterator.remove();continue;}
            body.setYRot(shock.yaw());body.setXRot(shock.pitch());body.setYHeadRot(shock.yaw());body.setYBodyRot(shock.yaw());
            if(now==shock.start()){
                double resistance=body.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
                var motion=body.getDeltaMovement().add(shock.push().scale(Math.max(0,1-resistance)));
                if(body instanceof ServerPlayer player)PlayerImpulse.apply(player,motion);
                else{body.setDeltaMovement(motion);body.hasImpulse=true;body.hurtMarked=true;}
            }
        }
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){ACTIVE.clear();}
    private ArcShock(){}
}
