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
        Vec3 target;
        int diveTicks;
        Flight(ServerPlayer p) {
            player=p;level=p.serverLevel();gravity=p.isNoGravity();
            diveAt=level.getGameTime()+40;hoverY=p.getY()+5;
        }
    }
    private static final Map<UUID,Flight> FLIGHTS=new HashMap<>();
    private static final double LAUNCH_VELOCITY=launchVelocity();
    private record Shockwave(net.minecraft.server.level.ServerLevel level,Vec3 center,long started) { }
    private static final java.util.List<Shockwave> SHOCKWAVES=new java.util.ArrayList<>();
    @SubscribeEvent public static void effects(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        SHOCKWAVES.removeIf(wave->wave.level().getGameTime()-wave.started()>10);
        for(var wave:SHOCKWAVES) {
            long age=wave.level().getGameTime()-wave.started();
            if(age%2!=0)continue;
            double radius=.5+age*.45;
            for(int i=0;i<48;i++) {
                double angle=2*Math.PI*i/48;
                double x=wave.center().x+Math.cos(angle)*radius,z=wave.center().z+Math.sin(angle)*radius;
                wave.level().sendParticles(ParticleTypes.CLOUD,x,wave.center().y+.15,z,1,.04,.02,.04,.015);
                if(age<=4&&i%3==0)wave.level().sendParticles(ParticleTypes.CRIT,x,wave.center().y+.25,z,1,.05,.1,.05,.08);
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
        // Capture the latest facing once, after the freely aimed two-second window.
        if(flight.target==null) {
            // Preserve the look vector's horizontal component: looking down shortens the dive,
            // looking level reaches the full seven blocks; looking straight down stays in place.
            Vec3 look=p.getLookAngle();
            flight.target=p.position().add(look.x*7,0,look.z*7);
        }
        if(flight.diveTicks++>0&&grounded(p)&&p.isAlive()) {
            velocity(p,Vec3.ZERO);clear(p);impact(p);return;
        }
        if(p.getY()<p.level().getMinBuildHeight()-64) {clear(p);return;}
        // Stop horizontal drive at a wall rather than sliding toward the target indefinitely.
        if(p.horizontalCollision&&flight.diveTicks>1)flight.target=p.position();
        Vec3 offset=new Vec3(flight.target.x-p.getX(),0,flight.target.z-p.getZ());
        Vec3 forward=offset.length()>1.4?offset.normalize().scale(1.4):offset;
        velocity(p,new Vec3(forward.x,-1,forward.z));
        p.serverLevel().sendParticles(ParticleTypes.CLOUD,p.getX(),p.getY()+.8,p.getZ(),2,.15,.2,.15,.02);
    }

    private static boolean grounded(ServerPlayer p) {
        // Remote-player onGround is client supplied; require actual support under the feet.
        var box=p.getBoundingBox();
        return !p.level().noCollision(p,new AABB(box.minX+.01,box.minY-.08,box.minZ+.01,
                box.maxX-.01,box.minY+.001,box.maxZ-.01));
    }

    private static boolean enemy(ServerPlayer owner,LivingEntity target) {
        boolean combatant=BotEntity.participant(target)||target instanceof net.minecraft.world.entity.monster.Enemy;
        if(target==owner||!target.isAlive()||target.isSpectator()||!combatant||SafeZones.contains(target)
                ||LobbyManager.respawning(target.getUUID())||FreeForAllMatch.blocked(target))return false;
        if(target instanceof Player p&&p.isCreative())return false;
        return owner.getTeam()==null||target.getTeam()==null||!owner.getTeam().getName().equals(target.getTeam().getName());
    }
    private static void impact(ServerPlayer p) {
        Vec3 center=p.position();
        var area=new AABB(center.x-5,center.y-2,center.z-5,center.x+5,center.y+4,center.z+5);
        for(var target:p.serverLevel().getEntitiesOfClass(LivingEntity.class,area,e->enemy(p,e))) {
            double dx=Math.abs(target.getX()-center.x),dz=Math.abs(target.getZ()-center.z);
            if(dx>5||dz>5)continue;
            if(dx<=2&&dz<=2)target.hurt(p.damageSources().playerAttack(p),60);
            if(!target.isAlive())continue;
            var old=target.getDeltaMovement();
            target.setDeltaMovement(old.x,LAUNCH_VELOCITY,old.z);target.hasImpulse=true;target.hurtMarked=true;
            if(target instanceof ServerPlayer player)player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        p.serverLevel().sendParticles(ParticleTypes.EXPLOSION,center.x,center.y+.15,center.z,1,0,0,0,0);
        var ground=p.serverLevel().getBlockState(net.minecraft.core.BlockPos.containing(center.add(0,-.2,0)));
        if(!ground.isAir())p.serverLevel().sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,ground),
                center.x,center.y+.2,center.z,70,1.6,.15,1.6,.22);
        SHOCKWAVES.add(new Shockwave(p.serverLevel(),center,p.level().getGameTime()));
        p.level().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.PLAYERS,1,.8f);
    }
    /** Initial impulse for a ten-block apex under vanilla living-entity gravity and drag. */
    private static double launchVelocity() {
        double low=0,high=3;
        for(int i=0;i<32;i++) {
            double speed=(low+high)/2,height=0;
            for(double v=speed;v>0;v=(v-.08)*.98)height+=v;
            if(height<10)low=speed;else high=speed;
        }
        return (low+high)/2;
    }
    public static void clear(ServerPlayer p) {
        var flight=FLIGHTS.remove(p.getUUID());if(flight==null)return;
        flight.player.setNoGravity(flight.gravity);flight.player.fallDistance=0;
        AirAssaultMotion.receive(flight.player,false,Vec3.ZERO);
        GungangNetwork.send(flight.player,new GungangNetwork.AssaultMotion(false,0,0,0));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(event.getEntity() instanceof ServerPlayer p)clear(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer p)clear(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer p)clear(p);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        for(var flight:FLIGHTS.values())flight.player.setNoGravity(flight.gravity);
        FLIGHTS.clear();SHOCKWAVES.clear();
    }
    private AirAssault() { }
}
