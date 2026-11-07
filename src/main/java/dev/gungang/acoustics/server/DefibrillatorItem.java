package dev.gungang.acoustics.server;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Secondary 78 제세동기: held use aims (the client marks the locked teammate); releasing charges them. See {@link Defibrillator}. */
public final class DefibrillatorItem extends Item {
    /** Cooldown outside a secondary loadout slot (e.g. a /give test item); the slot uses its configured seconds. */
    private static final int COOLDOWN_TICKS=30*20;
    public DefibrillatorItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(player.isSpectator()||SecondaryRuntime.remaining(player,stack)>0||player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
        if(level.isClientSide||!(entity instanceof Player player)||!player.isAlive()||player.isSpectator()||SafeZones.contains(player)||LobbyManager.respawning(player.getUUID()))return;
        var target=Defibrillator.release(player);
        if(target==null){player.displayClientMessage(Component.literal("제세동기: 32블록 안에 조준한 아군이 없습니다."),true);return;}
        Defibrillator.grant(player,target);
        // Inside a secondary use scope this starts the configured secondary cooldown instead of a vanilla one.
        player.getCooldowns().addCooldown(this,COOLDOWN_TICKS);
    }
    @Override public boolean onEntitySwing(ItemStack stack,LivingEntity entity){return true;}
}
