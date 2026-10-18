package dev.gungang.acoustics.server;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ServerFiles {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public record Loaded(JsonObject acoustics, List<ArenaMap> maps, TeamOptions options) { }
    public static Path root() { return FMLPaths.CONFIGDIR.get().resolve("gungang"); }
    public static Loaded load() throws IOException {
        Path directory = root();
        Files.createDirectories(directory.resolve("fonts"));
        Path acousticFile = directory.resolve("acoustics.json");
        if (!Files.exists(acousticFile)) Files.writeString(acousticFile, GSON.toJson(AcousticPolicy.defaults()), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        JsonObject acoustic = JsonParser.parseString(read(acousticFile)).getAsJsonObject();
        var checked = AcousticPolicy.parse(acoustic);
        Path optionsFile = directory.resolve("server/option.json");
        ConfigLayout.writeNew(optionsFile, GSON.toJson(TeamOptions.defaults()));
        TeamOptions options = GSON.fromJson(read(optionsFile), TeamOptions.class);
        if (options == null) throw new IOException("Empty option.json");
        return new Loaded(GSON.toJsonTree(checked).getAsJsonObject(), loadMaps(directory), options);
    }
    static List<ArenaMap> loadMaps(Path directory) throws IOException {
        Path mapsDirectory = directory.resolve("server").resolve("maps");
        boolean firstRun = !Files.exists(mapsDirectory);
        Files.createDirectories(mapsDirectory);
        Files.createDirectories(mapsDirectory.resolve("preview"));
        Files.createDirectories(mapsDirectory.resolve("data"));
        if (firstRun) ConfigLayout.writeNew(mapsDirectory.resolve("example.json"), GSON.toJson(ArenaMap.example()));
        ConfigLayout.writeNew(mapsDirectory.resolve("normandy.json"), GSON.toJson(NormandyRules.example()));
        ConfigLayout.writeNew(mapsDirectory.resolve("bomb.json"), GSON.toJson(BombRules.example()));
        ConfigLayout.writeNew(mapsDirectory.resolve("escort.json"), GSON.toJson(EscortRules.example()));
        ConfigLayout.writeNew(mapsDirectory.resolve("free_for_all.json"), GSON.toJson(FreeForAllRules.example()));
        ConfigLayout.writeNew(mapsDirectory.resolve("prop_hunt.json"), GSON.toJson(PropHuntRules.example()));
        List<ArenaMap> maps = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try (var paths = Files.list(mapsDirectory)) {
            var files = paths.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json")).sorted().toList();
            if (files.size() > 128) throw new IOException("At most 128 maps are supported");
            for (Path path : files) {
                ArenaMap map = GSON.fromJson(read(path), ArenaMap.class);
                if (map == null) throw new IOException("Empty map: " + path);
                map.validate();
                if (!ids.add(map.id())) throw new IOException("Duplicate map id: " + map.id());
                if (map.enabled()) maps.add(map);
            }
        }
        com.mojang.logging.LogUtils.getLogger().info("[GunGang] Loaded {} enabled maps from {}", maps.size(), mapsDirectory.toAbsolutePath().normalize());
        maps.sort(java.util.Comparator.comparing(ArenaMap::id));
        return List.copyOf(maps);
    }
    private static String read(Path path) throws IOException {
        if (Files.size(path) > 65536) throw new IOException("Config is too large: " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
    private ServerFiles() { }
}
