package dev.gungang.acoustics.server;

import java.util.List;
/** seconds: the current phase's clock; huntSeconds: the seek timer kept separately (shown while hiding); rush: final minute. */
public record PropHuntSnapshot(boolean active,String phase,int seconds,int changeSeconds,List<Row> players,int huntSeconds,boolean rush) {
    public PropHuntSnapshot(boolean active,String phase,int seconds,int changeSeconds,List<Row> players){this(active,phase,seconds,changeSeconds,players,0,false);}
    public record Row(String id,String name,String team,String block,int state,int entity,boolean found,boolean fixed,int hiddenSeconds,int finds) {}
}
