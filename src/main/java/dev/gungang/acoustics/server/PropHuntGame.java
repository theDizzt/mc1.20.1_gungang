package dev.gungang.acoustics.server;

import java.util.*;

/** Tick-based rules and a single, non-replenishing deck shared by every hider. */
public final class PropHuntGame {
    /** FINAL: the last minute of the hunt, without disguise changes and with faster seekers. */
    public static final int HIDE=60*20, HUNT=10*60*20, CHANGE=3*60*20, REVIEW=30*20, FINAL=60*20;
    public enum Phase { HIDING, HUNTING, REVIEW, FINISHED }
    private final ArrayDeque<String> deck;
    private final Set<UUID> alive=new LinkedHashSet<>();
    private final Map<UUID,Integer> hidden=new LinkedHashMap<>(),found=new LinkedHashMap<>();
    /** Seek time in ticks; HUNT unless the map sets propHuntMinutes. */
    private final int hunt;
    private int ticks,reviewTicks;
    private String winner="";
    public PropHuntGame(Collection<String> types,Collection<UUID> hiders,Random random){this(types,hiders,random,HUNT);}
    public PropHuntGame(Collection<String> types,Collection<UUID> hiders,Random random,int hunt){
        this.hunt=hunt;
        var unique=new ArrayList<>(new LinkedHashSet<>(types));
        if(hiders.isEmpty())throw new IllegalArgumentException("숨는 플레이어가 필요합니다.");
        // Every hider draws its first block plus one per change: 3 for 10 minutes, 5 for 16.
        int draws=draws(hunt);
        if(unique.size()<new HashSet<>(hiders).size()*draws)throw new IllegalArgumentException("블록 중복 방지를 위해 숨는 인원은 최대 "+unique.size()/draws+"명입니다.");
        Collections.shuffle(unique,random);deck=new ArrayDeque<>(unique);alive.addAll(hiders);
        hiders.forEach(id->hidden.put(id,0));
    }
    /** Debug prop session: no deck, hiders join and leave freely. */
    PropHuntGame(){deck=new ArrayDeque<>();hunt=HUNT;}
    /** Blocks one hider uses: the first disguise and every change before the final minute. */
    static int draws(int hunt){return 1+Math.max(0,(hunt-FINAL-1)/CHANGE);}
    public static int huntTicks(ArenaMap map){return map==null||map.propHuntMinutes()==null?HUNT:map.propHuntMinutes()*60*20;}
    void join(UUID id){alive.add(id);hidden.putIfAbsent(id,0);}
    void remove(UUID id){alive.remove(id);}
    public String draw(){if(deck.isEmpty())throw new IllegalStateException("Prop deck exhausted");return deck.removeFirst();}
    public Phase phase(){return winner.isEmpty()?(ticks<HIDE?Phase.HIDING:Phase.HUNTING):reviewTicks<REVIEW?Phase.REVIEW:Phase.FINISHED;}
    public boolean tick(){
        if(!winner.isEmpty()){reviewTicks++;return false;}
        // Hidden time starts with the hunt; the hiding minute never counts.
        ticks++;if(ticks>HIDE)for(UUID id:alive)hidden.put(id,(ticks-HIDE)/20);
        if(alive.isEmpty())winner="BRAVO";else if(ticks>=HIDE+hunt)winner="ALPHA";
        return winner.isEmpty()&&ticks>HIDE&&(ticks-HIDE)%CHANGE==0&&ticks<HIDE+hunt-FINAL;
    }
    public boolean eliminate(UUID id,UUID hunter){
        if(!winner.isEmpty()||!alive.remove(id))return false;
        if(hunter!=null)found.merge(hunter,1,Integer::sum);
        if(alive.isEmpty())winner="BRAVO";
        return true;
    }
    public boolean alive(UUID id){return alive.contains(id);}
    public int hidden(UUID id){return hidden.getOrDefault(id,0);}
    public int found(UUID id){return found.getOrDefault(id,0);}
    public String winner(){return winner;}
    public int remaining(){return switch(phase()){case HIDING->HIDE-ticks;case HUNTING->HIDE+hunt-ticks;case REVIEW->REVIEW-reviewTicks;case FINISHED->0;};}
    /** Ticks to the next disguise change, or 0 when none is left (the final minute has none). */
    public int nextChange(){
        if(!winner.isEmpty())return 0;
        int next=ticks<HIDE?HIDE+CHANGE:HIDE+((ticks-HIDE)/CHANGE+1)*CHANGE;
        return next<HIDE+hunt-FINAL?next-ticks:0;
    }
    /** The seek timer on its own: the full hunt while hiding, then what is left of it. */
    public int huntRemaining(){return winner.isEmpty()?Math.min(hunt,HIDE+hunt-ticks):0;}
    public boolean finalMinute(){return winner.isEmpty()&&ticks>=HIDE+hunt-FINAL&&ticks<HIDE+hunt;}
    public static double jumpVelocity(int chargeTicks){
        double target=jumpHeight(.42)*(1+2*Math.min(20,Math.max(0,chargeTicks))/20.0),lo=.42,hi=.8;
        for(int i=0;i<40;i++){double mid=(lo+hi)/2;if(jumpHeight(mid)>target)hi=mid;else lo=mid;}
        return lo;
    }
    static double jumpHeight(double velocity){double height=0;while(velocity>0){height+=velocity;velocity=(velocity-.08)*.98;}return height;}
}
