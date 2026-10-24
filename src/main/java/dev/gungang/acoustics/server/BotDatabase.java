package dev.gungang.acoustics.server;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Identity and career of up to {@link #MAX} bots, independent of human XP and contribution rewards. Each bot slot has
 * a fixed UUID, so renaming keeps its record. Slot 1 keeps the original single-bot UUID; a pre-multi-bot file holding
 * one profile is read as slot 1, and the others start from defaults.
 */
public final class BotDatabase {
    public static final String DEFAULT_SKIN="minecraft:textures/entity/player/wide/steve.png";
    public static final int MAX=4;
    private static final UUID[] IDS={UUID.fromString("87feab0f-4160-4ec8-a993-bcf41039c016"),UUID.fromString("87feab0f-4160-4ec8-a993-bcf41039c017"),
            UUID.fromString("87feab0f-4160-4ec8-a993-bcf41039c018"),UUID.fromString("87feab0f-4160-4ec8-a993-bcf41039c019")};
    public static UUID id(int slot){return IDS[slot];}
    /** Slot index (0-based) of a bot UUID, or -1 for anyone else. */
    public static int slot(UUID id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return -1;}
    static int slot(String id){try{return slot(UUID.fromString(id));}catch(IllegalArgumentException notUuid){return -1;}}
    public record Profile(String name,String skin,PlayerRecord statistics) {
        public Profile {
            if(name==null||name.isBlank()||name.length()>24||name.chars().anyMatch(c->Character.isISOControl(c)||c=='§'))
                throw new IllegalArgumentException("봇 이름은 제어 문자 없이 1~24자로 입력하세요.");
            if(skin==null||skin.length()>200||!(skin.matches("[a-z0-9_.-]+:textures/[a-z0-9_./-]+\\.png")||BotSkinFiles.valid(skin))||skin.contains(".."))
                throw new IllegalArgumentException("스킨은 namespace:textures/...png 또는 file:이름.png 형식으로 입력하세요.");
            if(statistics==null||BotDatabase.slot(statistics.id())<0)throw new IllegalArgumentException("Invalid bot statistics");
            statistics=statistics.named(name);
        }
        /** Shown as-is, like a player name; bots are told apart by their profile skin, not a name prefix. */
        public String displayName(){return name;}
        public Profile named(String value){return new Profile(value,skin,statistics);}
        public Profile skinned(String value){return new Profile(name,value,statistics);}
        int slot(){return BotDatabase.slot(statistics.id());}
    }
    public static Profile defaults(){return defaults(0);}
    public static Profile defaults(int slot){
        String name=slot==0?"Rookie":"Rookie "+(slot+1);
        return new Profile(name,DEFAULT_SKIN,PlayerRecord.empty(IDS[slot].toString(),name));
    }
    private record Data(List<Profile> bots) { }
    private final Path file;
    private final Profile[] profiles=new Profile[MAX];
    public BotDatabase(Path file)throws IOException{
        this.file=file;
        for(int i=0;i<MAX;i++)profiles[i]=defaults(i);
        if(!Files.exists(file)){write(profiles);return;}
        if(Files.size(file)>65536)throw new IOException("bot-db.json is too large");
        var root=com.google.gson.JsonParser.parseString(Files.readString(file));
        if(!root.isJsonObject())throw new IOException("Invalid bot-db.json");
        var object=root.getAsJsonObject();
        List<Profile> stored;
        if(object.has("bots"))stored=ServerFiles.GSON.fromJson(object,Data.class).bots();
        else if(object.has("name"))stored=List.of(ServerFiles.GSON.fromJson(object,Profile.class)); // single-bot format
        else throw new IOException("bot-db.json has no bot profiles");
        if(stored==null)throw new IOException("bot-db.json has no bot profiles");
        var seen=new HashSet<Integer>();
        for(var profile:stored){
            if(profile==null||!seen.add(profile.slot()))throw new IOException("Duplicate or empty bot profile in bot-db.json");
            profiles[profile.slot()]=profile;
        }
    }
    public Profile profile(){return profiles[0];}
    public Profile profile(int slot){return profiles[slot];}
    /** Saves the profile into the slot its statistics id belongs to. */
    public void update(Profile value)throws IOException{
        var next=profiles.clone();next[value.slot()]=value;
        write(next);
        profiles[value.slot()]=value;
    }
    private void write(Profile[] values)throws IOException{
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temp,ServerFiles.GSON.toJson(new Data(List.of(values))));
        if(Files.exists(file))Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    public void finish(MatchSnapshot match)throws IOException{finish(match,ExperienceRules.defaults());}
    /**
     * Adds the match to every bot that actually took part. Bots earn XP by the same rules as players (result plus
     * share of the team's contribution, counting the human teammates), kept here only: it never enters player-db.json
     * and players' own rewards are computed without bot rows.
     */
    public void finish(MatchSnapshot match,ExperienceRules rules)throws IOException{
        var eligible=match.players().stream().filter(r->Set.of("ALPHA","BRAVO").contains(r.team())&&(r.playSeconds()>0||r.kills()>0||r.deaths()>0||r.damage()>0)).toList();
        Map<String,Double> totals=new HashMap<>();
        for(var r:eligible)totals.merge(r.team(),rules.contribution(r),Double::sum);
        boolean reward=Set.of("ALPHA","BRAVO","DRAW").contains(match.winner());
        for(var row:eligible){
            int slot=slot(row.id());if(slot<0)continue;
            boolean won=row.team().equals(match.winner());
            int xp=reward?rules.reward(won,rules.contribution(row),totals.getOrDefault(row.team(),0d)):0;
            var profile=profiles[slot];
            update(new Profile(profile.name(),profile.skin(),profile.statistics().match(row,xp,won)));
        }
    }
}
