package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class SurvivalGadgets {
    public record View(UUID player,long lifeEnd,long jetEnd,int color) { }
    private record Life(long end,long nextDrain,DamageSource source) { }
    private record Jet(long end,Vec3 previous,boolean originalNoGravity,boolean verticalFlight) { }
    private record Trail(ServerLevel level,UUID owner,Vec3 position,long end) { }
    private static final Map<UUID,Life> LIFE=new HashMap<>();
    private static final Map<UUID,Jet> JETS=new HashMap<>();
    private static final List<Trail> TRAILS=new ArrayList<>();
    private static final Map<UUID,View> CLIENT=new HashMap<>();
    public static void receive(View state) {if(state.lifeEnd()==0&&state.jetEnd()==0)CLIENT.remove(state.player());else CLIENT.put(state.player(),state);}
    public static View clientState(Player p) {return CLIENT.get(p.getUUID());}
    public static boolean needsRecovery(ServerPlayer p) {return LIFE.containsKey(p.getUUID())&&!SurvivalRules.recovered(p.getHealth());}
    public static void clearClient() {CLIENT.clear();}
    public static boolean jetting(LivingEntity entity) {
        if(!(entity instanceof Player p))return false;
        if(!p.level().isClientSide)return JETS.containsKey(p.getUUID());
        var state=CLIENT.get(p.getUUID());return state!=null&&state.jetEnd()>p.level().getGameTime();
    }
    private static void sync(ServerPlayer p) {
        var life=LIFE.get(p.getUUID());var jet=JETS.get(p.getUUID());
        var team=p.getTeam();Integer fallback=team==null?null:team.getColor().getColor();
        int color=team==null?0xFFFFFF:team.getName().equals("Red")?LobbyManager.options().color("ALPHA"):team.getName().equals("Blue")?LobbyManager.options().color("BRAVO"):fallback==null?0xFFFFFF:fallback;
        var packet=new GungangNetwork.Ability(new View(p.getUUID(),life==null?0:life.end(),jet==null?0:jet.end(),color));
        for(var viewer:p.server.getPlayerList().getPlayers())if(viewer.level()==p.level())GungangNetwork.send(viewer,packet);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if(event.getTarget() instanceof ServerPlayer p)sync(p);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(LivingDeathEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||p.isSpectator()||LIFE.containsKey(p.getUUID())||event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL))return;
        var stack=p.getOffhandItem();
        if(!CombatGadgets.is(stack,"life_support")||!SecondaryRuntime.ready(p,stack))return;
        event.setCanceled(true);p.setHealth(SurvivalRules.LIFE_HEALTH);p.setAbsorptionAmount(SurvivalRules.LIFE_ABSORPTION);
        long now=p.level().getGameTime();LIFE.put(p.getUUID(),new Life(now+SurvivalRules.LIFE_TICKS,now+2,event.getSource()));
        SecondaryRuntime.commit(p,stack);p.clearFire();sync(p);
        p.level().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.TOTEM_USE,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.3f);
    }
    public static boolean startJet(ServerPlayer p,ItemStack stack) {
        if(jetting(p)||p.isPassenger()||p.isFallFlying()||SafeZones.contains(p))return false;
        p.stopUsingItem();p.setSprinting(false);
        JETS.put(p.getUUID(),new Jet(p.level().getGameTime()+SurvivalRules.JET_TICKS,p.position(),p.isNoGravity(),
                stack.hasTag()&&stack.getTag().getBoolean("GunGangJetpackVerticalFlight")));
        p.setNoGravity(true);p.fallDistance=0;
        SecondaryRuntime.commit(p,stack);sync(p);return true;
    }
    private static void velocity(ServerPlayer p,Vec3 velocity) {
        p.setDeltaMovement(velocity);p.hasImpulse=true;
        if(p.connection!=null)p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
    }
    private static void stopJet(ServerPlayer p) {
        var jet=JETS.remove(p.getUUID());if(jet==null)return;
        p.setNoGravity(jet.originalNoGravity());p.fallDistance=0;
        velocity(p,Vec3.ZERO);
        long end=p.level().getGameTime()+SurvivalRules.TRAIL_TICKS;
        TRAILS.replaceAll(t->t.owner().equals(p.getUUID())?new Trail(t.level(),t.owner(),t.position(),Math.min(t.end(),end)):t);
        sync(p);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void heal(LivingHealEvent event) {if(jetting(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(LivingEntityUseItemEvent.Start event) {if(jetting(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        var jet=JETS.get(event.getEntity().getUUID());
        if(jet!=null)event.getEntity().setDeltaMovement(jetDirection(event.getEntity(),jet.verticalFlight()).scale(SurvivalRules.JET_SPEED));
    }
    private static Vec3 jetDirection(LivingEntity entity,boolean verticalFlight) {
        return verticalFlight?entity.getLookAngle().normalize():Vec3.directionFromRotation(0,entity.getYRot()).normalize();
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        long now=p.level().getGameTime();var life=LIFE.get(p.getUUID());
        if(life!=null) {
            if(!p.isAlive()||p.isSpectator()||LobbyManager.respawning(p.getUUID())) {LIFE.remove(p.getUUID());p.setAbsorptionAmount(0);sync(p);}
            else {
                if(now>=life.nextDrain()) {
                    p.setAbsorptionAmount(SurvivalRules.drain(p.getAbsorptionAmount()));
                    LIFE.put(p.getUUID(),new Life(life.end(),now+2,life.source()));
                }
                if(now>=life.end()||p.getAbsorptionAmount()<=0) {
                    // Keep LIFE present while die() dispatches events, so the same device cannot revive twice.
                    p.setAbsorptionAmount(0);
                    if(!SurvivalRules.recovered(p.getHealth())) {p.setHealth(0);p.die(life.source());}
                    LIFE.remove(p.getUUID());sync(p);
                }
            }
        }
        var jet=JETS.get(p.getUUID());if(jet==null)return;
        if(now>=jet.end()||!p.isAlive()||p.isSpectator()||p.isPassenger()||SafeZones.contains(p)) {stopJet(p);return;}
        p.setNoGravity(true);p.fallDistance=0;
        Vec3 direction=jetDirection(p,jet.verticalFlight()),motion=direction.scale(SurvivalRules.JET_SPEED);
        var swept=p.getBoundingBox().expandTowards(jet.previous().subtract(p.position())).expandTowards(motion).inflate(.1);
        var hit=p.serverLevel().getEntitiesOfClass(ServerPlayer.class,swept,q->q!=p&&q.isAlive()&&!q.isSpectator()&&!SafeZones.contains(q)).stream()
                .min(Comparator.comparingDouble(q->q.position().distanceToSqr(jet.previous()))).orElse(null);
        if(hit!=null) {
            OrdnanceDamage.hurt(hit,p.damageSources().playerAttack(p),SurvivalRules.JET_DAMAGE);
            Vec3 knockback=direction.horizontalDistanceSqr()<1e-6?Vec3.directionFromRotation(0,p.getYRot()):direction;
            hit.knockback(1.5,-knockback.x,-knockback.z);velocity(hit,hit.getDeltaMovement());stopJet(p);return;
        }
        JETS.put(p.getUUID(),new Jet(jet.end(),p.position(),jet.originalNoGravity(),jet.verticalFlight()));
        TRAILS.add(new Trail(p.serverLevel(),p.getUUID(),p.position().subtract(direction.scale(.4)),jet.end()+SurvivalRules.TRAIL_TICKS));
        velocity(p,motion);
        if(now%6==0)p.level().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,net.minecraft.sounds.SoundSource.PLAYERS,.5f,1.4f);
    }
    @SubscribeEvent public static void trails(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        TRAILS.removeIf(t->t.end()<=t.level().getGameTime());
        for(var trail:TRAILS) {
            var pos=trail.position();var level=trail.level();
            if(level.getGameTime()%4==0)level.sendParticles(ParticleTypes.FLAME,pos.x,pos.y+.3,pos.z,2,.2,.15,.2,.015);
            for(var victim:level.getEntitiesOfClass(ServerPlayer.class,new AABB(pos.x-.65,pos.y,pos.z-.65,pos.x+.65,pos.y+1,pos.z+.65),
                    p->!p.getUUID().equals(trail.owner())&&p.isAlive()&&!p.isSpectator()&&!SafeZones.contains(p)))victim.setSecondsOnFire(3);
        }
    }
    public static void clear(ServerPlayer p) {if(LIFE.remove(p.getUUID())!=null)p.setAbsorptionAmount(0);stopJet(p);sync(p);}
    public static void reset(net.minecraft.server.MinecraftServer server) {for(var p:server.getPlayerList().getPlayers())clear(p);LIFE.clear();JETS.clear();TRAILS.clear();}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {if(event.getEntity() instanceof ServerPlayer p)clear(p);}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event) {LIFE.clear();JETS.clear();TRAILS.clear();}
    private SurvivalGadgets() { }
}
