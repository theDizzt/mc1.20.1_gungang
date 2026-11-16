package dev.gungang.acoustics.server;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Passive primary 39. Carried while using a secondary; neither mouse button attacks. */
public final class ToeItem extends Item {
    public ToeItem(){super(new Properties().stacksTo(1));}
    public static boolean is(ItemStack stack){return stack!=null&&stack.getItem() instanceof ToeItem;}
    /** Ticks left on the left-click sprint cooldown (0 when ready); synced to the client with the stack. */
    public static long cooldown(net.minecraft.world.level.Level level,ItemStack stack){
        if(!is(stack)||level==null)return 0;var tag=stack.getTag();
        return tag==null?0:Math.max(0,tag.getLong("ToeCooldownEnd")-level.getGameTime());
    }
    /** Length of the cooldown last started, for the slot's clock wipe. */
    public static int cooldownTotal(ItemStack stack){var tag=stack.getTag();return tag==null?0:tag.getInt("ToeCooldownTicks");}
    public static ItemStack carried(LivingEntity entity){
        if(is(entity.getMainHandItem()))return entity.getMainHandItem();
        if(is(entity.getOffhandItem()))return entity.getOffhandItem();
        if(entity instanceof Player p)for(var stack:p.getInventory().items)if(is(stack))return stack;
        if(entity instanceof BotEntity&&!entity.level().isClientSide){var agent=BotManager.agent(entity);if(agent!=null&&is(agent.primaryStack))return agent.primaryStack;}
        return ItemStack.EMPTY;
    }
    public static boolean blocksSecondary(LivingEntity entity,ItemStack stack){
        if(entity==null||stack==null||carried(entity).isEmpty())return false;
        var tag=stack.getTag();
        return tag!=null&&(tag.toString().contains("lrtactical:karambit")||tag.getString("GunGangSecondaryId").equals("88"));
    }
    @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,Player player){return false;}
    @Override public boolean onEntitySwing(ItemStack stack,LivingEntity entity){return true;}
    @Override public void inventoryTick(ItemStack stack,net.minecraft.world.level.Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){
        if(!level.isClientSide){var tag=stack.getOrCreateTag();tag.putDouble("ToeJumpHeight",ExtraGadgetConfig.toe.jumpHeight());tag.putInt("ToeMaximum",ExtraGadgetConfig.toe.maximumLength());}
    }
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean changed){return changed||oldStack.getItem()!=newStack.getItem();}
}
