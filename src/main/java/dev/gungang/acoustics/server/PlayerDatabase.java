package dev.gungang.acoustics.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** UUID-indexed local JSON database. Atomic commits, backup, and copy-on-write transactions. */
public final class PlayerDatabase implements AutoCloseable {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private record Data(int version,List<PlayerRecord> players) { }
    private final Path file;
    private Map<String,PlayerRecord> rows=new LinkedHashMap<>();
    private ScheduledExecutorService writer;
    private final Object saveLock=new Object();
    private Map<String,PlayerRecord> pending;
    private ScheduledFuture<?> scheduled;
    private boolean closed;
    private volatile IOException saveFailure;
    /** Initial loading/migration finishes before the background writer is exposed to the server. */
    static PlayerDatabase background(Path file) throws IOException {
        var db=new PlayerDatabase(file);
        db.writer=Executors.newSingleThreadScheduledExecutor(task->{
            var thread=new Thread(task,"gungang-player-db");thread.setDaemon(true);return thread;
        });
        return db;
    }
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
            // Records from before 4-digit tags get one now (commit assigns them).
            upgrade|=rows.values().stream().anyMatch(r->r.tag().isEmpty());
            if(upgrade) {commit(rows);com.mojang.logging.LogUtils.getLogger().info("[GunGang] Added preset fields to {}",file);}
        } else commit(rows);
    }
    public Collection<PlayerRecord> all() {return List.copyOf(rows.values());}
    /** The stored record, or an empty one (without a tag until it is first saved). */
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
    /** Gives every record without a tag a free one (PlayerTag.allocate); records that have one keep it. */
    static Map<String,PlayerRecord> tagged(Map<String,PlayerRecord> next) {
        if(next.values().stream().allMatch(r->!r.tag().isEmpty()))return next;
        var used=new HashSet<String>();for(var r:next.values())if(!r.tag().isEmpty())used.add(r.tag());
        var result=new LinkedHashMap<String,PlayerRecord>();
        for(var entry:next.entrySet()){
            var r=entry.getValue();
            if(r.tag().isEmpty()){String tag=PlayerTag.allocate(UUID.fromString(r.id()),used);used.add(tag);r=r.withTag(tag);}
            result.put(entry.getKey(),r);
        }
        return result;
    }
    private void commit(Map<String,PlayerRecord> next) throws IOException {
        next=tagged(next);
        if(writer==null){write(next);rows=next;return;}
        synchronized(saveLock){
            if(closed)throw new IOException("Player database is closed");
            // Maps are copy-on-write and PlayerRecord defensively copies its lists. Never mutate
            // an accepted map: the worker owns this snapshot even after the next game update.
            pending=next;
            if(scheduled==null)scheduled=writer.schedule(this::savePending,1,TimeUnit.SECONDS);
            rows=next;
        }
    }
    private void savePending(){
        Map<String,PlayerRecord> snapshot;
        synchronized(saveLock){
            if(closed)return;
            snapshot=pending;pending=null;scheduled=null;
        }
        if(snapshot==null)return;
        try{
            write(snapshot);
            if(saveFailure!=null)com.mojang.logging.LogUtils.getLogger().info("[GunGang] Player database saving recovered: {}",file);
            saveFailure=null;
        }catch(Exception error){
            var failure=error instanceof IOException io?io:new IOException("Player database serialization failed",error);
            if(saveFailure==null)com.mojang.logging.LogUtils.getLogger().error("[GunGang] Player database save failed; retaining latest snapshot and retrying: {}",file,failure);
            saveFailure=failure;
            synchronized(saveLock){
                if(!closed){
                    // A newer snapshot already includes this transaction. Never replace it with an old retry.
                    if(pending==null)pending=snapshot;
                    if(scheduled==null)scheduled=writer.schedule(this::savePending,5,TimeUnit.SECONDS);
                }
            }
        }
    }
    IOException saveFailure(){return saveFailure;}
    private void write(Map<String,PlayerRecord> snapshot) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp=file.resolveSibling(file.getFileName()+".tmp"),backup=file.resolveSibling(file.getFileName()+".bak");
        Files.writeString(temp,JSON.toJson(new Data(1,List.copyOf(snapshot.values()))));
        if(Files.exists(file))Files.copy(file,backup,StandardCopyOption.REPLACE_EXISTING);
        try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e) {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    /** Only shutdown waits for I/O. The final snapshot follows any in-flight write on the same worker. */
    @Override public void close() throws IOException {
        if(writer==null)return;
        Future<?> last;
        synchronized(saveLock){
            if(closed)return;
            closed=true;
            if(scheduled!=null)scheduled.cancel(false);
            scheduled=null;pending=null;
            var snapshot=rows;
            last=writer.submit(()->{write(snapshot);return null;});
            writer.shutdown();
        }
        boolean interrupted=false;
        try{
            // Do not leave an old writer running against the next integrated-server session.
            while(true)try{last.get();saveFailure=null;break;}
            catch(InterruptedException error){interrupted=true;}
            catch(ExecutionException error){throw new IOException("Could not flush player database: "+file,error.getCause());}
        }finally{if(interrupted)Thread.currentThread().interrupt();}
    }
}
