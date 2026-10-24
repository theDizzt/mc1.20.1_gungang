package dev.gungang.acoustics.server;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/**
 * Hand-placed tactical points per map, in {@code config/gungang/server/bot_waypoints/<map>.json}: routes to patrol,
 * cover spots, sniper perches and objectives. Optional: without a file the bot samples the map on its own.
 * {@code team} limits a point to ALPHA or BRAVO (e.g. each side's own perches); ANY serves both.
 */
public final class BotWaypoints {
    public enum Type {ROUTE,COVER,SNIPER,OBJECTIVE}
    public record Point(Type type,double x,double y,double z,String team) {
        public Point {
            if(type==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>29_999_000||Math.abs(z)>29_999_000)
                throw new IllegalArgumentException("Invalid bot waypoint");
            team=team==null?"ANY":team;
            if(!Set.of("ANY","ALPHA","BRAVO").contains(team))throw new IllegalArgumentException("Waypoint team: ANY / ALPHA / BRAVO");
        }
        public Vec3 position() {return new Vec3(x,y,z);}
        public boolean serves(String side) {return team.equals("ANY")||team.equals(side);}
    }
    public record Data(List<Point> points) {
        public Data {points=points==null?List.of():List.copyOf(points);if(points.size()>512)throw new IllegalArgumentException("At most 512 bot waypoints per map");}
    }
    private static final Map<String,Data> CACHE=new HashMap<>();
    static Path file(String map) {
        if(map==null||!map.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("Invalid map id");
        return ServerFiles.root().resolve("server/bot_waypoints/"+map+".json");
    }
    /** Cached; a broken file is reported once in the log and treated as empty so the bot keeps working. */
    static Data get(String map) {
        return CACHE.computeIfAbsent(map,id->{
            try {
                Path path=file(id);if(!Files.exists(path))return new Data(List.of());
                if(Files.size(path)>262144)throw new IOException("bot waypoint file too large");
                var data=ServerFiles.GSON.fromJson(Files.readString(path),Data.class);
                return data==null?new Data(List.of()):data;
            }catch(Exception error) {
                com.mojang.logging.LogUtils.getLogger().warn("[GunGang] Ignoring bot waypoints for {}: {}",id,error.getMessage());
                return new Data(List.of());
            }
        });
    }
    static List<Point> of(String map,Type type,String side) {
        return get(map).points().stream().filter(p->p.type()==type&&p.serves(side)).toList();
    }
    static void save(String map,Data data) throws IOException {
        Path path=file(map);Files.createDirectories(path.getParent());
        Path temp=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(temp,ServerFiles.GSON.toJson(data));
        Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
        CACHE.put(map,data);
    }
    static void forget() {CACHE.clear();}
    private BotWaypoints() { }
}
