package dev.gungang.acoustics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.CombatGadgets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ShieldHandLayerMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void gungang$shield(LivingEntity entity,ItemStack stack,ItemDisplayContext context,HumanoidArm arm,
            PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci) {
        if(entity instanceof Player p&&CombatGadgets.holdingShield(p))ci.cancel();
    }
}
