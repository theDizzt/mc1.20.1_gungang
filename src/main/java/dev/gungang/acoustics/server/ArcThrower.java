package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 32 ARC-12 Thunderbolt ({@code zeta:arc12}). The gun fires several pellets per shot; a body they reach takes the shot's
 * damage once (no headshots), and an arc jumps from it to everyone within {@code arcRadius} blocks for arcDamage
 * (once per shot each). Everyone struck glows for glowSeconds ({@link ExtraGadgetConfig.ArcThrower}).
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class ArcThrower {
    public static final String GUN_ID="zeta:arc12";
    /** Bodies already struck by a shooter's current shot (shots are at least 0.6 s apart, so one tick is one shot). */
    private record Shot(UUID shooter,long tick) { }
    private static final Map<Shot,Set<UUID>> STRUCK=new HashMap<>();
    private static long cleared;

    public static boolean is(net.minecraft.world.item.ItemStack stack){var tag=stack.getTag();return tag!=null&&GUN_ID.equals(tag.getString("GunId"));}
    static boolean pellet(Entity direct){return direct instanceof Las5Damage.BulletAccess bullet&&GUN_ID.equals(String.valueOf(bullet.getGunId()));}
    private static Shot shot(Entity direct,LivingEntity victim){
        var owner=direct instanceof Projectile p?p.getOwner():null;
        long now=victim.level().getGameTime();
        if(now!=cleared){STRUCK.keySet().removeIf(s->s.tick()<now-1);cleared=now;}
        return new Shot(owner==null?direct.getUUID():owner.getUUID(),now);
    }
    /** Later pellets of the same shot (and TaCZ's armour-piercing second hit) do nothing to a body already struck. */
    @SubscribeEvent(priority=EventPriority.HIGH) public static void attack(LivingAttackEvent event){
        var victim=event.getEntity();var direct=event.getSource().getDirectEntity();
        if(victim.level().isClientSide||!pellet(direct))return;
        var struck=STRUCK.get(shot(direct,victim));
        if(struck!=null&&struck.contains(victim.getUUID()))event.setCanceled(true);
    }
    /** The shot's damage replaces the pellet's (headshot and distance multipliers included). */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hurt(LivingHurtEvent event){
        var victim=event.getEntity();var direct=event.getSource().getDirectEntity();
        if(victim.level().isClientSide||!pellet(direct))return;
        var struck=STRUCK.computeIfAbsent(shot(direct,victim),s->new HashSet<>());
        if(!struck.add(victim.getUUID())){event.setCanceled(true);return;}
        var owner=direct instanceof Projectile p?p.getOwner():null;
        event.setAmount((float)(ExtraGadgetConfig.arcThrower.damage()*AdrenalineDamage.multiplier(owner)));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void struck(LivingDamageEvent event){
        var victim=event.getEntity();var direct=event.getSource().getDirectEntity();
        if(event.isCanceled()||event.getAmount()<=0||victim.level().isClientSide||!pellet(direct)||!(victim.level() instanceof ServerLevel level))return;
        var config=ExtraGadgetConfig.arcThrower;
        glow(victim);
        ArcShock.apply(victim,direct instanceof Projectile projectile&&projectile.getOwner()!=null?projectile.getOwner().position():direct.position());
        var owner=direct instanceof Projectile p?p.getOwner():null;
        var struck=STRUCK.computeIfAbsent(shot(direct,victim),s->new HashSet<>());
        struck.add(victim.getUUID());
        double radius=config.arcRadius();
        var type=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.INDIRECT_MAGIC);
        float damage=(float)(config.arcDamage()*AdrenalineDamage.multiplier(owner));
        var from=victim.getBoundingBox().getCenter();
        for(var near:level.getEntitiesOfClass(LivingEntity.class,victim.getBoundingBox().inflate(radius),CombatRules::target)){
            if(near==victim||near==owner||!near.isAlive()||near.isSpectator()||struck.contains(near.getUUID()))continue;
            if(near.getBoundingBox().getCenter().distanceTo(from)>radius||SafeZones.contains(near))continue;
            if(owner instanceof Player p&&near instanceof Player other&&!p.canHarmPlayer(other))continue;
            struck.add(near.getUUID());
            near.invulnerableTime=0;
            // Keep kill credit with the shooter; the shock supplies its own small push.
            CombatRules.quietly(()->{if(near.hurt(new DamageSource(type,victim,owner),damage)&&near.isAlive()){glow(near);ArcShock.apply(near,victim.position());}});
            arc(level,from,near.getBoundingBox().getCenter());
        }
    }
    private static void glow(LivingEntity body){
        int ticks=ExtraGadgetConfig.ticks(ExtraGadgetConfig.arcThrower.glowSeconds());
        if(ticks>0)body.addEffect(new MobEffectInstance(MobEffects.GLOWING,ticks,0,false,false,true));
    }
    /** A short line of electric sparks from the struck body to the one the arc jumped to. */
    private static void arc(ServerLevel level,net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to){
        var step=to.subtract(from);int points=Math.max(2,(int)(step.length()*4));
        for(int i=0;i<=points;i++){
            var at=from.add(step.scale(i/(double)points));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,at.x,at.y,at.z,1,.05,.05,.05,0);
        }
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){STRUCK.clear();}
    private ArcThrower(){}
}
