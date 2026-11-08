package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 29 katana right click (360° spin): lrtactical's pack factor is replaced by the item config katana.rightClickDamage,
 * and every victim the spin actually damages bleeds for bleedDamagePerSecond each second over bleedSeconds (a new
 * spin hit restarts it). The bleed is GunGang state, not a registered status effect, so vanilla clients stay compatible;
 * health packs and respawns clear it ({@link #clear}).
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class KatanaSpin {
    private static final String KATANA="katana:katanagai";
    /** The attacker while lrtactical resolves a katana right-click attack (server thread, synchronous). */
    private static LivingEntity spinning;
    private record Bleed(UUID attacker,int ticksLeft) { }
    private static final Map<LivingEntity,Bleed> BLEEDING=new WeakHashMap<>();

    /** Called by MeleeAttackMixin around lrtactical's MeleeItem.attack. */
    public static void begin(LivingEntity attacker,ItemStack stack,Object action){
        spinning=attacker!=null&&!attacker.level().isClientSide&&"RIGHT".equals(String.valueOf(action))&&katana(stack)?attacker:null;
    }
    public static void end(){spinning=null;}
    static boolean katana(ItemStack stack){
        var tag=stack.getTag();
        return tag!=null&&KATANA.equals(tag.getString("MeleeWeaponId"))
                &&new net.minecraft.resources.ResourceLocation("lrtactical","melee").equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
    private static boolean spin(DamageSource source){
        return spinning!=null&&source.getEntity()==spinning&&source.getDirectEntity()==spinning;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hurt(LivingHurtEvent event){
        if(!event.getEntity().level().isClientSide&&spin(event.getSource()))event.setAmount((float)ExtraGadgetConfig.katana.rightClickDamage());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damaged(LivingDamageEvent event){
        if(event.isCanceled()||event.getAmount()<=0||!spin(event.getSource()))return;
        var victim=event.getEntity();var config=ExtraGadgetConfig.katana;
        int ticks=ExtraGadgetConfig.ticks(config.bleedSeconds());
        if(victim==spinning||ticks<=0||config.bleedDamagePerSecond()<=0)return;
        BLEEDING.put(victim,new Bleed(spinning.getUUID(),ticks));
    }
    public static boolean bleeding(LivingEntity entity){return BLEEDING.containsKey(entity);}
    /** Bleed damage still to come: one tick every 20 ticks left (the last one at 0). */
    static double pendingBleed(LivingEntity entity){
        var bleed=BLEEDING.get(entity);if(bleed==null||bleed.ticksLeft()<=0)return 0;
        return ((bleed.ticksLeft()-1)/20+1)*ExtraGadgetConfig.katana.bleedDamagePerSecond();
    }
    public static void clear(LivingEntity entity){BLEEDING.remove(entity);}
    @SubscribeEvent public static void died(LivingDeathEvent event){BLEEDING.remove(event.getEntity());}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||BLEEDING.isEmpty())return;
        // Damage can kill and clear entries, so iterate over a snapshot.
        for(var entry:new ArrayList<>(BLEEDING.entrySet())){
            var victim=entry.getKey();var bleed=entry.getValue();
            if(!victim.isAlive()||victim.isRemoved()||victim.isSpectator()||SafeZones.contains(victim)){BLEEDING.remove(victim);continue;}
            int left=bleed.ticksLeft()-1;
            if(left<=0)BLEEDING.remove(victim);else BLEEDING.put(victim,new Bleed(bleed.attacker(),left));
            if(left%20!=0||!(victim.level() instanceof ServerLevel level))continue;
            var attacker=bleed.attacker()==null?null:level.getEntity(bleed.attacker());
            var type=level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.MAGIC);
            victim.invulnerableTime=0;
            // Bleeding keeps the attacker for kill credit, but a bleed tick must not shove the victim towards them.
            CombatRules.quietly(()->victim.hurt(new DamageSource(type,null,attacker),(float)ExtraGadgetConfig.katana.bleedDamagePerSecond()));
        }
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){BLEEDING.clear();spinning=null;}
    private KatanaSpin(){}
}
