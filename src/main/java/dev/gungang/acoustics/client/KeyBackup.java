package dev.gungang.acoustics.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/** Saves every key binding, plus the removed-key list, to one file and loads it back from the Controls screen. */
@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class KeyBackup {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("gungang_acoustics-keybinds.json"); }

    static void save() throws IOException {
        JsonObject keys = new JsonObject();
        for (KeyMapping key : Minecraft.getInstance().options.keyMappings) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", key.saveString());
            entry.addProperty("modifier", key.getKeyModifier().name());
            keys.add(key.getName(), entry);
        }
        JsonArray removed = new JsonArray();
        AcousticConfig.REMOVED_KEYS.get().forEach(removed::add);
        JsonObject root = new JsonObject();
        root.add("keys", keys);
        root.add("removed", removed);
        Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    /** Applies the saved file; controls missing from it, e.g. from mods added later, keep their current key. */
    static int load() throws IOException {
        JsonObject root = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), JsonObject.class);
        JsonObject keys = root.getAsJsonObject("keys");
        var mc = Minecraft.getInstance();
        int applied = 0;
        for (KeyMapping key : mc.options.keyMappings) {
            if (!keys.has(key.getName())) continue;
            JsonObject entry = keys.getAsJsonObject(key.getName());
            KeyModifier modifier = KeyModifier.valueFromString(entry.get("modifier").getAsString());
            key.setKeyModifierAndCode(modifier, InputConstants.getKey(entry.get("key").getAsString()));
            applied++;
        }
        List<String> removed = new ArrayList<>();
        if (root.get("removed") instanceof JsonArray array) for (JsonElement name : array) removed.add(name.getAsString());
        AcousticConfig.REMOVED_KEYS.set(removed);
        AcousticConfig.SPEC.save();
        KeyMapping.resetMapping();
        mc.options.save();
        KeyRemoval.unbind();
        return applied;
    }

    @SubscribeEvent
    public static void controls(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof KeyBindsScreen screen)) return;
        var mc = Minecraft.getInstance();
        Button[] loadButton = new Button[1];
        event.addListener(Button.builder(Component.translatable("gungang.controls.backup.save"), b -> {
                    try {
                        save();
                        loadButton[0].active = true;
                        toast("gungang.controls.backup.saved", Component.literal(file().getFileName().toString()));
                    } catch (IOException | RuntimeException failure) {
                        LogUtils.getLogger().error("Could not save key bindings", failure);
                        toast("gungang.controls.backup.failed", null);
                    }
                }).bounds(4, 1, 64, 18)
                .tooltip(Tooltip.create(Component.translatable("gungang.controls.backup.save.tip"))).build());
        loadButton[0] = Button.builder(Component.translatable("gungang.controls.backup.load"), b -> {
                    try {
                        int applied = load();
                        KeyRemoval.refresh(((KeyRemovalList) screen).gungang$keyBindsList());
                        toast("gungang.controls.backup.loaded", Component.translatable("gungang.controls.backup.count", applied));
                    } catch (IOException | RuntimeException failure) {
                        LogUtils.getLogger().error("Could not load key bindings", failure);
                        toast("gungang.controls.backup.failed", null);
                    }
                }).bounds(70, 1, 64, 18)
                .tooltip(Tooltip.create(Component.translatable("gungang.controls.backup.load.tip"))).build();
        loadButton[0].active = Files.isRegularFile(file());
        event.addListener(loadButton[0]);
    }

    private static void toast(String title, Component detail) {
        SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                Component.translatable(title), detail);
    }

    private KeyBackup() { }
}
