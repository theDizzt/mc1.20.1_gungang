package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class AirAssault {
    private static final class Flight {
        final ServerPlayer player;
        final net.minecraft.server.level.ServerLevel level;
        final boolean gravity;
        final long diveAt;
        final double hoverY;
        final ExtraGadgetConfig.AirAssault config=ExtraGadgetConfig.airAssault;
        Vec3 target;
        int diveTicks;
        boolean verticalDrop;
        Flight(ServerPlayer p) {
            player=p;level=p.serverLevel();gravity=p.isNoGravity();
            diveAt=level.getGameTime()+ExtraGadgetConfig.ticks(config.hoverSeconds());hoverY=p.getY()+5;
        }
    }
    private static final Map<UUID,Flight> FLIGHTS=new HashMap<>();
    private static final UUID SPEED_ID=UUID.fromString("bf8b5b1d-5211-4823-a6d8-9799a8fb3f95");
    private static final Map<ServerPlayer,Long> SPEED=new HashMap<>();
    private static final double LAUNCH_VELOCITY=launchVelocity();
    private record Shockwave(net.minecraft.server.level.ServerLevel level,Vec3 center,long started,double radius) { }
    private static final java.util.List<Shockwave> SHOCKWAVES=new java.util.ArrayList<>();
    @SubscribeEvent public static void effects(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(var iterator=SPEED.entrySet().iterator();iterator.hasNext();){var boost=iterator.next();var p=boost.getKey();
            if(!p.isAlive()||p.isRemoved()||p.level().getGameTime()>=boost.getValue()){
                var attribute=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(attribute!=null)attribute.removeModifier(SPEED_ID);iterator.remove();
            }
        }
        SHOCKWAVES.removeIf(wave->wave.level().getGameTime()-wave.started()>10);
        for(var wave:SHOCKWAVES) {
            long age=wave.level().getGameTime()-wave.started();
            if(age%2!=0)continue;
            double radius=wave.radius()*(age+1)/11;
            for(int i=0;i<24;i++) {
                double angle=2*Math.PI*i/24;
                double x=wave.center().x+Math.cos(angle)*radius,z=wave.center().z+Math.sin(angle)*radius;
                double y=surfaceY(wave.level(),x,wave.center().y,z);
                wave.level().sendParticles(ParticleTypes.CLOUD,x,y+.15,z,1,.04,.02,.04,.015);
                if(age<=4&&i%3==0)wave.level().sendParticles(ParticleTypes.CRIT,x,y+.25,z,1,.05,.1,.05,.08);
            }
        }
    }
    public static void start(ServerPlayer p,ItemStack stack) {
        if(FLIGHTS.containsKey(p.getUUID())||p.isPassenger()||p.isFallFlying()||SafeZones.contains(p))return;
        var flight=new Flight(p);
        p.stopUsingItem();p.setNoGravity(true);p.fallDistance=0;
        FLIGHTS.put(p.getUUID(),flight);
        velocity(p,new Vec3(0,.5,0));
        SecondaryRuntime.commit(p,stack);
        p.level().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,
                net.minecraft.sounds.SoundSource.PLAYERS,1,1);
    }
    // Motion packets leave yaw/pitch untouched; the client moves normally through block collisions.
    private static void velocity(ServerPlayer p,Vec3 motion) {
        p.fallDistance=0;PlayerImpulse.apply(p,motion);
        GungangNetwork.send(p,new GungangNetwork.AssaultMotion(true,motion.x,motion.y,motion.z));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        var flight=FLIGHTS.get(p.getUUID());if(flight==null)return;
        if(flight.player!=p||!p.isAlive()||p.isSpectator()||p.serverLevel()!=flight.level
                ||p.isPassenger()||LobbyManager.respawning(p.getUUID())||FreeForAllMatch.blocked(p)||SafeZones.contains(p)) {clear(p);return;}
        p.setNoGravity(true);p.fallDistance=0;
        if(flight.level.getGameTime()<flight.diveAt) {
            // Rise over roughly half a second, then maintain the five-block altitude.
            velocity(p,new Vec3(0,net.minecraft.util.Mth.clamp(flight.hoverY-p.getY(),-.5,.5),0));
            if(p.tickCount%2==0)p.serverLevel().sendParticles(ParticleTypes.FIREWORK,p.getX(),p.getY(),p.getZ(),3,.2,.1,.2,.025);
            return;
        }
        // Capture the latest facing once, after the freely aimed one-second window.
        if(flight.target==null) {
            Vec3 look=p.getLookAngle();
            var hit=p.level().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),
                    p.getEyePosition().add(look.scale(flight.config.diveRange())),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK) {
                flight.target=hit.getLocation();
            } else {
                Vec3 horizontal=new Vec3(look.x,0,look.z);
                Vec3 end=p.position().add(horizontal.lengthSqr()<1e-8?Vec3.ZERO:horizontal.normalize().scale(flight.config.diveRange()));
                var ground=p.level().clip(new net.minecraft.world.level.ClipContext(end,
                        new Vec3(end.x,p.level().getMinBuildHeight()-1,end.z),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,p));
                flight.target=ground.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?ground.getLocation()
                        :new Vec3(end.x,p.getY()-5,end.z);
            }
        }
        if(flight.diveTicks++>0&&grounded(p)&&p.isAlive()) {
            velocity(p,Vec3.ZERO);clear(p);impact(p,flight.config);return;
        }
        if(p.getY()<p.level().getMinBuildHeight()-64) {clear(p);return;}
        if(!flight.verticalDrop&&p.horizontalCollision&&flight.diveTicks>1)verticalDrop(flight);
        Vec3 offset=new Vec3(flight.target.x-p.getX(),0,flight.target.z-p.getZ());
        // Follow the slope to the selected landing point instead of hitting the ground early on long dives.
        double drop=Math.max(.1,p.getY()-flight.target.y);
        double travelTicks=AirAssaultRules.remainingDiveTicks(flight.config.diveSeconds(),flight.diveTicks);
        Vec3 forward=offset.scale(1/travelTicks);
        Vec3 motion=new Vec3(forward.x,-Math.max(flight.verticalDrop?1:.1,drop/travelTicks),forward.z);
        if(!flight.verticalDrop){
            // Use the player's full collision shape, including slabs and head-height obstructions.
            Vec3 allowed=net.minecraft.world.entity.Entity.collideBoundingBox(p,motion,p.getBoundingBox(),p.level(),java.util.List.of());
            if(Math.abs(allowed.x-motion.x)>1e-5||Math.abs(allowed.z-motion.z)>1e-5){
                verticalDrop(flight);
                double down=Math.max(1,(p.getY()-flight.target.y)/Math.max(1,ExtraGadgetConfig.ticks(flight.config.diveSeconds())));
                motion=new Vec3(0,-down,0);
            }
        }
        velocity(p,motion);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD,p.getX(),p.getY()+.8,p.getZ(),2,.15,.2,.15,.02);
    }

    private static void verticalDrop(Flight flight){
        var p=flight.player;flight.verticalDrop=true;flight.diveTicks=1;
        var ground=p.level().clip(new net.minecraft.world.level.ClipContext(p.position(),
                new Vec3(p.getX(),p.level().getMinBuildHeight()-1,p.getZ()),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        // Keep X/Z fixed and aim at the ground, not the collision point's current height.
        flight.target=new Vec3(p.getX(),ground.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?ground.getLocation().y:p.getY()-5,p.getZ());
    }

    private static boolean grounded(ServerPlayer p) {
        // Remote-player onGround is client supplied; require actual support under the feet.
        var box=p.getBoundingBox();
        return !p.level().noCollision(p,new AABB(box.minX+.01,box.minY-.08,box.minZ+.01,
                box.maxX-.01,box.minY+.001,box.maxZ-.01));
    }

    private static boolean enemy(ServerPlayer owner,LivingEntity target) {
        boolean combatant=CombatRules.target(target)||target instanceof net.minecraft.world.entity.monster.Enemy;
        if(target==owner||!target.isAlive()||target.isSpectator()||!combatant||SafeZones.contains(target)
                ||LobbyManager.respawning(target.getUUID())||FreeForAllMatch.blocked(target))return false;
        if(target instanceof Player p&&p.isCreative())return false;
        return owner.getTeam()==null||target.getTeam()==null||!owner.getTeam().getName().equals(target.getTeam().getName());
    }
    private static void impact(ServerPlayer p,ExtraGadgetConfig.AirAssault config) {
        Vec3 center=p.position();
        // Include the half-block height difference on either side of a slab surface.
        // Query entity bodies directly: slabs must not shield the area effect above them.
        double half=config.shockwaveSize()/2;boolean hit=false;
        var area=new AABB(center.x-half,center.y-2.5,center.z-half,center.x+half,center.y+4.5,center.z+half);
        for(var target:p.serverLevel().getEntitiesOfClass(LivingEntity.class,area,e->enemy(p,e))) {
            double dx=Math.abs(target.getX()-center.x),dz=Math.abs(target.getZ()-center.z);
            double damage=AirAssaultRules.damage(dx,dz,config);if(damage<0)continue;
            boolean damaged=target.hurt(p.damageSources().playerAttack(p),(float)damage);
            hit|=damaged;
            if(!target.isAlive())continue;
            var old=target.getDeltaMovement();
            target.setDeltaMovement(old.x,LAUNCH_VELOCITY,old.z);target.hasImpulse=true;target.hurtMarked=true;
            if(target instanceof ServerPlayer player)player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        if(hit&&config.hitSpeedSeconds()>0&&config.hitSpeedBonusPercent()>0){
            var attribute=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if(attribute!=null){attribute.removeModifier(SPEED_ID);attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SPEED_ID,"Air assault hit speed",config.hitSpeedBonusPercent()/100,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
                SPEED.put(p,p.level().getGameTime()+ExtraGadgetConfig.ticks(config.hitSpeedSeconds()));}
        }
        p.serverLevel().sendParticles(ParticleTypes.EXPLOSION,center.x,center.y+.15,center.z,1,0,0,0,0);
        var ground=p.serverLevel().getBlockState(net.minecraft.core.BlockPos.containing(center.add(0,-.2,0)));
        if(!ground.isAir())p.serverLevel().sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,ground),
                center.x,center.y+.2,center.z,70,1.6,.15,1.6,.22);
        SHOCKWAVES.add(new Shockwave(p.serverLevel(),center,p.level().getGameTime(),half));
        p.level().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.PLAYERS,1,.8f);
    }
    private static double surfaceY(net.minecraft.server.level.ServerLevel level,double x,double y,double z) {
        var hit=level.clip(new net.minecraft.world.level.ClipContext(new Vec3(x,y+1,z),new Vec3(x,y-1,z),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,null));
        return hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getLocation().y:y;
    }
    /** Initial impulse for a three-block apex under vanilla living-entity gravity and drag. */
    private static double launchVelocity() {
        double low=0,high=3;
        for(int i=0;i<32;i++) {
            double speed=(low+high)/2,height=0;
            for(double v=speed;v>0;v=(v-.08)*.98)height+=v;
            if(height<3)low=speed;else high=speed;
        }
        return (low+high)/2;
    }
    public static void clear(ServerPlayer p) {
        var flight=FLIGHTS.remove(p.getUUID());if(flight==null)return;
        flight.player.setNoGravity(flight.gravity);flight.player.fallDistance=0;
        AirAssaultMotion.receive(flight.player,false,Vec3.ZERO);
        GungangNetwork.send(flight.player,new GungangNetwork.AssaultMotion(false,0,0,0));
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void airborneDamage(net.minecraftforge.event.entity.living.LivingHurtEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;var flight=FLIGHTS.get(p.getUUID());
        if(flight!=null&&flight.player==p&&!grounded(p))event.setAmount((float)(event.getAmount()*(1-flight.config.airborneReductionPercent()/100)));
    }
    private static void clearSpeed(ServerPlayer p){SPEED.remove(p);var attribute=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(attribute!=null)attribute.removeModifier(SPEED_ID);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(event.getEntity() instanceof ServerPlayer p){clear(p);clearSpeed(p);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer p){clear(p);clearSpeed(p);}
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer p){clear(p);clearSpeed(p);}
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        for(var flight:FLIGHTS.values())flight.player.setNoGravity(flight.gravity);
        FLIGHTS.clear();SHOCKWAVES.clear();
        for(var p:java.util.List.copyOf(SPEED.keySet()))clearSpeed(p);
    }
    private AirAssault() { }
}
