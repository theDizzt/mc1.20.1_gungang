package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Combat rules that override vanilla and other mods:
 * <ul>
 * <li>no jump (falling) critical hits;</li>
 * <li>hits from an attacker always knock players and bots back. Vanilla does this inside {@code hurt}; when another
 * listener cancels that knockback, or it never happens, the expected push is applied here at the end of the tick and
 * the cause is logged once. GunGang's own exceptions (raised shield, turret bullets, damage-over-time) still apply,
 * and a TaCZ bullet pushes by its gun's {@code knockback} value as TaCZ intends (0: no push).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class CombatRules {
    /** Vanilla's knockback strength for a hit. */
    static final double HIT_STRENGTH=.4;
    private record Expected(Vec3 from,double strength,String cause) { }
    /** Victims hit this tick whose knockback has not been seen yet. */
    private static final Map<LivingEntity,Expected> PENDING=new WeakHashMap<>();
    private static final ThreadLocal<Integer> QUIET=ThreadLocal.withInitial(()->0);
    private static final Set<String> REPORTED=new HashSet<>();
    private static java.lang.reflect.Field bulletKnockback;
    private static boolean bulletKnockbackMissing;

    /** Damage dealt inside {@code work} (bleeding, fallout ...) pushes nobody. */
    /**
     * A Custom NPCs entity our own weapons may hit, like a vanilla mob. Prop hunt keeps map NPCs inert (bullets pass
     * through them), so they are never targets there. Their own faction rules still decide inside {@code hurt}.
     */
    public static boolean npcTarget(net.minecraft.world.entity.Entity entity){
        return entity instanceof net.minecraft.world.entity.LivingEntity living&&living.isAlive()&&!living.isSpectator()
                &&PropHuntMatch.npc(entity)&&MatchManager.propHunt()==null;
    }
    /** A player or bot in play, or a Custom NPC ({@link #npcTarget}). */
    public static boolean target(net.minecraft.world.entity.Entity entity){return BotEntity.participant(entity)||npcTarget(entity);}
    public static void quietly(Runnable work){
        QUIET.set(QUIET.get()+1);
        try{work.run();}finally{QUIET.set(QUIET.get()-1);}
    }
    private static boolean quiet(){return QUIET.get()>0;}

    @SubscribeEvent public static void critical(CriticalHitEvent event){
        if(event.isVanillaCritical()){event.setResult(Event.Result.DENY);event.setDamageModifier(1);}
    }

    private static boolean guarded(LivingEntity victim){return victim instanceof Player||victim instanceof BotEntity;}
    /** The push this hit should give: a TaCZ bullet's own knockback, otherwise vanilla's. */
    private static double strength(Entity direct){
        if(!(direct instanceof Las5Damage.BulletAccess))return HIT_STRENGTH;
        if(bulletKnockbackMissing)return HIT_STRENGTH;
        try{
            if(bulletKnockback==null){bulletKnockback=Class.forName("com.tacz.guns.entity.EntityKineticBullet").getDeclaredField("knockback");bulletKnockback.setAccessible(true);}
            return Math.max(0,bulletKnockback.getFloat(direct));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){bulletKnockbackMissing=true;return HIT_STRENGTH;}
    }
    /** Guns whose bullets also knock teammates back (friendly fire blocks the damage, and with it vanilla's push). */
    private static final Set<String> ALLY_KNOCKBACK_GUNS=Set.of("zeta:sg225sp");
    /**
     * Guns that push whoever they hit, teammates and enemies alike, whatever their damage (the 35 classic blower deals
     * none, so no hit would push). Their pellets push a target at most once per tick; any other push of that target in
     * the same tick (its own damage hit) is dropped, so a strength of 1 means 1 whichever way the hit lands.
     */
    private static final Set<String> PUSH_GUNS=Set.of("bf1:wex");
    /** Victims pushed by a {@link #PUSH_GUNS} bullet, by the game time of that push. */
    private static final Map<LivingEntity,Long> PUSHED=new WeakHashMap<>();
    private static boolean pushing;
    private static boolean pushedNow(LivingEntity victim){return PUSHED.getOrDefault(victim,Long.MIN_VALUE)==victim.level().getGameTime();}
    /**
     * A knockback gun's bullet hitting someone, before TaCZ hurts them: pushes them by the bullet's knockback, away from
     * the shooter, where the hit itself would not ({@link #ALLY_KNOCKBACK_GUNS}: teammates behind friendly fire;
     * {@link #PUSH_GUNS}: everyone).
     */
    public static void gunKnockback(net.minecraft.world.entity.projectile.Projectile bullet,Entity target){
        if(bullet.level().isClientSide||!(bullet instanceof Las5Damage.BulletAccess access))return;
        String gun=String.valueOf(access.getGunId());
        boolean everyone=PUSH_GUNS.contains(gun);
        if(!everyone&&!ALLY_KNOCKBACK_GUNS.contains(gun))return;
        if(!(bullet.getOwner() instanceof LivingEntity owner)||!(target instanceof LivingEntity victim)||victim==owner
                ||!BotEntity.participant(victim)||LobbyManager.respawning(victim.getUUID())||SafeZones.contains(victim))return;
        if(!everyone){
            var team=owner.getTeam();
            if(team==null||team.isAllowFriendlyFire()||!owner.isAlliedTo(victim))return;
        }
        if(victim instanceof ServerPlayer p&&CombatGadgets.shieldBlocked(p))return;
        if(everyone&&pushedNow(victim))return;
        double strength=strength(bullet);
        if(strength<=0)return;
        if(everyone)PUSHED.put(victim,victim.level().getGameTime());
        pushing=true;
        try{victim.knockback(strength,owner.getX()-victim.getX(),owner.getZ()-victim.getZ());}
        finally{pushing=false;}
        if(!(victim instanceof ServerPlayer))victim.hurtMarked=true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hit(LivingDamageEvent event){
        var victim=event.getEntity();
        if(event.isCanceled()||event.getAmount()<=0||victim.level().isClientSide||quiet()||!guarded(victim))return;
        var source=event.getSource();var attacker=source.getEntity();
        if(attacker==null||attacker==victim||source.is(DamageTypeTags.IS_EXPLOSION))return;
        double strength=strength(source.getDirectEntity());
        if(strength<=0)return;
        PENDING.put(victim,new Expected(attacker.position(),strength,source.getMsgId()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void knockback(LivingKnockBackEvent event){
        var victim=event.getEntity();
        if(victim.level().isClientSide)return;
        if(quiet()){event.setCanceled(true);PENDING.remove(victim);return;}
        // A push gun already pushed this victim this tick: drop the extra push of its own damage hit.
        if(!pushing&&pushedNow(victim)){event.setCanceled(true);PENDING.remove(victim);return;}
        if(pushing)return;
        var expected=PENDING.remove(victim);
        if(expected==null)return;
        boolean ours=victim instanceof ServerPlayer p&&CombatGadgets.shieldBlocked(p)||Turrets.bulletVictim(victim);
        if(ours)return;
        if(event.isCanceled()){
            event.setCanceled(false);
            report("cancelled","LivingKnockBackEvent was cancelled by another listener (damage "+expected.cause()+"); GunGang restored it.");
        }
        if(event.getStrength()<expected.strength()*.5){
            report("weakened","LivingKnockBackEvent strength was lowered to "+event.getStrength()+" by another listener (damage "+expected.cause()+"); GunGang restored "+expected.strength()+".");
            event.setStrength((float)expected.strength());
        }
    }
    /** Hits whose knockback never reached the event (another mod skipped it): push here, the way vanilla would. */
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||PENDING.isEmpty())return;
        var due=new ArrayList<>(PENDING.entrySet());PENDING.clear();
        for(var entry:due){
            var victim=entry.getKey();var expected=entry.getValue();
            if(!victim.isAlive()||victim.isRemoved()||victim.isSpectator())continue;
            if(victim instanceof ServerPlayer p&&CombatGadgets.shieldBlocked(p))continue;
            double strength=expected.strength()*(1-victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            if(strength<=0)continue;
            double x=expected.from().x-victim.getX(),z=expected.from().z-victim.getZ();
            if(x*x+z*z<1e-4){x=(Math.random()-Math.random())*.01;z=(Math.random()-Math.random())*.01;}
            var push=new Vec3(x,0,z).normalize().scale(strength);var old=victim.getDeltaMovement();
            var motion=new Vec3(old.x/2-push.x,victim.onGround()?Math.min(.4,old.y/2+strength):old.y,old.z/2-push.z);
            report("skipped","Knockback for damage "+expected.cause()+" never reached LivingKnockBackEvent; GunGang applied it.");
            if(victim instanceof ServerPlayer player)PlayerImpulse.apply(player,motion);
            else{victim.setDeltaMovement(motion);victim.hasImpulse=true;victim.hurtMarked=true;}
        }
    }
    private static void report(String kind,String message){
        if(REPORTED.add(kind))com.mojang.logging.LogUtils.getLogger().warn("GunGang knockback: {}",message);
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){PENDING.clear();}
    private CombatRules(){}
}
