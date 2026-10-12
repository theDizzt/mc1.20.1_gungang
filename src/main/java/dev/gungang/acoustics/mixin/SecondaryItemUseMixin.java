package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.SecondaryRuntime;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class SecondaryItemUseMixin {
    @Redirect(method="useOn",at=@At(value="INVOKE",target="Lnet/minecraftforge/common/ForgeHooks;onPlaceItemIntoWorld(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",remap=false))
    private InteractionResult place(net.minecraft.world.item.context.UseOnContext context) {
        return SecondaryRuntime.use((ItemStack)(Object)this,context.getPlayer(),()->net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(context),InteractionResult.FAIL);
    }
    @Inject(method="setCount",at=@At("HEAD"),cancellable=true)
    private void preserve(int count,CallbackInfo ci) { if(SecondaryRuntime.preventDecrease((ItemStack)(Object)this,count))ci.cancel(); }
    @Redirect(method="use",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/Item;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"))
    private InteractionResultHolder<ItemStack> use(Item item,Level level,Player player,InteractionHand hand) {
        var stack=(ItemStack)(Object)this;
        return SecondaryRuntime.use(stack,player,()->item.use(level,player,hand),InteractionResultHolder.fail(stack));
    }
    @Redirect(method="finishUsingItem",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/Item;finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack finish(Item item,ItemStack stack,Level level,LivingEntity entity) {
        return SecondaryRuntime.use(stack,entity,()->{var result=item.finishUsingItem(stack,level,entity);return SecondaryRuntime.secondary(stack)?stack:result;},stack);
    }
    @Redirect(method="releaseUsing",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/Item;releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V"))
    private void release(Item item,ItemStack stack,Level level,LivingEntity entity,int time) {
        SecondaryRuntime.use(stack,entity,()->{item.releaseUsing(stack,level,entity,time);return true;},false);
    }
    @Redirect(method="onUseTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/Item;onUseTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;I)V"))
    private void tick(Item item,Level level,LivingEntity entity,ItemStack stack,int time) {
        SecondaryRuntime.use(stack,entity,()->{item.onUseTick(level,entity,stack,time);return true;},false);
    }
}
