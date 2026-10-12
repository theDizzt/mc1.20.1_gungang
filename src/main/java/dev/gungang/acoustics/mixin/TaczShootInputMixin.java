package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.OffhandMacro;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoid TaCZ's ordinary polling resetting charge or firing twice during the macro. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.ShootKey", remap = false)
public abstract class TaczShootInputMixin {
    @Inject(method = "autoShoot", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gungang$macroInput(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (OffhandMacro.ownsGunInput()||dev.gungang.acoustics.server.SafeZones.contains(net.minecraft.client.Minecraft.getInstance().player)
                ||dev.gungang.acoustics.server.CombatGadgets.holdingShield(net.minecraft.client.Minecraft.getInstance().player)) ci.cancel();
    }
}
