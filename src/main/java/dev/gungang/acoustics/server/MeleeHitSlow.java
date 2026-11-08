package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A hit from 28 rapier, 29 katana or 88 karambit slows the victim for a moment, the way a gunshot staggers: movement
 * -40% for half a second, refreshed by each hit. Only a blow struck by the attacker's own hand counts (thrown or
 * fired things do not), from players and bots alike. No status effect, so no icon or particles.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class MeleeHitSlow {
    static final double AMOUNT=-.4;static final int TICKS=10;
    public static final UUID MODIFIER=UUID.fromString("6a2f0f7e-3c1b-4d6a-9a55-1f2e8b7c4d30");
    private static final Set<String> MELEE=Set.of("katana:katanagai","lrtactical:karambit");
    /** Victims still slowed, until the server tick in the value. */
    private static final Map<LivingEntity,Integer> slowed=new WeakHashMap<>();

    /** 28 rapier, or the lrtactical katana / karambit. */
    static boolean staggers(ItemStack stack){
        if(stack.getItem() instanceof RapierItem)return true;
        var tag=stack.getTag();
        return tag!=null&&MELEE.contains(tag.getString("MeleeWeaponId"))
                &&new net.minecraft.resources.ResourceLocation("lrtactical","melee").equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hit(LivingDamageEvent event){
        if(event.isCanceled()||event.getAmount()<=0)return;
        var victim=event.getEntity();var source=event.getSource();
        if(victim.level().isClientSide||!(source.getEntity() instanceof LivingEntity attacker)||source.getDirectEntity()!=attacker||attacker==victim)return;
        if(!staggers(attacker.getMainHandItem()))return;
        var speed=victim.getAttribute(Attributes.MOVEMENT_SPEED);if(speed==null)return;
        // Path-walking bots move attribute squared, so they take the root to slow down by the same share.
        double amount=victim instanceof net.minecraft.world.entity.Mob?Math.sqrt(1+AMOUNT)-1:AMOUNT;
        if(speed.getModifier(MODIFIER)==null)speed.addTransientModifier(new AttributeModifier(MODIFIER,"GunGang melee hit",amount,AttributeModifier.Operation.MULTIPLY_TOTAL));
        slowed.put(victim,victim.getServer()==null?0:victim.getServer().getTickCount()+TICKS);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||slowed.isEmpty())return;
        int now=event.getServer().getTickCount();
        slowed.entrySet().removeIf(e->{
            if(now<e.getValue()&&e.getKey().isAlive()&&!e.getKey().isRemoved())return false;
            var speed=e.getKey().getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(MODIFIER);
            return true;
        });
    }
    private MeleeHitSlow(){}
}
