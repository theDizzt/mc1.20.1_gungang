package dev.gungang.acoustics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Key bindings removed from the Controls screen: marked as removed in its list and kept unbound until restored. */
@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class KeyRemoval {
    private static boolean applied;
    private static Button restoreAllButton;

    public static boolean removed(KeyMapping key) { return AcousticConfig.REMOVED_KEYS.get().contains(key.getName()); }

    public static void remove(KeyMapping key) {
        List<String> names = new ArrayList<>(AcousticConfig.REMOVED_KEYS.get());
        if (!names.contains(key.getName())) names.add(key.getName());
        AcousticConfig.REMOVED_KEYS.set(names);
        AcousticConfig.SPEC.save();
        unbind();
    }

    /** Re-unbinds removed keys, e.g. after "Reset Keys" or a hand-edited options.txt. */
    public static void unbind() {
        var mc = Minecraft.getInstance();
        boolean changed = false;
        for (KeyMapping key : mc.options.keyMappings) {
            if (!removed(key) || key.isUnbound()) continue;
            key.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
            changed = true;
        }
        if (!changed) return;
        KeyMapping.resetMapping();
        mc.options.save();
    }

    public static void restore(KeyMapping key) {
        List<String> names = new ArrayList<>(AcousticConfig.REMOVED_KEYS.get());
        if (!names.remove(key.getName())) return;
        AcousticConfig.REMOVED_KEYS.set(names);
        AcousticConfig.SPEC.save();
        key.setToDefault();
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    public static void restoreAll() {
        var mc = Minecraft.getInstance();
        for (KeyMapping key : mc.options.keyMappings) if (removed(key)) key.setToDefault();
        AcousticConfig.REMOVED_KEYS.set(List.of());
        AcousticConfig.SPEC.save();
        KeyMapping.resetMapping();
        mc.options.save();
    }

    /** Refreshes every row and the "restore removed" button after a key is removed or restored. */
    public static void refresh(KeyBindsList list) {
        list.refreshEntries();
        if (restoreAllButton == null) return;
        int count = AcousticConfig.REMOVED_KEYS.get().size();
        restoreAllButton.visible = count > 0;
        restoreAllButton.setMessage(Component.translatable("gungang.controls.restoreRemoved", count));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        // Options load after mod setup, so enforce stored removals on the first client tick.
        if (applied || event.phase != TickEvent.Phase.END || !AcousticConfig.SPEC.isLoaded()) return;
        applied = true;
        unbind();
    }

    @SubscribeEvent
    public static void controls(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof KeyBindsScreen screen)) return;
        KeyBindsList list = ((KeyRemovalList) screen).gungang$keyBindsList();
        restoreAllButton = Button.builder(Component.empty(), b -> {
                    restoreAll();
                    refresh(list);
                }).bounds(screen.width - 124, 1, 120, 18)
                .tooltip(Tooltip.create(Component.translatable("gungang.controls.restoreRemoved.tip"))).build();
        event.addListener(restoreAllButton);
        refresh(list);
    }

    private KeyRemoval() { }
}
