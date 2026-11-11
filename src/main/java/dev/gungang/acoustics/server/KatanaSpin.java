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
 * spin hit restarts it). The bleed itself is {@link Bleeding}.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class KatanaSpin {
    private static final String KATANA="katana:katanagai";
    /** The attacker while lrtactical resolves a katana right-click attack (server thread, synchronous). */
    private static LivingEntity spinning;

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
        Bleeding.apply(victim,spinning,ticks,config.bleedDamagePerSecond());
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){spinning=null;}
    private KatanaSpin(){}
}
