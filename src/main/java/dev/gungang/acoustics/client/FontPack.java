package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FontPack {
    @SubscribeEvent
    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        try {
            ExternalFonts.prepareDirectory(FMLPaths.CONFIGDIR.get());
        } catch (IOException failure) {
            LogUtils.getLogger().warn("Could not create config/gungang/fonts", failure);
        }
        var root = ModList.get().getModFileById(GungangAcoustics.ID).getFile().findResource("resourcepacks", "gungang_font");
        event.addRepositorySource(consumer -> {
            boolean enabled = AcousticConfig.SPEC.isLoaded() ? AcousticConfig.CUSTOM_FONT.get() : AcousticConfig.CUSTOM_FONT.getDefault();
            if (!enabled) return;
            Pack pack = Pack.readMetaAndCreate("gungang_font", Component.literal("GunGangRealistic Font"), true,
                    id -> new FontResources(id, root), PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }
    @SubscribeEvent
    public static void loaded(FMLLoadCompleteEvent event) {
        // The first repository scan can precede CLIENT config loading on startup.
        event.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            boolean selected = mc.getResourcePackRepository().getSelectedIds().contains("gungang_font");
            // Rebuild the generated definition after config loading, including file selection.
            if (selected != AcousticConfig.CUSTOM_FONT.get() || AcousticConfig.CUSTOM_FONT.get()) {
                mc.getResourcePackRepository().reload();
                mc.reloadResourcePacks();
            }
        });
    }

    private static final class FontResources extends PathPackResources {
        private final byte[] external;
        private final byte[] definition;

        FontResources(String id, Path root) {
            super(id, root, true);
            byte[] loaded = null;
            try {
                String selection = AcousticConfig.SPEC.isLoaded()
                        ? AcousticConfig.FONT_FILE.get() : AcousticConfig.FONT_FILE.getDefault();
                loaded = ExternalFonts.load(ExternalFonts.prepareDirectory(FMLPaths.CONFIGDIR.get()), selection);
            } catch (IOException | IllegalArgumentException failure) {
                LogUtils.getLogger().warn("Could not load TTF from config/gungang/fonts; using bundled Galmuri11 Bold", failure);
            }
            external = loaded;
            int resolution = AcousticConfig.SPEC.isLoaded()
                    ? AcousticConfig.FONT_OVERSAMPLE.get() : AcousticConfig.FONT_OVERSAMPLE.getDefault();
            String providers = external == null ? "" : provider("user.ttf", resolution) + ",";
            definition = ("{\"providers\":[" + providers + provider("galmuri11-bold.ttf", resolution)
                     + "]}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        private static String provider(String filename, int resolution) {
            double offsetX = AcousticConfig.SPEC.isLoaded() ? AcousticConfig.FONT_OFFSET_X.get() : AcousticConfig.FONT_OFFSET_X.getDefault();
            double offsetY = AcousticConfig.SPEC.isLoaded() ? AcousticConfig.FONT_OFFSET_Y.get() : AcousticConfig.FONT_OFFSET_Y.getDefault();
            return "{\"type\":\"ttf\",\"file\":\"gungang_acoustics:" + filename
                    + "\",\"shift\":[" + offsetX + "," + offsetY + "],\"size\":9,\"oversample\":" + resolution + "}";
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            Set<String> namespaces = new HashSet<>(super.getNamespaces(type));
            if (type == PackType.CLIENT_RESOURCES) namespaces.add("gungang_acoustics");
            return namespaces;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            if (type == PackType.CLIENT_RESOURCES) {
                if (location.getNamespace().equals("minecraft") && location.getPath().equals("font/default.json")) {
                    return () -> new ByteArrayInputStream(definition);
                }
                if (external != null && location.getNamespace().equals("gungang_acoustics")
                        && location.getPath().equals("font/user.ttf")) {
                    return () -> new ByteArrayInputStream(external);
                }
            }
            return super.getResource(type, location);
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            // FontManager discovers font JSONs through enumeration, not just getResource.
            super.listResources(type, namespace, path,
                    (location, original) -> output.accept(location, getResource(type, location)));
        }
    }

    private FontPack() { }
}
