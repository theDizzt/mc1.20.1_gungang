package dev.gungang.acoustics.server;

import java.util.*;

/** Tick-based rules and a single, non-replenishing deck shared by every hider. */
public final class PropHuntGame {
    /** FINAL: the last minute of the hunt, without disguise changes and with faster seekers. */
    public static final int HIDE=60*20, HUNT=10*60*20, CHANGE=3*60*20, REVIEW=30*20, FINAL=60*20;
    public enum Phase { HIDING, HUNTING, REVIEW, FINISHED }
    /** Blocks nobody has taken yet, shuffled. Offered blocks stay in it; only a taken block leaves. */
    private final List<String> deck;
    /** Choices on offer per hider; one hider's offer never shows another's, so two cannot take the same block. */
    private final Map<UUID,List<String>> offers=new HashMap<>();
    private final Set<UUID> alive=new LinkedHashSet<>();
    private final Map<UUID,Integer> hidden=new LinkedHashMap<>(),found=new LinkedHashMap<>();
    /** Seek time in ticks; HUNT unless the map sets propHuntMinutes. */
    private final int hunt;
    /** Hiding time, ticks between disguise changes and how many changes the hunt has. */
    private final int hide,change,changes;
    private int ticks,reviewTicks;
    private String winner="";
    /** First-come choice: one list shared by every hider; the index each hider claimed, and the blocks already taken. */
    private List<String> shared=List.of();
    private final Map<UUID,Integer> claims=new HashMap<>();
    private final Set<Integer> sharedTaken=new HashSet<>();
    /** Hiders that already took their block from the open shared list. */
    private final Set<UUID> served=new HashSet<>();
    public PropHuntGame(Collection<String> types,Collection<UUID> hiders,Random random){this(types,hiders,random,HUNT);}
    public PropHuntGame(Collection<String> types,Collection<UUID> hiders,Random random,int hunt){this(types,hiders,random,hunt,HIDE,null);}
    /** {@code changes} null: one change every 3 minutes before the final minute (3 blocks for 10 minutes, 5 for 16). */
    public PropHuntGame(Collection<String> types,Collection<UUID> hiders,Random random,int hunt,int hide,Integer changes){
        this.hunt=hunt;this.hide=hide;
        if(changes==null){change=CHANGE;this.changes=legacyChanges(hunt);}
        else{this.changes=Math.max(0,changes);change=this.changes==0?Integer.MAX_VALUE:Math.max(20,(hunt-FINAL)/this.changes);}
        var unique=new ArrayList<>(new LinkedHashSet<>(types));
        if(hiders.isEmpty())throw new IllegalArgumentException("숨는 플레이어가 필요합니다.");
        // Every hider draws its first block plus one per change.
        int draws=1+this.changes;
        if(unique.size()<new HashSet<>(hiders).size()*draws)throw new IllegalArgumentException("블록 중복 방지를 위해 숨는 인원은 최대 "+unique.size()/draws+"명입니다.");
        Collections.shuffle(unique,random);deck=new ArrayList<>(unique);alive.addAll(hiders);
        hiders.forEach(id->hidden.put(id,0));
    }
    /** Debug prop session: no deck, hiders join and leave freely. */
    PropHuntGame(){deck=new ArrayList<>();hunt=HUNT;hide=HIDE;change=CHANGE;changes=legacyChanges(HUNT);}
    /** Blocks one hider uses: the first disguise and every change before the final minute. */
    static int draws(int hunt){return 1+legacyChanges(hunt);}
    /** Changes of the default schedule: every 3 minutes, before the final minute. */
    public static int legacyChanges(int hunt){return Math.max(0,(hunt-FINAL-1)/CHANGE);}
    public int hideTicks(){return hide;}
    public int changeInterval(){return changes==0?0:change;}
    public static int huntTicks(ArenaMap map){return map==null||map.propHuntMinutes()==null?HUNT:map.propHuntMinutes()*60*20;}
    void join(UUID id){alive.add(id);hidden.putIfAbsent(id,0);}
    void remove(UUID id){alive.remove(id);offers.remove(id);}
    /** Takes the next block no hider has on offer (bots, and players without an offer). */
    public String draw(){
        for(String type:deck)if(!reserved(type)){deck.remove(type);return type;}
        throw new IllegalStateException("Prop deck exhausted");
    }
    private boolean reserved(String type){
        for(var list:offers.values())if(list.contains(type))return true;
        int i=shared.indexOf(type);return i>=0&&!sharedTaken.contains(i);
    }
    /** Blocks free to offer: in the deck and not on another hider's offer. */
    public int free(){int n=0;for(String type:deck)if(!reserved(type))n++;return n;}
    /** Puts up to {@code count} random free blocks on offer to this hider (replacing an earlier offer). */
    public List<String> offer(UUID id,int count,Random random){
        offers.remove(id);
        var free=new ArrayList<String>();for(String type:deck)if(!reserved(type))free.add(type);
        Collections.shuffle(free,random);
        var list=List.copyOf(free.subList(0,Math.max(0,Math.min(count,free.size()))));
        if(!list.isEmpty())offers.put(id,list);
        return list;
    }
    public List<String> offers(UUID id){return sharing()&&alive.contains(id)&&!served.contains(id)?shared:offers.getOrDefault(id,List.of());}
    /** Whether a first-come list is open. */
    public boolean sharing(){return !shared.isEmpty();}
    /**
     * Opens one first-come list of up to {@code count} free blocks for every hider (replacing any earlier one). The
     * blocks stay in the deck until someone takes one.
     */
    public List<String> openShared(int count,Random random){
        closeShared();
        var free=new ArrayList<String>();for(String type:deck)if(!reserved(type))free.add(type);
        Collections.shuffle(free,random);
        shared=List.copyOf(free.subList(0,Math.max(0,Math.min(count,free.size()))));
        return shared;
    }
    public void closeShared(){shared=List.of();claims.clear();sharedTaken.clear();served.clear();}
    /** Claims entry {@code index} of the shared list for this hider: false when someone else got it first. */
    public boolean claim(UUID id,int index){
        if(!sharing()||index<0||index>=shared.size()||sharedTaken.contains(index))return false;
        for(var e:claims.entrySet())if(e.getValue()==index&&!e.getKey().equals(id))return false;
        claims.put(id,index);return true;
    }
    public int claimed(UUID id){return claims.getOrDefault(id,-1);}
    /** Indices of the shared list another hider holds (claimed or already taken). */
    public List<Integer> takenByOthers(UUID id){
        var result=new TreeSet<>(sharedTaken);
        for(var e:claims.entrySet())if(!e.getKey().equals(id))result.add(e.getValue());
        return List.copyOf(result);
    }
    /** Shared entries nobody holds yet. */
    public List<Integer> unclaimed(){
        var result=new ArrayList<Integer>();
        for(int i=0;i<shared.size();i++)if(!sharedTaken.contains(i)&&!claims.containsValue(i))result.add(i);
        return result;
    }
    /** The hider's claim, else a random entry nobody holds, else (list used up) the next free block. */
    private String takeShared(UUID id,Random random){
        Integer index=claims.remove(id);
        if(index==null||sharedTaken.contains(index)){var free=unclaimed();index=free.isEmpty()?null:free.get(random.nextInt(free.size()));}
        if(index==null){served.add(id);return draw();}
        served.add(id);sharedTaken.add(index);String type=shared.get(index);deck.remove(type);return type;
    }
    /**
     * Takes the offered block at {@code index} (out of range: a random one of the offer) and ends the offer; only this
     * block leaves the deck, the others can be offered again. Without an offer, draws as usual.
     */
    public String take(UUID id,int index,Random random){
        if(sharing()&&alive.contains(id)&&!served.contains(id))return takeShared(id,random);
        var list=offers.remove(id);
        if(list==null||list.isEmpty())return draw();
        String type=list.get(index>=0&&index<list.size()?index:random.nextInt(list.size()));
        deck.remove(type);return type;
    }
    public void release(UUID id){offers.remove(id);}
    public Phase phase(){return winner.isEmpty()?(ticks<hide?Phase.HIDING:Phase.HUNTING):reviewTicks<REVIEW?Phase.REVIEW:Phase.FINISHED;}
    public boolean tick(){
        if(!winner.isEmpty()){reviewTicks++;return false;}
        // Hidden time starts with the hunt; the hiding minute never counts.
        ticks++;if(ticks>hide)for(UUID id:alive)hidden.put(id,(ticks-hide)/20);
        if(alive.isEmpty())winner="BRAVO";else if(ticks>=hide+hunt)winner="ALPHA";
        return winner.isEmpty()&&changes>0&&ticks>hide&&(ticks-hide)%change==0&&(ticks-hide)/change<=changes;
    }
    public boolean eliminate(UUID id,UUID hunter){
        if(!winner.isEmpty()||!alive.remove(id))return false;
        offers.remove(id);claims.remove(id);
        if(hunter!=null)found.merge(hunter,1,Integer::sum);
        if(alive.isEmpty())winner="BRAVO";
        return true;
    }
    public boolean alive(UUID id){return alive.contains(id);}
    public int hidden(UUID id){return hidden.getOrDefault(id,0);}
    public int found(UUID id){return found.getOrDefault(id,0);}
    public String winner(){return winner;}
    public int remaining(){return switch(phase()){case HIDING->hide-ticks;case HUNTING->hide+hunt-ticks;case REVIEW->REVIEW-reviewTicks;case FINISHED->0;};}
    /** Ticks to the next disguise change, or 0 when none is left (the final minute has none). */
    public int nextChange(){
        if(!winner.isEmpty()||changes==0)return 0;
        int index=ticks<hide?1:(ticks-hide)/change+1;
        return index<=changes?hide+index*change-ticks:0;
    }
    /** The seek timer on its own: the full hunt while hiding, then what is left of it. */
    public int huntRemaining(){return winner.isEmpty()?Math.min(hunt,hide+hunt-ticks):0;}
    public int huntTicks(){return hunt;}
    public boolean finalMinute(){return winner.isEmpty()&&ticks>=hide+hunt-FINAL&&ticks<hide+hunt;}
    /** The charged leap with Jump Boost added as vanilla adds it to a jump (0.1 per level), e.g. 159 brewing stand's. */
    public static double jumpVelocity(net.minecraft.world.entity.LivingEntity body,int chargeTicks){
        var boost=body.getEffect(net.minecraft.world.effect.MobEffects.JUMP);
        return jumpVelocity(chargeTicks)+(boost==null?0:.1*(boost.getAmplifier()+1));
    }
    public static double jumpVelocity(int chargeTicks){
        double target=jumpHeight(.42)*(1+2*Math.min(20,Math.max(0,chargeTicks))/20.0),lo=.42,hi=.8;
        for(int i=0;i<40;i++){double mid=(lo+hi)/2;if(jumpHeight(mid)>target)hi=mid;else lo=mid;}
        return lo;
    }
    static double jumpHeight(double velocity){double height=0;while(velocity>0){height+=velocity;velocity=(velocity-.08)*.98;}return height;}
}
