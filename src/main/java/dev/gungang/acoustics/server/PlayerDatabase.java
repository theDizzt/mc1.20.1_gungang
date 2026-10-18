package dev.gungang.acoustics.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** UUID-indexed local JSON database. Atomic commits, backup, and copy-on-write transactions. */
public final class PlayerDatabase {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private record Data(int version,List<PlayerRecord> players) { }
    private final Path file;
    private Map<String,PlayerRecord> rows=new LinkedHashMap<>();
    public PlayerDatabase(Path file) throws IOException {
        this.file=file;
        if(Files.exists(file)) {
            String text=Files.readString(file);
            var data=JSON.fromJson(text,Data.class);
            if(data==null||data.version()!=1||data.players()==null)throw new IOException("Invalid player database: "+file);
            for(var row:data.players())if(row==null||rows.putIfAbsent(row.id(),row)!=null)throw new IOException("Duplicate or invalid player record");
            // Add the preset fields to records saved by older versions; every other value is rewritten unchanged (old file kept as .bak).
            boolean upgrade=false;
            for(var row:com.google.gson.JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("players"))
                upgrade|=!row.getAsJsonObject().has("presets")||!row.getAsJsonObject().has("defaultPreset");
            if(upgrade) {commit(rows);com.mojang.logging.LogUtils.getLogger().info("[GunGang] Added preset fields to {}",file);}
        } else commit(rows);
    }
    public Collection<PlayerRecord> all() {return List.copyOf(rows.values());}
    public PlayerRecord get(String id,String name) {return rows.getOrDefault(id,PlayerRecord.empty(id,name));}
    public void update(PlayerRecord row) throws IOException {var next=new LinkedHashMap<>(rows);next.put(row.id(),row);commit(next);}
    public Map<String,Integer> match(List<MatchSnapshot.Row> result,String winner,ExperienceRules rules) throws IOException {
        var eligible=result.stream().filter(r->(Set.of("ALPHA","BRAVO").contains(r.team())||r.team().equals(r.id()))&&(r.playSeconds()>0||r.kills()>0||r.deaths()>0||r.damage()>0)).toList();
        Map<String,Double> totals=new HashMap<>();
        for(var r:eligible)totals.merge(r.team(),rules.contribution(r),Double::sum);
        Map<String,Integer> rewards=new LinkedHashMap<>();var next=new LinkedHashMap<>(rows);
        boolean reward=Set.of("ALPHA","BRAVO","DRAW").contains(winner)||eligible.stream().anyMatch(r->r.id().equals(winner));
        for(var r:eligible) {
            int xp=reward?rules.reward(winner.equals(r.team()),rules.contribution(r),totals.getOrDefault(r.team(),0d)):0;
            next.put(r.id(),get(r.id(),r.name()).match(r,xp,winner.equals(r.team())));rewards.put(r.id(),xp);
        }
        commit(next);return rewards;
    }
    private void commit(Map<String,PlayerRecord> next) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp=file.resolveSibling(file.getFileName()+".tmp"),backup=file.resolveSibling(file.getFileName()+".bak");
        Files.writeString(temp,JSON.toJson(new Data(1,List.copyOf(next.values()))));
        if(Files.exists(file))Files.copy(file,backup,StandardCopyOption.REPLACE_EXISTING);
        try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e) {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        rows=next;
    }
}
