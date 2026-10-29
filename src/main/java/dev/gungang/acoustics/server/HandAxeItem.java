package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Secondary 80: holding use charges a straight throw, which starts the cooldown. No durability. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class HandAxeItem extends Item {
    public HandAxeItem() { super(new Properties().stacksTo(1)); }
    public static boolean is(ItemStack stack) {return stack.getItem() instanceof HandAxeItem;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(player.isSpectator()||SecondaryRuntime.remaining(player,stack)>0)return InteractionResultHolder.fail(stack);
        if(!level.isClientSide) {
            configurePreview(stack);
            player.inventoryMenu.broadcastChanges();
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack) {return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack stack) {return UseAnim.SPEAR;}
    public static void configurePreview(ItemStack stack) {
        var c=ExtraGadgetConfig.handAxe;var tag=stack.getOrCreateTag();
        tag.putDouble("AxeMinRange",c.minThrowDistance());tag.putDouble("AxeMaxRange",c.maxThrowDistance());
        tag.putDouble("AxeChargeSeconds",c.chargeSeconds());tag.putDouble("AxeArcDegrees",c.arcDegrees());
    }
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining) {
        if(level.isClientSide||!(entity instanceof Player player)||!player.isAlive()||SafeZones.contains(player)||LobbyManager.respawning(player.getUUID()))return;
        var c=ExtraGadgetConfig.handAxe;
        double distance=HandAxeRules.distance(getUseDuration(stack)-remaining,c.minThrowDistance(),c.maxThrowDistance(),c.chargeSeconds());
        var axe=GadgetItems.HAND_AXE_PROJECTILE.get().create(level);if(axe==null)return;
        axe.setOwner(player);axe.setItem(stack.copyWithCount(1));axe.setPos(HandAxeFlight.origin(player));
        var launch=HandAxeFlight.velocity(player,distance,c.arcDegrees());
        axe.shoot(launch.x,launch.y,launch.z,(float)launch.length(),0);
        axe.setDeltaMovement(launch);
        axe.expireAfter(ExtraGadgetConfig.ticks(c.cooldownSeconds()));
        if(!level.addFreshEntity(axe))return;
        level.playSound(null,player.blockPosition(),SoundEvents.TRIDENT_THROW,SoundSource.PLAYERS,.8f,1.2f);
        // Inside a secondary use scope this starts the configured secondary cooldown instead of a vanilla one.
        player.getCooldowns().addCooldown(this,ExtraGadgetConfig.ticks(c.cooldownSeconds()));
    }
    /** Prevent vanilla melee attacks on both sides. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void swing(AttackEntityEvent event) {
        if(is(event.getEntity().getMainHandItem()))event.setCanceled(true);
    }
    @Override public boolean onEntitySwing(ItemStack stack,LivingEntity entity) {return true;}
}