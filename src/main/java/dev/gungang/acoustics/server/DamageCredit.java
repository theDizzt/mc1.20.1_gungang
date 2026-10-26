package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/**
 * Who to credit for a hit. Burning, wither and poison ticks carry no attacker, so damage from flamethrowers,
 * molotovs, jet flame and the blowpipe would otherwise be missing from match statistics.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class DamageCredit {
    /** Fire a gun lit has no recorded owner; credit the last enemy hit while it still burns. */
    static final int FIRE_WINDOW=200;
    private record Hit(UUID attacker,long time) { }
    private record Burn(UUID owner,long until) { }
    private record Credit(UUID owner,long added) { }
    private static final Map<LivingEntity,Hit> LAST=new WeakHashMap<>();
    private static final Map<LivingEntity,Burn> FIRE=new WeakHashMap<>();
    private static final Map<LivingEntity,Map<MobEffect,Credit>> EFFECTS=new WeakHashMap<>();

    private static boolean combatant(Entity entity){return entity instanceof ServerPlayer||BotManager.owns(entity);}
    private static Entity direct(DamageSource source){
        var attacker=source.getEntity();
        if(attacker==null&&source.getDirectEntity() instanceof Projectile projectile)attacker=projectile.getOwner();
        return attacker;
    }
    /** The attacker to credit, or null for damage nobody caused (map hazards, falls, own fire). */
    public static Entity attacker(LivingEntity victim,DamageSource source){
        var attacker=direct(source);if(attacker!=null)return attacker;
        if(!(victim.level() instanceof ServerLevel level))return null;
        long now=level.getGameTime();UUID owner=null;
        if(source.is(DamageTypes.ON_FIRE)&&victim.isOnFire()) {
            var burn=FIRE.get(victim);var hit=LAST.get(victim);
            if(burn!=null&&burn.until()>now)owner=burn.owner();
            else if(hit!=null&&now-hit.time()<=FIRE_WINDOW)owner=hit.attacker();
        }
        else if(source.is(DamageTypes.WITHER))owner=effectOwner(victim,MobEffects.WITHER);
        else if(source.is(DamageTypes.MAGIC)||source.is(DamageTypes.INDIRECT_MAGIC))owner=effectOwner(victim,MobEffects.POISON);
        if(owner==null||owner.equals(victim.getUUID()))return null;
        var player=level.getServer().getPlayerList().getPlayer(owner);
        return player!=null?player:level.getEntity(owner);
    }
    private static UUID effectOwner(LivingEntity victim,MobEffect effect){
        if(!victim.hasEffect(effect))return null;
        var credits=EFFECTS.get(victim);var credit=credits==null?null:credits.get(effect);
        return credit==null?null:credit.owner();
    }
    /** Molotov fields and jet flame know their owner; gun fire does not and falls back to the last hit. */
    static void burning(LivingEntity victim,UUID owner,int seconds) {
        if(owner!=null)FIRE.put(victim,new Burn(owner,victim.level().getGameTime()+seconds*20L));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hit(LivingDamageEvent event) {
        var victim=event.getEntity();if(victim.level().isClientSide)return;
        var attacker=direct(event.getSource());
        if(attacker==null||attacker==victim||!combatant(attacker))return;
        long now=victim.level().getGameTime();LAST.put(victim,new Hit(attacker.getUUID(),now));
        // A gun may apply its effect just before its damage lands; give that same-tick effect to this shooter.
        var credits=EFFECTS.get(victim);
        if(credits!=null)credits.replaceAll((effect,credit)->credit.owner()==null&&credit.added()==now?new Credit(attacker.getUUID(),now):credit);
    }
    @SubscribeEvent public static void effect(MobEffectEvent.Added event) {
        var victim=event.getEntity();if(victim.level().isClientSide)return;
        long now=victim.level().getGameTime();UUID owner=null;
        if(combatant(event.getEffectSource())&&event.getEffectSource()!=victim)owner=event.getEffectSource().getUUID();
        else {var hit=LAST.get(victim);if(hit!=null&&now-hit.time()<=1)owner=hit.attacker();}
        // Unattributed effects (acid hazards) overwrite older credit so they never count for a past shooter.
        EFFECTS.computeIfAbsent(victim,v->new HashMap<>()).put(event.getEffectInstance().getEffect(),new Credit(owner,now));
    }
    private DamageCredit(){}
}
