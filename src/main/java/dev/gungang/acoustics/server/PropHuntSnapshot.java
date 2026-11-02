package dev.gungang.acoustics.server;

import java.util.List;
/** seconds: the current phase's clock; huntSeconds: the seek timer kept separately (shown while hiding); rush: final minute. */
/** offers/picked/choiceSeconds/firstChoice: the viewer's own block choice (catalog types), empty when none is open. */
public record PropHuntSnapshot(boolean active,String phase,int seconds,int changeSeconds,List<Row> players,int huntSeconds,boolean rush,List<String> offers,int picked,int choiceSeconds,boolean firstChoice) {
    public PropHuntSnapshot(boolean active,String phase,int seconds,int changeSeconds,List<Row> players,int huntSeconds,boolean rush){this(active,phase,seconds,changeSeconds,players,huntSeconds,rush,List.of(),-1,0,false);}
    public PropHuntSnapshot(boolean active,String phase,int seconds,int changeSeconds,List<Row> players){this(active,phase,seconds,changeSeconds,players,0,false);}
    public record Row(String id,String name,String team,String block,int state,int entity,boolean found,boolean fixed,int hiddenSeconds,int finds,int cooldown,int cooldownTotal,int active,float auxiliary,float auxiliaryMax,boolean stunned,int wall) {
        public Row(String id,String name,String team,String block,int state,int entity,boolean found,boolean fixed,int hiddenSeconds,int finds){this(id,name,team,block,state,entity,found,fixed,hiddenSeconds,finds,0,0,0,0,0,false,-1);}
        public Row(String id,String name,String team,String block,int state,int entity,boolean found,boolean fixed,int hiddenSeconds,int finds,int cooldown,int cooldownTotal,int active,float auxiliary,float auxiliaryMax,boolean stunned){this(id,name,team,block,state,entity,found,fixed,hiddenSeconds,finds,cooldown,cooldownTotal,active,auxiliary,auxiliaryMax,stunned,-1);}
        /** The wall a clinging hider hangs on, or null. */
        public net.minecraft.core.Direction clinging(){return wall<0?null:net.minecraft.core.Direction.from2DDataValue(wall);}
    }
}
