package dev.gungang.acoustics.server;

/**
 * Utility scores (0..1) for when the bot should use its gadget. Pure, so the curves are testable; the caller adds a
 * little noise and fires at {@link #THRESHOLD}. Placement and safety checks (blast radius, clear corridor, valid
 * ground) stay with the action itself: a high score only means "now would be a good moment".
 */
final class BotGadgetScore {
    static final float THRESHOLD=.5f;
    /**
     * @param sinceSeen ticks since an enemy was last in sight; @param sinceHurt ticks since the bot last took damage
     * @param traveling patrolling or chasing (not fighting); @param goalDistance horizontal blocks to the travel goal
     * @param spareHealth health covers the adrenaline cost with a margin
     */
    record Situation(boolean seen,double distance,float health,long sinceSeen,long sinceHurt,boolean traveling,double goalDistance,boolean spareHealth) { }

    static float score(String kind,Situation s) {
        return switch(kind) {
            // Throwables: best at mid range, useless point-blank or beyond a throw.
            case "molotov","arrow_grenade","use"->s.seen()?bell(s.distance(),13,12):0;
            // Traps go down on quiet stretches of the route, the longer since the last contact the likelier.
            case "landmine","bear_trap"->!s.seen()&&s.traveling()?clamp(s.sinceSeen()/200f)*.75f:0;
            // Cover when hurt and under fire.
            case "barrier"->s.seen()&&s.distance()<20?(1-s.health())*1.1f+(s.sinceHurt()<40?.2f:0):0;
            // Far targets are the ones guns struggle with; the strike itself refuses unsafe spots.
            case "orbital_strike"->s.seen()?.45f+.35f*clamp((float)(s.distance()/48)):0;
            case "recovery_potion"->(1-s.health())*1.3f;
            case "adrenaline"->s.seen()&&s.spareHealth()&&s.distance()<28?.35f+(s.distance()<16?.25f:0)+(s.health()>.8f?.15f:0):0;
            // Scan after a few seconds of searching without contact.
            case "infrared_scanner"->!s.seen()?clamp((s.sinceSeen()-20)/100f):0;
            case "shield"->s.seen()&&s.sinceHurt()<30?(1-s.health())*1.3f:0;
            // Long empty trips are worth flying.
            case "jetpack"->!s.seen()&&s.traveling()?clamp((float)((s.goalDistance()-10)/30)):0;
            // Life support triggers on lethal damage by itself.
            default->0;
        };
    }
    private static float bell(double x,double center,double width) {return clamp((float)(1-Math.abs(x-center)/width));}
    private static float clamp(float value) {return Math.max(0,Math.min(1,value));}
    private BotGadgetScore() { }
}
