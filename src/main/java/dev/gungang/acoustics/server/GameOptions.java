package dev.gungang.acoustics.server;

import java.util.ArrayList;
import java.util.List;

/**
 * Lobby overrides, one section per mode plus the common rules. Every value is optional: null (a blank field in the
 * game options screen) keeps the selected map/mode's own value. Only the section of the mode being played applies.
 */
public record GameOptions(Common common,Control control,Normandy normandy,PropHunt propHunt,Escort escort,Bomb bomb,
                          Deathmatch deathmatch,FreeForAll freeForAll,
                          /* Legacy option.json (before per-mode options): folded into common. */
                          Boolean duplicatePrimary,Boolean duplicateSecondary) {
    public GameOptions(Common common,Control control,Normandy normandy,PropHunt propHunt,Escort escort,Bomb bomb,Deathmatch deathmatch,FreeForAll freeForAll){
        this(common,control,normandy,propHunt,escort,bomb,deathmatch,freeForAll,null,null);
    }
    public GameOptions {
        if(common==null)common=new Common(duplicatePrimary,duplicateSecondary,null,null,null);
        duplicatePrimary=duplicateSecondary=null;
        if(control==null)control=new Control(null,null,null,null);
        if(normandy==null)normandy=new Normandy(null,null,null);
        if(propHunt==null)propHunt=new PropHunt(null,null,null,null,null,null);
        if(escort==null)escort=new Escort(null,null,null,null,null,null,null,null);
        if(bomb==null)bomb=new Bomb(null,null,null,null,null,null,null);
        if(deathmatch==null)deathmatch=new Deathmatch(null,null,null);
        if(freeForAll==null)freeForAll=new FreeForAll(null,null,null,null);
    }
    public static GameOptions defaults(){return new GameOptions(null,null,null,null,null,null,null,null);}

    /** Modes with two teams of players on equal terms: the head-count handicap applies to these. */
    public static boolean teamMode(String mode){return java.util.Set.of("CONTROL","NORMANDY","ESCORT","BOMB","DEATHMATCH").contains(mode);}

    public record Common(Boolean duplicatePrimary,Boolean duplicateSecondary,Integer respawnSeconds,
                         /* Head-count handicap: the bigger team plays without secondaries, the smaller one gets more health. */
                         Boolean handicapSecondary,Double handicapHealth) {
        public Common {
            if(duplicatePrimary==null)duplicatePrimary=false;
            if(duplicateSecondary==null)duplicateSecondary=true;
            if(handicapSecondary==null)handicapSecondary=false;
            range(respawnSeconds,1,300,"리스폰 시간은 1~300초입니다.");
            range(handicapHealth,1,5,"인원 적은 팀 체력 배수는 1.00~5.00입니다.");
        }
    }
    /** 점령전 (쟁탈전). captureSeconds: neutral to fully held. */
    public record Control(Integer timeSeconds,Integer preparationSeconds,Integer captureSeconds,Integer targetScore) {
        public Control {time(timeSeconds);preparation(preparationSeconds);range(captureSeconds,1,3600,"점령 시간은 1~3600초입니다.");score(targetScore);}
    }
    /** 이동 점령. captureSeconds: per point in order, null entries keep the map's time. */
    public record Normandy(Integer timeSeconds,Integer preparationSeconds,List<Integer> captureSeconds) {
        public Normandy {
            time(timeSeconds);preparation(preparationSeconds);captureSeconds=list(captureSeconds,16);
            for(var s:captureSeconds)range(s,1,3600,"거점 점령 시간은 1~3600초입니다.");
        }
        public Integer capture(int point){return point<captureSeconds.size()?captureSeconds.get(point):null;}
    }
    public enum Choice { OFF, BASIC, FIRST_COME }
    /**
     * 숨바꼭질. timeSeconds: the seek time (hiding excluded); preparationSeconds: the hiding time. excluded: catalog
     * display numbers kept out of the deck. changes: disguise changes during the hunt, evenly spaced, the last one
     * no later than the final minute.
     */
    public record PropHunt(Integer timeSeconds,Integer preparationSeconds,List<String> excluded,Integer changes,Choice choice,Boolean cling) {
        public PropHunt {
            range(timeSeconds,60,3600,"숨바꼭질 제한 시간은 60~3600초입니다.");
            range(preparationSeconds,5,600,"숨바꼭질 준비 시간은 5~600초입니다.");
            var cleaned=new ArrayList<String>();
            for(var n:list(excluded,512)){if(n==null)continue;String t=n.strip();if(t.isEmpty())continue;if(!t.matches("[0-9]{1,4}"))throw new IllegalArgumentException("제외 블록은 블록 번호(숫자)로 입력하세요: "+t);t=t.length()==1?"0"+t:t;if(!cleaned.contains(t))cleaned.add(t);}
            excluded=List.copyOf(cleaned);
            range(changes,0,50,"변신 횟수는 0~50회입니다.");
        }
        public Choice choiceOrDefault(){return choice==null?Choice.BASIC:choice;}
        public boolean clingEnabled(){return cling==null||cling;}
        /** Seconds between changes for this many changes in this seek time; at least 60 when allowed. */
        public static int interval(int huntSeconds,int changes){return changes<=0?0:(huntSeconds-PropHuntGame.FINAL/20)/changes;}
        public static boolean allowed(int huntSeconds,int changes){return changes<=0||huntSeconds>=120&&huntSeconds>=60+60*changes;}
    }
    /**
     * 호위. timeSeconds: the starting clock; bonusSeconds: added on reaching each checkpoint, in order; overtimeSeconds:
     * the overtime after the clock runs out; speeds in blocks per second; maxMultiplier: reached with four escorts.
     */
    public record Escort(Integer timeSeconds,Integer preparationSeconds,List<Integer> bonusSeconds,Integer overtimeSeconds,
                         Double speed,Double maxMultiplier,Double reverseSpeed,Integer stopSeconds) {
        public Escort {
            time(timeSeconds);preparation(preparationSeconds);bonusSeconds=list(bonusSeconds,3);
            for(var s:bonusSeconds)range(s,0,3600,"체크포인트 추가 시간은 0~3600초입니다.");
            range(overtimeSeconds,1,600,"추가 시간(연장전)은 1~600초입니다.");
            range(speed,.1,10,"수레 속도는 0.1~10블록/초입니다.");
            range(maxMultiplier,1,5,"수레 최대 배속은 1.00~5.00입니다.");
            range(reverseSpeed,0,10,"수레 후진 속도는 0~10블록/초입니다.");
            range(stopSeconds,0,60,"체크포인트 정차 시간은 0~60초입니다.");
        }
        public Integer bonus(int checkpoint){return checkpoint<bonusSeconds.size()?bonusSeconds.get(checkpoint):null;}
    }
    /** 폭탄전. timeSeconds: per round; swapEveryRounds: rounds played before attack and defense switch sides. */
    public record Bomb(Integer timeSeconds,Integer preparationSeconds,Integer rounds,Integer swapEveryRounds,
                       Double plantSeconds,Double fuseSeconds,Double defuseSeconds) {
        public Bomb {
            range(timeSeconds,10,3600,"폭탄전 라운드 시간은 10~3600초입니다.");
            range(preparationSeconds,1,600,"폭탄전 준비 시간은 1~600초입니다.");
            range(rounds,2,99,"라운드 수는 2~99입니다.");
            range(swapEveryRounds,1,99,"공수교대 라운드 수는 1 이상입니다.");
            range(plantSeconds,.5,300,"폭탄 설치 시간은 0.5~300초입니다.");
            range(fuseSeconds,1,3600,"폭탄 폭파 시간은 1~3600초입니다.");
            range(defuseSeconds,.5,300,"폭탄 해체 시간은 0.5~300초입니다.");
        }
    }
    public record Deathmatch(Integer timeSeconds,Integer preparationSeconds,Integer targetScore) {
        public Deathmatch {time(timeSeconds);preparation(preparationSeconds);score(targetScore);}
    }
    /** 개인전. preparationSeconds: the opening grace (part of the time limit); protectionSeconds: after each respawn. */
    public record FreeForAll(Integer timeSeconds,Integer preparationSeconds,Integer protectionSeconds,Integer targetScore) {
        public FreeForAll {
            time(timeSeconds);range(preparationSeconds,0,600,"개인전 준비(시작 무적) 시간은 0~600초입니다.");
            range(protectionSeconds,0,300,"리스폰 후 무적 시간은 0~300초입니다.");score(targetScore);
        }
    }

    private static void time(Integer seconds){range(seconds,30,86400,"게임 제한 시간은 30~86400초입니다.");}
    private static void preparation(Integer seconds){range(seconds,1,600,"게임 준비 시간은 1~600초입니다.");}
    private static void score(Integer score){range(score,1,10000,"목표 점수는 1~10000입니다.");}
    private static void range(Number value,double min,double max,String message){
        if(value!=null&&(!Double.isFinite(value.doubleValue())||value.doubleValue()<min||value.doubleValue()>max))throw new IllegalArgumentException(message);
    }
    private static <T> List<T> list(List<T> values,int max){
        if(values==null)return List.of();
        if(values.size()>max)throw new IllegalArgumentException("항목이 너무 많습니다 (최대 "+max+"개).");
        return java.util.Collections.unmodifiableList(new ArrayList<>(values)); // nulls allowed: a blank entry keeps the map's value
    }
    private static <T> T or(T value,T fallback){return value==null?fallback:value;}

    // ---- Applying to a match ------------------------------------------------------------------------------------

    public DeathmatchRules deathmatch(DeathmatchRules base){return new DeathmatchRules(or(deathmatch.timeSeconds,base.timeLimitSeconds()),or(deathmatch.targetScore,base.winningKills()));}

    /** The map as this mode plays it with these overrides (the arena already reduced to the mode, see LobbyManager). */
    public ArenaMap apply(ArenaMap base,String mode){
        var json=ServerFiles.GSON.toJsonTree(base).getAsJsonObject();var original=json.deepCopy();
        switch(mode){
            case "CONTROL"->{
                if(control.timeSeconds!=null)json.addProperty("timeLimitSeconds",control.timeSeconds);
                if(control.targetScore!=null&&base.capture()!=null)json.getAsJsonObject("capture").addProperty("winningScore",control.targetScore);
            }
            case "NORMANDY"->{if(normandy.timeSeconds!=null)json.addProperty("timeLimitSeconds",normandy.timeSeconds);}
            case "ESCORT"->{
                if(escort.timeSeconds!=null)json.addProperty("timeLimitSeconds",escort.timeSeconds);
                if(base.escort()!=null){
                    var e=json.getAsJsonObject("escort");
                    if(escort.speed!=null)e.addProperty("speed",escort.speed);
                    if(escort.maxMultiplier!=null)e.addProperty("maxMultiplier",escort.maxMultiplier);
                    if(escort.stopSeconds!=null)e.addProperty("checkpointStopSeconds",escort.stopSeconds);
                }
            }
            case "BOMB"->{
                if(bomb.timeSeconds!=null)json.addProperty("timeLimitSeconds",bomb.timeSeconds);
                if(base.bomb()!=null){
                    var b=json.getAsJsonObject("bomb");
                    int rounds=or(bomb.rounds,base.bomb().rounds()),swap=or(bomb.swapEveryRounds,Math.min(base.bomb().swapEveryRounds(),rounds));
                    if(swap>rounds)throw new IllegalArgumentException("공수교대 라운드 수("+swap+")는 라운드 수("+rounds+") 이하여야 합니다.");
                    b.addProperty("rounds",rounds);b.addProperty("swapEveryRounds",swap);
                    if(bomb.preparationSeconds!=null)b.addProperty("preparationSeconds",bomb.preparationSeconds);
                }
            }
            case "FREE_FOR_ALL"->{
                if(base.freeForAll()!=null){
                    var f=json.getAsJsonObject("freeForAll");
                    if(freeForAll.timeSeconds!=null){f.addProperty("timeLimitSeconds",freeForAll.timeSeconds);json.addProperty("timeLimitSeconds",freeForAll.timeSeconds);}
                    if(freeForAll.targetScore!=null)f.addProperty("winningScore",freeForAll.targetScore);
                    int time=or(freeForAll.timeSeconds,base.freeForAll().timeLimitSeconds()),opening=or(freeForAll.preparationSeconds,FreeForAllMatch.OPENING_TICKS/20);
                    if(opening>=time)throw new IllegalArgumentException("개인전 준비(시작 무적) 시간은 게임 제한 시간보다 짧아야 합니다.");
                }
            }
            case "PROP_HUNT"->{
                int hunt=or(propHunt.timeSeconds,PropHuntGame.huntTicks(base)/20);
                if(propHunt.changes!=null&&!PropHunt.allowed(hunt,propHunt.changes))
                    throw new IllegalArgumentException(hunt<120?"변신 횟수는 게임 제한 시간이 120초 이상일 때만 설정할 수 있습니다."
                            :"변신 "+propHunt.changes+"회에는 게임 제한 시간이 "+(60+60*propHunt.changes)+"초 이상 필요합니다 (변신 대기 시간 최소 60초).");
                for(var n:propHunt.excluded)if(PropCatalog.type(n)==null)throw new IllegalArgumentException("없는 블록 번호입니다: "+n);
            }
            default->{}
        }
        return json.equals(original)?base:ServerFiles.GSON.fromJson(json,ArenaMap.class);
    }
    /** Preparation seconds for a mode that keeps them outside the map file (bomb: in the map, applied above). */
    public Integer preparation(String mode){
        return switch(mode){case "CONTROL"->control.preparationSeconds;case "NORMANDY"->normandy.preparationSeconds;case "ESCORT"->escort.preparationSeconds;case "DEATHMATCH"->deathmatch.preparationSeconds;default->null;};
    }
    /** Normandy capture ticks per point: the override, else the map's shared value. */
    public int[] normandyTicks(NormandyRules rules){
        var ticks=new int[rules.points().size()];
        for(int i=0;i<ticks.length;i++){var s=normandy.capture(i);ticks[i]=s==null?rules.captureTicks():s*20;}
        return ticks;
    }

    // ---- Placeholders -----------------------------------------------------------------------------------------

    /**
     * Every value the selected map and mode play with when nothing is overridden, for the screen's placeholders.
     * {@code arena} is already reduced to the mode; null when no map is selected.
     */
    public static GameOptions effectiveDefaults(ArenaMap arena,String mode,TeamOptions team){
        var common=new Common(false,true,"FREE_FOR_ALL".equals(mode)?LobbyManager.FFA_RESPAWN_TICKS/20:team.respawnSeconds(),false,1.0);
        if(arena==null)return new GameOptions(common,null,null,null,null,null,null,null);
        int time=Math.max(30,arena.timeLimitSeconds());
        Control control=null;Normandy normandy=null;PropHunt prop=null;Escort escort=null;Bomb bomb=null;Deathmatch tdm=null;FreeForAll ffa=null;
        switch(mode){
            case "CONTROL"->{if(arena.capture()!=null)control=new Control(time,30,Math.max(1,(int)Math.round(arena.capture().captureStepTicks()*100/20.0)),arena.capture().winningScore());}
            case "NORMANDY"->{if(arena.normandy()!=null){var list=new ArrayList<Integer>();for(var p:arena.normandy().points())list.add(Math.max(1,arena.normandy().captureTicks()/20));normandy=new Normandy(time,45,list);}}
            case "PROP_HUNT"->{int hunt=PropHuntGame.huntTicks(arena)/20;prop=new PropHunt(hunt,PropHuntGame.HIDE/20,List.of(),PropHuntGame.legacyChanges(hunt*20),Choice.BASIC,true);}
            case "ESCORT"->{if(arena.escort()!=null){var r=arena.escort();int b=r.checkpointBonusSeconds();escort=new Escort(time,30,List.of(b,b,b),EscortGame.OVERTIME_TICKS/20,r.speed(),r.maxMultiplier(),r.speed()*EscortGame.PUSH_BACK,r.checkpointStopSeconds());}}
            case "BOMB"->{if(arena.bomb()!=null){var r=arena.bomb();var t=BombEquipment.timing;bomb=new Bomb(Math.min(3600,Math.max(10,arena.timeLimitSeconds())),r.preparationSeconds(),Math.max(2,r.rounds()),r.swapEveryRounds(),Math.max(.5,t.plantSeconds()),Math.max(1,t.fuseSeconds()),Math.max(.5,t.defuseSeconds()));}}
            case "DEATHMATCH"->{var r=team.deathmatch();tdm=new Deathmatch(r.timeLimitSeconds(),30,r.winningKills());}
            case "FREE_FOR_ALL"->{if(arena.freeForAll()!=null)ffa=new FreeForAll(arena.freeForAll().timeLimitSeconds(),FreeForAllMatch.OPENING_TICKS/20,FreeForAllMatch.RESPAWN_PROTECTION_TICKS/20,arena.freeForAll().winningScore());}
            default->{}
        }
        return new GameOptions(common,control,normandy,prop,escort,bomb,tdm,ffa);
    }
}
