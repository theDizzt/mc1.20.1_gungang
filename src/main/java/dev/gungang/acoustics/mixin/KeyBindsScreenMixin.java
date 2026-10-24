package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.KeyRemoval;
import dev.gungang.acoustics.client.KeyRemovalList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyBindsScreen.class)
public abstract class KeyBindsScreenMixin implements KeyRemovalList {
    @Shadow private KeyBindsList keyBindsList;

    @Override public KeyBindsList gungang$keyBindsList() { return keyBindsList; }

    /** A removed key is unbound on purpose; it should not light up "Reset Keys". */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDefault()Z"))
    private boolean gungang$removedCountsAsDefault(KeyMapping key) {
        return KeyRemoval.removed(key) || key.isDefault();
    }
}
