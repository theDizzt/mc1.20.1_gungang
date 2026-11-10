package dev.gungang.acoustics.mixin;
import dev.gungang.acoustics.server.M95;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.tacz.guns.util.AttachmentDataUtils",remap=false)
public abstract class M95MagazineMixin {
    @Inject(method="getAmmoCountWithAttachment",at=@At("HEAD"),cancellable=true)
    private static void gungang$m95(ItemStack stack,@Coerce Object data,CallbackInfoReturnable<Integer> ci){
        if(M95.is(stack))ci.setReturnValue(stack.getTag().contains(M95.MAGAZINE)?Math.max(1,stack.getTag().getInt(M95.MAGAZINE)):3);
    }
}
