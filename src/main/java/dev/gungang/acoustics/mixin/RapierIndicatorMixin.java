package dev.gungang.acoustics.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hide only the vanilla sword recharge indicator, preserving crosshair and saved user settings. */
@Mixin(Gui.class)
public abstract class RapierIndicatorMixin {
    @Redirect(method={"renderCrosshair","renderHotbar"},at=@At(value="INVOKE",target="Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object gungang$rapierIndicator(OptionInstance<?> option){
        var mc=Minecraft.getInstance();
        if(option==mc.options.attackIndicator()&&mc.player!=null&&mc.player.getMainHandItem().getItem() instanceof dev.gungang.acoustics.server.RapierItem)return AttackIndicatorStatus.OFF;
        return option.get();
    }
}
