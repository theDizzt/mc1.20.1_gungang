package dev.gungang.acoustics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.RapierItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third person: a rapier is a one-handed duelling blade, so the off-hand secondary is not drawn while it is wielded. */
@Mixin(ItemInHandLayer.class)
public abstract class RapierOffhandLayerMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void gungang$hideOffhand(LivingEntity entity,ItemStack stack,ItemDisplayContext context,HumanoidArm arm,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){
        // Shuriken: a one-handed throwing weapon, likewise drawn without the secondary in the other hand.
        if(arm!=entity.getMainArm()&&(entity.getMainHandItem().getItem() instanceof RapierItem||entity.getMainHandItem().getItem() instanceof dev.gungang.acoustics.server.ShurikenItem))ci.cancel();
    }
}
