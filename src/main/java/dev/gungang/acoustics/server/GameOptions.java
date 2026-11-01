package dev.gungang.acoustics.server;

/** Lobby overrides. Zero preserves the selected map/mode's configured value. */
public record GameOptions(int minutes,int targetScore,boolean duplicatePrimary,Boolean duplicateSecondary) {
    public GameOptions {
        if(minutes<0||minutes>1440||targetScore<0||targetScore>10000)throw new IllegalArgumentException("시간은 0~1440분, 목표 점수는 0~10000입니다. 0은 기본값입니다.");
        if(duplicateSecondary==null)duplicateSecondary=true;
    }
    public static GameOptions defaults(){return new GameOptions(0,0,false,true);}
    public DeathmatchRules deathmatch(DeathmatchRules base){return new DeathmatchRules(minutes==0?base.timeLimitSeconds():minutes*60,targetScore==0?base.winningKills():targetScore);}
    public ArenaMap apply(ArenaMap base){
        if(minutes==0&&targetScore==0)return base;
        var json=ServerFiles.GSON.toJsonTree(base).getAsJsonObject();
        if(minutes>0){
            if(base.propHunt()!=null){
                if(minutes<2||minutes>60)throw new IllegalArgumentException("숨바꼭질 찾는 시간은 2~60분입니다.");
                json.addProperty("propHuntMinutes",minutes);
            }
            json.addProperty("timeLimitSeconds",minutes*60);
            if(base.freeForAll()!=null)json.getAsJsonObject("freeForAll").addProperty("timeLimitSeconds",minutes*60);
        }
        if(targetScore>0){
            if(base.capture()!=null)json.getAsJsonObject("capture").addProperty("winningScore",targetScore);
            if(base.freeForAll()!=null)json.getAsJsonObject("freeForAll").addProperty("winningScore",targetScore);
            if(base.bomb()!=null){
                if(targetScore>50)throw new IllegalArgumentException("폭탄전 목표 승수는 1~50입니다.");
                var bomb=json.getAsJsonObject("bomb");int rounds=targetScore*2-1;
                bomb.addProperty("rounds",rounds);bomb.addProperty("swapEveryRounds",Math.min(base.bomb().swapEveryRounds(),rounds));
            }
        }
        return ServerFiles.GSON.fromJson(json,ArenaMap.class);
    }
}
