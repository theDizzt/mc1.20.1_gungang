package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.KeyRemoval;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBindsList.class)
public abstract class KeyBindsListMixin {
    /**
     * Clicks only reach a row inside its row width, and the remove button sits past vanilla's reset button
     * (left + 262..282). Widen the row so the whole button, ending 2px before the row edge, is clickable.
     */
    @Inject(method = "getRowWidth", at = @At("RETURN"), cancellable = true)
    private void gungang$fitRemoveButton(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Math.max(cir.getReturnValueI(), 284));
    }

    /** "Reset Keys" restores every default; put removed keys back to unbound before the rows refresh. */
    @Inject(method = "resetMappingAndUpdateButtons", at = @At("HEAD"))
    private void gungang$keepRemovedUnbound(CallbackInfo ci) {
        KeyRemoval.unbind();
    }
}
