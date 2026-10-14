package dev.gungang.acoustics.server;

import java.util.List;

public record MatchSnapshot(boolean active, boolean result, String map, String phase, int seconds, boolean countdown,
                            String winner, int alphaScore, int bravoScore, List<Row> players,
                            Capture point, TeamOptions options, List<Respawn> respawns,int elapsedSeconds,String weather,String dimension,BombMatch.View bomb,String timeOfDay) {
    public MatchSnapshot(boolean active,boolean result,String map,String phase,int seconds,boolean countdown,String winner,
                         int alphaScore,int bravoScore,List<Row> players,Capture point,TeamOptions options,List<Respawn> respawns,int elapsedSeconds,String weather,String dimension,BombMatch.View bomb){
        this(active,result,map,phase,seconds,countdown,winner,alphaScore,bravoScore,players,point,options,respawns,elapsedSeconds,weather,dimension,bomb,"day");
    }
    public MatchSnapshot(boolean active,boolean result,String map,String phase,int seconds,boolean countdown,String winner,
                         int alphaScore,int bravoScore,List<Row> players,Capture point,TeamOptions options,List<Respawn> respawns,int elapsedSeconds,String weather,String dimension){
        this(active,result,map,phase,seconds,countdown,winner,alphaScore,bravoScore,players,point,options,respawns,elapsedSeconds,weather,dimension,null);
    }
    public MatchSnapshot(boolean active,boolean result,String map,String phase,int seconds,boolean countdown,String winner,
                         int alphaScore,int bravoScore,List<Row> players,Capture point,TeamOptions options,List<Respawn> respawns) {
        this(active,result,map,phase,seconds,countdown,winner,alphaScore,bravoScore,players,point,options,respawns,result?seconds:0,"clear","");
    }
    public record Capture(String name, float progress, boolean contested, int owner, int goal, String mode, int completed) {
        public Capture(String name,float progress,boolean contested,int owner,int goal) { this(name,progress,contested,owner,goal,"CONTROL",0); }
    }
    public record Respawn(String id, int remainingTicks, int totalTicks) { }
    public MatchSnapshot(boolean active, boolean result, String map, String phase, int seconds, boolean countdown,
                         String winner, int alphaScore, int bravoScore, List<Row> players) {
        this(active,result,map,phase,seconds,countdown,winner,alphaScore,bravoScore,players,null,TeamOptions.defaults(),List.of());
    }
    public record Row(String id, String name, String team, int kills, int deaths, int assists, double damage, int playSeconds) { }
}
