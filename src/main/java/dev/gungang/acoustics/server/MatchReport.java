package dev.gungang.acoustics.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MatchReport {
    public static String time(int seconds) { return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60); }
    public static Path save(Path directory, ArenaMap map, MatchSnapshot result, ZonedDateTime started, ZonedDateTime ended) throws IOException {
        String header="GunGang match result\nDate / started: "+started+"\nEnded: "+ended+"\nMap: "+map.name()+" ("+map.id()+")\nDescription: "+map.description()+"\nDimension: "+map.dimension()+"\nMode: "+map.gameMode()+"\nTime limit: "+map.timeLimitSeconds()+" seconds\nPlay time: "+time(result.seconds())+"\nWinner: "+result.winner()+"\nScore ALPHA / BRAVO: "+result.alphaScore()+" / "+result.bravoScore()+"\n\nPlayer | UUID | Team | K / D / A | Damage | Play time\n";
        var text=new StringBuilder(header);
        for(var r:result.players()) text.append(String.format(Locale.ROOT,"%s | %s | %s | %d / %d / %d | %.1f | %s%n",r.name(),r.id(),r.team(),r.kills(),r.deaths(),r.assists(),r.damage(),time(r.playSeconds())));
        Files.createDirectories(directory);
        Path file=directory.resolve(started.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))+"_"+map.id()+"_"+UUID.randomUUID()+".txt");
        Files.writeString(file,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        return file;
    }
    private MatchReport() {}
}
