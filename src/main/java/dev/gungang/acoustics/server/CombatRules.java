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
    /**
     * The push this hit should give: a TaCZ bullet's own knockback, otherwise vanilla's. TaCZ copies the bullet's
     * knockback from the shooter's attachment cache, which it only recomputes on draw, refit and fire-mode change; a
     * stale cache (another gun's, or one from before a pack reload) gives 0, and TaCZ then sets every hit's push to 0.
     * A 0 there falls back to the gun data's own knockback, so a knockback gun always pushes.
     */
    private static double strength(Entity direct){
        if(ArcThrower.pellet(direct))return 0;
        if(!(direct instanceof Las5Damage.BulletAccess access))return HIT_STRENGTH;
        if(bulletKnockbackMissing)return HIT_STRENGTH;
        float cached;
        try{
            if(bulletKnockback==null){bulletKnockback=Class.forName("com.tacz.guns.entity.EntityKineticBullet").getDeclaredField("knockback");bulletKnockback.setAccessible(true);}
            cached=bulletKnockback.getFloat(direct);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){bulletKnockbackMissing=true;return HIT_STRENGTH;}
        if(cached>0)return cached;
        double configured=dataKnockback(access.getGunId());
        if(configured>0){
            report("stale","A "+access.getGunId()+" bullet carried knockback "+cached+" from a stale TaCZ attachment cache; GunGang used the gun data's "+configured+".");
            if(direct instanceof net.minecraft.world.entity.projectile.Projectile bullet&&bullet.getOwner() instanceof LivingEntity shooter)refreshGunCache(shooter);
        }
        return configured;
    }
    private static java.lang.reflect.Method postChange;
    private static boolean postChangeMissing;
    /**
     * Has TaCZ recompute the shooter's attachment cache (knockback, armor pierce, headshot ...) for the gun in their
     * main hand, as a draw does. Needed after GunGang swaps the held gun, which the client may not answer with a draw.
     */
    public static void refreshGunCache(LivingEntity shooter){
        if(shooter.level().isClientSide||postChangeMissing)return;
        try{
            if(postChange==null)postChange=Class.forName("com.tacz.guns.resource.modifier.AttachmentPropertyManager").getMethod("postChangeEvent",LivingEntity.class,net.minecraft.world.item.ItemStack.class);
            postChange.invoke(null,shooter,shooter.getMainHandItem());
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){postChangeMissing=true;}
    }
    private static java.lang.reflect.Method gunIndex,bulletData,dataKnockback;
    private static boolean gunDataMissing;
    /** The {@code bullet.knockback} in the gun's TaCZ data, 0 when unknown. */
    private static double dataKnockback(net.minecraft.resources.ResourceLocation gun){
        if(gun==null||gunDataMissing)return 0;
        try{
            if(gunIndex==null){
                gunIndex=Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class);
                bulletData=Class.forName("com.tacz.guns.resource.index.CommonGunIndex").getMethod("getBulletData");
                dataKnockback=Class.forName("com.tacz.guns.resource.pojo.data.gun.BulletData").getMethod("getKnockback");
            }
            var index=(Optional<?>)gunIndex.invoke(null,gun);
            if(index.isEmpty())return 0;
            return Math.max(0,((Number)dataKnockback.invoke(bulletData.invoke(index.get()))).doubleValue());
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){gunDataMissing=true;return 0;}
    }
    /**
     * Knockback guns (the Spray N' Pray knockback shotgun): every pellet pushes whoever it hits, teammates and enemies
     * alike. GunGang applies the push itself after entity ticking ({@link #tick}), so damage handlers, AI, TaCZ's own
     * knockback cache and other mods cannot cancel or overwrite it; the hit's own vanilla knockback is dropped.
     */
    private static final Set<String> KNOCKBACK_GUNS=Set.of("zeta:sg225sp");
    public static boolean knockbackGun(Object bullet){
        return bullet instanceof Las5Damage.BulletAccess access&&KNOCKBACK_GUNS.contains(String.valueOf(access.getGunId()));
    }
    /** Victims pushed by a knockback gun pellet, by the game time of that push. */
    private static final Map<LivingEntity,Long> PUSHED=new WeakHashMap<>();
    private record Shove(Vec3 from,double strength) {}
    /** Pellet pushes due at the end of this tick, in hit order. */
    private static final Map<LivingEntity,List<Shove>> SHOVES=new WeakHashMap<>();
    /** Pellet hits already counted this tick: TaCZ and TaCZ Tweaks may both report one hit. */
    private static final Set<Long> COUNTED=new HashSet<>();
    private static boolean pushedNow(LivingEntity victim){return PUSHED.getOrDefault(victim,Long.MIN_VALUE)==victim.level().getGameTime();}
    /** A knockback gun pellet hitting someone, before TaCZ hurts them: queues its push away from the shooter. */
    public static void gunKnockback(net.minecraft.world.entity.projectile.Projectile bullet,Entity target){
        if(bullet.level().isClientSide||!knockbackGun(bullet))return;
        if(!(bullet.getOwner() instanceof LivingEntity owner)||!(target instanceof LivingEntity victim)||victim==owner
                ||!victim.isAlive()||victim.isSpectator()||!target(victim)||LobbyManager.respawning(victim.getUUID())||SafeZones.contains(victim))return;
        if(!COUNTED.add(((long)bullet.getId()<<32)|(victim.getId()&0xffffffffL)))return;
        if(victim instanceof ServerPlayer p&&CombatGadgets.shieldBlocked(p))return;
        double strength=strength(bullet);
        if(strength<=0)return;
        // The knockback shotgun owns the fall its hit causes (kill and damage credit).
        if(!owner.isAlliedTo(victim))DamageCredit.wind(victim,owner);
        PUSHED.put(victim,victim.level().getGameTime());
        SHOVES.computeIfAbsent(victim,v->new ArrayList<>()).add(new Shove(owner.position(),strength));
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
        if(quiet()||ArcShock.justHit(victim)){event.setCanceled(true);PENDING.remove(victim);return;}
        // A knockback gun pellet pushes this victim at the end of the tick: drop its damage hit's own push.
        if(pushedNow(victim)){event.setCanceled(true);PENDING.remove(victim);return;}
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
    /**
     * Knockback gun pellets push here, one vanilla knockback per pellet in hit order (old motion halved, then the push
     * added, a hop when on the ground). Hits whose knockback never reached the event (another mod skipped it) are
     * pushed here too, the way vanilla would.
     */
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        COUNTED.clear();
        if(!SHOVES.isEmpty()){
            var shoves=new ArrayList<>(SHOVES.entrySet());SHOVES.clear();
            for(var entry:shoves){
                var victim=entry.getKey();
                if(!victim.isAlive()||victim.isRemoved()||victim.isSpectator()||LobbyManager.respawning(victim.getUUID())||SafeZones.contains(victim))continue;
                double resistance=1-victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                var motion=victim.getDeltaMovement();boolean ground=victim.onGround();boolean moved=false;
                for(var shove:entry.getValue()){
                    double strength=shove.strength()*resistance;
                    if(strength<=0)continue;
                    double x=shove.from().x-victim.getX(),z=shove.from().z-victim.getZ();
                    if(x*x+z*z<1e-4){x=(Math.random()-Math.random())*.01;z=(Math.random()-Math.random())*.01;}
                    var push=new Vec3(x,0,z).normalize().scale(strength);
                    motion=new Vec3(motion.x/2-push.x,ground?Math.min(.4,motion.y/2+strength):motion.y,motion.z/2-push.z);
                    moved=true;
                }
                if(!moved)continue;
                if(victim instanceof ServerPlayer player)PlayerImpulse.apply(player,motion);
                else{victim.setDeltaMovement(motion);victim.hasImpulse=true;victim.hurtMarked=true;}
            }
        }
        if(PENDING.isEmpty())return;
        var due=new ArrayList<>(PENDING.entrySet());PENDING.clear();
        for(var entry:due){
            var victim=entry.getKey();var expected=entry.getValue();
            if(!victim.isAlive()||victim.isRemoved()||victim.isSpectator()||pushedNow(victim))continue;
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
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){PENDING.clear();SHOVES.clear();COUNTED.clear();PUSHED.clear();}
    private CombatRules(){}
}
