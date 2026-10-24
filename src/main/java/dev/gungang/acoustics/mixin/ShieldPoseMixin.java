package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.ShieldRendering;
import dev.gungang.acoustics.server.CombatGadgets;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class ShieldPoseMixin {
    @Inject(method="setupAnim",at=@At("TAIL"))
    private void gungang$guard(LivingEntity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        dev.gungang.acoustics.client.HandAxeAnimation.poseArm((HumanoidModel<?>)(Object)this,entity);
        if(entity instanceof Player player&&CombatGadgets.holdingShield(player))
            ShieldRendering.poseArms((HumanoidModel<?>)(Object)this);
    }
}
