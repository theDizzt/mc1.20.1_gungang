package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One multiplier after armor/resistance/absorption, before match damage/death accounting. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class AdrenalineDamage {
    public static double multiplier(net.minecraft.world.entity.Entity attacker) {
        return attacker instanceof ServerPlayer p&&p.isAlive()&&!p.isSpectator()&&GadgetManager.boostRemaining(p)>0
                ?1+GadgetConfig.adrenaline.damageBonusPercent()/100:1;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void raw(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if(event.getSource().is(DamageTypes.ON_FIRE)) {
            var burn=BURNS.get(event.getEntity());
            if(burn!=null&&event.getEntity().isOnFire()&&burn.until()>event.getEntity().level().getGameTime())event.setAmount((float)burn.damage());
        }
        var direct=event.getSource().getDirectEntity();
        if(direct==null||event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))return;
        double scale=direct.getPersistentData().getDouble("GunGangRawDamageMultiplier");
        // Restore unscaled input for armor/shield processing; final health damage is boosted below.
        if(scale>1)event.setAmount((float)(event.getAmount()/scale));
    }
    private record Burn(UUID owner,long until,double damage) { }
    private static final Map<LivingEntity,Burn> BURNS=new WeakHashMap<>();
    public static void burning(LivingEntity victim,UUID owner,int seconds,double damage) {
        BURNS.put(victim,new Burn(owner,victim.level().getGameTime()+seconds*20L,damage));
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void damage(LivingDamageEvent event) {
        var victim=event.getEntity();if(victim.level().isClientSide||event.getAmount()<=0)return;
        var attacker=event.getSource().getEntity();
        if(attacker==null&&event.getSource().getDirectEntity() instanceof Projectile projectile)attacker=projectile.getOwner();
        if(attacker==null&&event.getSource().is(DamageTypes.ON_FIRE)) {
            var burn=BURNS.get(victim);
            if(burn!=null&&burn.owner()!=null&&victim.isOnFire()&&burn.until()>victim.level().getGameTime())
                attacker=victim.getServer().getPlayerList().getPlayer(burn.owner());
            else BURNS.remove(victim);
        }
        if(attacker instanceof ServerPlayer player&&player!=victim&&player.isAlive()&&!player.isSpectator()
                &&GadgetManager.boostRemaining(player)>0)
            event.setAmount((float)(event.getAmount()*(1+GadgetConfig.adrenaline.damageBonusPercent()/100)));
    }
    private AdrenalineDamage() { }
}
