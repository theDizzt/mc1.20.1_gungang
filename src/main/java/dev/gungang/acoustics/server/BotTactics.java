package dev.gungang.acoustics.server;

import java.util.EnumMap;
import java.util.Map;

/**
 * Utility AI: tactical actions receive situation scores; emergencies preempt ordinary choices. Pure, so the
 * curves are unit-testable. Hysteresis keeps the bot from flickering between near-equal options; an urgent change
 * (a clearly better option) still switches at once; an action that failed is held down for a while.
 * Scores are tuning starting points, not measured values.
 */
final class BotTactics {
    enum Action {
        EVADE("위험 지역 이탈"),ENGAGE("교전"),PUSH("돌격"),COVER("엄폐 후 재정비"),RETREAT("후퇴"),REGROUP("아군 합류"),SUPPORT("아군 지원"),
        HEAL("회복"),INVESTIGATE("추적·확인"),RALLY("팀 포인트 확인"),PATROL("순찰");
        final String label;
        Action(String label){this.label=label;}
    }
    /**
     * @param health 0..1; @param ammo magazine fill 0..1 (1 when unknown); @param visible enemies in sight;
     * @param alliesNear allies within 16 blocks; @param alliesAlive allies alive anywhere; @param allyFighting an ally is
     * under fire nearby; @param distance to the target (infinite when none); @param underFire hit in the last 1.5 s;
     * @param contactAge ticks since the freshest unseen contact (infinite when none); @param allyFar nearest ally beyond 24 blocks
     * @param hazard standing in a burning patch or a visible orbital strike radius
     */
    record Situation(float health,float ammo,boolean reloading,int visible,int alliesNear,int alliesAlive,boolean allyFighting,
                     double distance,boolean underFire,boolean melee,boolean healthPack,long contactAge,boolean allyFar,boolean hazard) {
        Situation(float health,float ammo,boolean reloading,int visible,int alliesNear,int alliesAlive,boolean allyFighting,
                  double distance,boolean underFire,boolean melee,boolean healthPack,long contactAge,boolean allyFar) {
            this(health,ammo,reloading,visible,alliesNear,alliesAlive,allyFighting,distance,underFire,melee,healthPack,contactAge,allyFar,false);
        }
        boolean lastStand(){return alliesAlive==0;}
        /** Enemies in sight beyond those our nearby allies can answer. */
        int outnumbered(){return Math.max(0,visible-alliesNear-1);}
    }
    static final int HYSTERESIS=8,URGENT=25,COMMIT_TICKS=10,FAILURE_TICKS=80;
    private Action action=Action.PATROL;
    /** Starts outside the commitment window: the first decision of a life is free. */
    private int since=-COMMIT_TICKS;
    private final Map<Action,Integer> failedUntil=new EnumMap<>(Action.class);
    private final Map<Action,Float> last=new EnumMap<>(Action.class);

    static float score(Action action,Situation s) {
        float missing=1-s.health();
        return switch(action) {
            case COVER->{
                if(s.visible()==0&&!s.underFire()&&!s.reloading())yield 0;
                float v=20+(s.underFire()?missing*70:missing*25)+15*s.outnumbered();
                if(s.reloading()||s.ammo()<.15f)v+=65;
                if(s.melee())v-=30;
                if(s.lastStand())v+=10;
                yield v;
            }
            case REGROUP->{
                if(s.alliesAlive()==0||s.alliesNear()>0)yield 0;
                // Idle: only rejoin when the team is far away, never trail a player around.
                if(s.visible()==0&&!s.underFire())yield s.allyFar()?32:0;
                yield 30+missing*40+15*s.outnumbered();
            }
            case RETREAT->{
                if(s.visible()>0&&s.distance()>24&&(s.health()<.45f||s.ammo()<.15f||s.outnumbered()>=2))
                    yield 95+missing*20+10*s.outnumbered();
                if(!s.underFire()||s.health()>=.5f)yield 0;
                yield 25+missing*50+10*s.outnumbered()+(s.lastStand()?10:0);
            }
            case ENGAGE->{
                if(s.visible()==0)yield 0;
                float v=55+20*s.health()-12*s.outnumbered();
                if(s.reloading())v-=40;
                if(s.underFire()&&s.health()<.35f)v-=20;
                yield v;
            }
            case PUSH->{
                if(s.visible()==0)yield 0;
                float v=15+40*s.health()*(s.visible()==1?1:.5f)+(s.melee()?35:0)-10*(s.visible()-1);
                if(s.reloading())v-=30;
                if(s.lastStand())v-=15;
                yield v;
            }
            case SUPPORT->s.allyFighting()&&s.visible()==0?60+15*s.health():0;
            case HEAL->{
                if(!s.healthPack()||s.health()>=.6f)yield 0;
                yield s.visible()==0?40+missing*60:s.health()<.3f?50:0;
            }
            case INVESTIGATE->s.visible()==0&&s.contactAge()<400?Math.max(0,55-s.contactAge()/10f):0;
            case RALLY->0;
            case PATROL->20;
            // Nothing outranks walking out of a strike zone or fire.
            case EVADE->s.hazard()?100:0;
        };
    }

