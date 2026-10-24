package dev.gungang.acoustics.mixin;

import com.google.common.collect.ImmutableList;
import dev.gungang.acoustics.client.KeyRemoval;
import dev.gungang.acoustics.client.KeyRemovalList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a remove/restore toggle after "Reset" on every Controls row and marks removed rows. */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyEntryMixin {
    @Shadow @Final private KeyMapping key;
    @Shadow @Final private Button changeButton;
    @Shadow @Final private Button resetButton;
    @Unique private Button gungang$remove;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void gungang$addRemove(CallbackInfo ci) {
        gungang$remove = Button.builder(Component.empty(), b -> {
                    if (KeyRemoval.removed(key)) KeyRemoval.restore(key);
                    else KeyRemoval.remove(key);
                    if (Minecraft.getInstance().screen instanceof KeyRemovalList screen) KeyRemoval.refresh(screen.gungang$keyBindsList());
                }).bounds(0, 0, 20, 20).build();
        gungang$mark();
    }

    /** Vanilla's refresh rewrites the key label and reset state, so re-apply the removed look after it. */
    @Inject(method = "refreshEntry", at = @At("TAIL"))
    private void gungang$refresh(CallbackInfo ci) {
        if (gungang$remove != null) gungang$mark();
    }

    @Unique
    private void gungang$mark() {
        boolean removed = KeyRemoval.removed(key);
        changeButton.active = !removed;
        if (removed) {
            resetButton.active = false;
            changeButton.setMessage(Component.translatable("gungang.controls.removed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            changeButton.setTooltip(Tooltip.create(Component.translatable("gungang.controls.removed.tip")));
        }
        gungang$remove.setMessage(Component.translatable(removed ? "gungang.controls.restore.button" : "gungang.controls.remove.button")
                .withStyle(removed ? ChatFormatting.GREEN : ChatFormatting.RED));
        gungang$remove.setTooltip(Tooltip.create(Component.translatable(removed ? "gungang.controls.restore.tip" : "gungang.controls.remove.tip")));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void gungang$tintRemoved(GuiGraphics g, int index, int top, int left, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        if (KeyRemoval.removed(key)) g.fill(left - 2, top, left + width - 2, top + 20, 0x50FF3030);
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"), index = 1)
    private Component gungang$strikeRemovedName(Component name) {
        return KeyRemoval.removed(key) ? name.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH) : name;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void gungang$renderRemove(GuiGraphics g, int index, int top, int left, int width, int height,
                                      int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        // Vanilla's reset button ends at left + 260; KeyBindsListMixin widens the row so this stays clickable.
        gungang$remove.setX(left + 262);
        gungang$remove.setY(top);
        gungang$remove.render(g, mouseX, mouseY, partialTick);
    }

    @Inject(method = "children", at = @At("RETURN"), cancellable = true)
    private void gungang$children(CallbackInfoReturnable<List<? extends GuiEventListener>> cir) {
        cir.setReturnValue(ImmutableList.of(changeButton, resetButton, gungang$remove));
    }

    @Inject(method = "narratables", at = @At("RETURN"), cancellable = true)
    private void gungang$narratables(CallbackInfoReturnable<List<? extends NarratableEntry>> cir) {
        cir.setReturnValue(ImmutableList.of(changeButton, resetButton, gungang$remove));
    }
}
