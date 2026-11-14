package dev.gungang.acoustics.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/**
 * Saved match results (config/gungang/result/*.txt, written by {@link MatchReport}) for the stats screen's match
 * history. Matches are numbered from 1 in start-time order; the file names begin with the start time, so that is
 * their name order. Parsed files are cached by name: results are only ever added.
 */
public final class MatchHistory {
    /**
     * One player's row. {@code name} is the name written in the file (older results only, "" otherwise) until
     * {@link #named} replaces it with the current nickname and 4-digit tag looked up by UUID.
     */
    public record Player(String name,String tag,String id,String team,int kills,int deaths,int assists,double damage,int seconds,int score) {
        Player named(String value,String newTag){return new Player(value,newTag,id,team,kills,deaths,assists,damage,seconds,score);}
        Player scored(int value){return new Player(name,tag,id,team,kills,deaths,assists,damage,seconds,value);}
    }
    public record Match(int number,String started,String map,String mode,String winner,int alpha,int bravo,int seconds,List<Player> players) {}
    /** One list row; {@code team} and the K/D/A are the viewed player's, team empty when they did not play. */
    public record Summary(int number,String started,String map,String mode,String winner,int alpha,int bravo,int seconds,int players,
                          String team,int kills,int deaths,int assists) {}
    /** Server → client: the list ({@code match} null) or one match's detail. */
    public record View(String target,List<Summary> matches,Match match) {}
    record Parsed(String started,String map,String mode,String winner,int alpha,int bravo,int seconds,List<Player> players) {}
    private static final com.google.gson.Gson GSON=new com.google.gson.Gson();
    private static final Map<String,Parsed> CACHE=new HashMap<>();
    /** Keeps the list packet under the client-bound payload limit; the oldest rows go first. */
    private static final int MAX_BYTES=900_000;

    private static Path directory(){return ServerFiles.root().resolve("result");}
    private static synchronized List<Match> all(){
        var names=new ArrayList<String>();
        try(var files=Files.list(directory())){files.map(p->p.getFileName().toString()).filter(n->n.endsWith(".txt")).forEach(names::add);}
        catch(Exception e){return List.of();}
        Collections.sort(names);
        CACHE.keySet().retainAll(new HashSet<>(names));
        var result=new ArrayList<Match>();
        for(var name:names){
            var parsed=CACHE.get(name);
            if(parsed==null){
                try{parsed=parse(Files.readAllLines(directory().resolve(name),StandardCharsets.UTF_8));}
                catch(Exception e){com.mojang.logging.LogUtils.getLogger().warn("[GunGang] Unreadable match result {}: {}",name,e.getMessage());continue;}
                CACHE.put(name,parsed);
            }
            result.add(new Match(result.size()+1,parsed.started,parsed.map,parsed.mode,parsed.winner,parsed.alpha,parsed.bravo,parsed.seconds,parsed.players));
        }
        return result;
    }
    static Parsed parse(List<String> lines){
        String started="",map="",mode="CUSTOM",winner="";int alpha=0,bravo=0,seconds=0;
        var players=new ArrayList<Player>();var scores=new HashMap<String,Integer>();
        int section=0;
        for(var line:lines){
            // "Player | UUID | ..." before players were recorded by UUID only, "UUID | Team | ..." since.
            if(line.startsWith("Player | UUID")||line.startsWith("UUID | Team")){section=1;continue;}
            if(line.startsWith("개인전 순위")){section=2;continue;}
            if(section==0){
                int colon=line.indexOf(": ");if(colon<0)continue;
                String key=line.substring(0,colon),value=line.substring(colon+2).trim();
                switch(key){
                    case "Date / started" -> started=date(value);
                    case "Map" -> map=value.replaceFirst("\\s*\\([^()]*\\)$","");
                    case "Match mode" -> mode=value;
                    case "Winner" -> winner=value;
                    case "Play time" -> seconds=time(value);
                    case "Score ALPHA / BRAVO" -> {var parts=value.split("/");if(parts.length==2){alpha=number(parts[0]);bravo=number(parts[1]);}}
                    default -> {}
                }
                continue;
            }
            var cells=line.split(" \\| ");
            if(section==1&&(cells.length==6||cells.length==5)){
                int o=cells.length-5; // 1 when the row starts with a name
                var kda=cells[o+2].split("/");
                if(kda.length!=3)continue;
                double damage;try{damage=Double.parseDouble(cells[o+3].trim());}catch(NumberFormatException e){damage=0;}
                players.add(new Player(o==1?cells[0]:"","",cells[o].trim(),cells[o+1].trim(),number(kda[0]),number(kda[1]),number(kda[2]),damage,time(cells[o+4].trim()),0));
            }else if(section==2&&cells.length==3)scores.put(cells[1].trim(),number(cells[2]));
        }
        // The ranking names each player by UUID (or by name in older files).
        if(!scores.isEmpty())players.replaceAll(p->p.scored(scores.getOrDefault(p.id(),scores.getOrDefault(p.name(),0))));
        return new Parsed(started,map,mode,winner,alpha,bravo,seconds,List.copyOf(players));
    }
    /** "2026-11-13T23:35:02.16+09:00[Asia/Seoul]" → "2026-11-13 23:35". */
    private static String date(String value){
        return value.length()>=16&&value.charAt(10)=='T'?value.substring(0,10)+" "+value.substring(11,16):value;
    }
    private static int time(String value){
        var parts=value.split(":");
        return parts.length==2?number(parts[0])*60+number(parts[1]):0;
    }
    private static int number(String value){try{return Integer.parseInt(value.trim());}catch(NumberFormatException e){return 0;}}