    /** Re-scores everything and returns the action to run this tick. */
    Action choose(Situation s,int now) {
        return choose(s,now,false,false);
    }
    /** Emergencies interrupt cover/shield commitments and never inherit normal failure cooldowns. */
    Action choose(Situation s,int now,boolean holdCover,boolean shield) {
        return choose(s,now,holdCover,shield,false);
    }
    Action choose(Situation s,int now,boolean holdCover,boolean shield,boolean rally) {
        if(s.hazard()) {
            for(var candidate:Action.values())last.put(candidate,score(candidate,s));
            failedUntil.remove(Action.EVADE);
            if(action!=Action.EVADE){action=Action.EVADE;since=now;}
            return action;
        }
        if(action==Action.EVADE){action=Action.PATROL;since=now-COMMIT_TICKS;}
        if(holdCover||shield) {
            Action held=holdCover?Action.COVER:Action.ENGAGE;
            if(action!=held){action=held;since=now;}
            return action;
        }
        Action best=action;float bestScore=Float.NEGATIVE_INFINITY,current=0;
        for(var candidate:Action.values()) {
            float score=score(candidate,s);
            if(candidate==Action.RALLY&&rally&&s.visible()==0&&!s.underFire()&&s.health()>=.5f&&!s.reloading()&&s.ammo()>=.15f)score=58;
            // Losing sight of the enemy on the way into cover must not cancel a needed reload.
            if(candidate==Action.COVER&&action==Action.COVER&&s.visible()==0&&(s.reloading()||s.ammo()<.5f))
                score=Math.max(score,80);
            if(now<failedUntil.getOrDefault(candidate,0))score=Float.NEGATIVE_INFINITY;
            last.put(candidate,score);
            if(candidate==action)current=score;
            float biased=candidate==action?score+HYSTERESIS:score;
            if(biased>bestScore) {bestScore=biased;best=candidate;}
        }
        // A fresh decision holds briefly unless something is clearly better.
        if(best!=action&&now-since<COMMIT_TICKS&&bestScore-current<URGENT)return action;
        if(best!=action) {action=best;since=now;}
        return action;
    }
    /** The running behaviour could not be carried out (no cover, unreachable, no pack): try something else. */
    void failed(Action failed,int now) {if(failed!=Action.EVADE)failedUntil.put(failed,now+FAILURE_TICKS);if(failed==action)since=now-COMMIT_TICKS;}
    /** Finished its job (e.g. reloaded behind cover): drop it for a moment so the bot re-engages. */
    void completed(Action done,int now,int rest) {if(done!=Action.EVADE)failedUntil.put(done,now+rest);if(done==action)since=now-COMMIT_TICKS;}
    Action action() {return action;}
    int since() {return since;}
    float lastScore(Action action) {return last.getOrDefault(action,0f);}
    void reset(int now) {action=Action.PATROL;since=now-COMMIT_TICKS;failedUntil.clear();last.clear();}
}
