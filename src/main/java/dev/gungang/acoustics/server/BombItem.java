package dev.gungang.acoustics.server;

import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

public final class BombItem extends Item {
    public final boolean defuser;
    public BombItem(boolean defuser){super(new Properties().stacksTo(1));this.defuser=defuser;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND||player.isSpectator())return InteractionResultHolder.pass(stack);
        if(player instanceof ServerPlayer p){
            var match=MatchManager.bomb();
            if(match==null||!match.beginUse(p,defuser))return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.BOW;}
    @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining){
        if(entity instanceof ServerPlayer p){var match=MatchManager.bomb();if(match==null||!match.continueUse(p,defuser))p.stopUsingItem();}
    }
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
        if(entity instanceof ServerPlayer p&&MatchManager.bomb()!=null)MatchManager.bomb().cancelUse(p);
    }
}