    /** Lobby action {@code matchHistory}: empty value → the list, a number → that match. */
    public static void request(ServerPlayer viewer,String value,String target){
        var matches=all();
        if(value.isEmpty()){
            var rows=new ArrayList<Summary>(matches.size());
            for(var m:matches){
                var mine=m.players().stream().filter(p->p.id().equalsIgnoreCase(target)).findFirst().orElse(null);
                rows.add(new Summary(m.number(),m.started(),m.map(),m.mode(),m.winner(),m.alpha(),m.bravo(),m.seconds(),m.players().size(),
                        mine==null?"":mine.team(),mine==null?0:mine.kills(),mine==null?0:mine.deaths(),mine==null?0:mine.assists()));
            }
            String json=GSON.toJson(new View(target,rows,null));
            while(json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES&&rows.size()>1){
                rows.subList(0,Math.max(1,rows.size()/10)).clear();
                json=GSON.toJson(new View(target,rows,null));
            }
            GungangNetwork.send(viewer,new GungangNetwork.MatchHistoryView(json));
            return;
        }
        int number;try{number=Integer.parseInt(value);}catch(NumberFormatException e){return;}
        if(number<1||number>matches.size())return;
        GungangNetwork.send(viewer,new GungangNetwork.MatchHistoryView(GSON.toJson(new View(target,null,named(matches.get(number-1))))));
    }
    /**
     * The match with each player's current nickname and tag, looked up by UUID: the player DB, then the bot profiles.
     * Someone in neither keeps the name the file recorded, or a short UUID when it recorded none.
     */
    private static Match named(Match m){
        var records=ProgressionService.records();
        var players=new ArrayList<Player>(m.players().size());
        for(var p:m.players()){
            int bot=BotDatabase.slot(p.id());
            var record=records.get(p.id().toLowerCase(Locale.ROOT));
            if(bot>=0)players.add(p.named(BotManager.name(bot),BotManager.tag(bot)));
            else if(record!=null)players.add(p.named(record.displayName(),record.tag()));
            else players.add(p.named(p.name().isEmpty()?p.id().substring(0,Math.min(8,p.id().length())):p.name(),""));
        }
        return new Match(m.number(),m.started(),m.map(),m.mode(),m.winner(),m.alpha(),m.bravo(),m.seconds(),List.copyOf(players));
    }
    private MatchHistory() {}
}
