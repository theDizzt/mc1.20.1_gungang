package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.OffhandMacro;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MinecraftInputMixin implements OffhandMacro.Clicks {
    @Invoker("startAttack") public abstract boolean gungang$attack();
    @Invoker("startUseItem") public abstract void gungang$use();

    // A PASS from the temporary main-hand item must not use the original weapon in the other hand.
    @Redirect(method = "startUseItem()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionHand;values()[Lnet/minecraft/world/InteractionHand;"))
    private InteractionHand[] gungang$macroHand() {
        return OffhandMacro.executingClick() ? new InteractionHand[]{InteractionHand.MAIN_HAND} : InteractionHand.values();
    }
}
