package dev.gungang.acoustics.client;

/**
 * Grip and body keyframes for the straight thrust and the outward blade beat, staged like an action-film strike:
 * a quick cock back (anticipation), a snapped lunge, a slight overshoot held for a beat on impact, then a slower
 * recovery to guard. Each key's last column eases the segment that ends at it.
 */
public final class RapierMotion {
    public record Pose(float x,float y,float z,float pitch,float yaw,float roll,
                       float armPitch,float armYaw,float armRoll,float bodyYaw,float lean) {}
    /** Segment easing: smoothstep, snap in (accelerating strike), settle out (decelerating), hold. */
    private static final float SMOOTH=0,SNAP=1,SETTLE=2;
    // Progress, grip xyz, grip pitch/yaw/roll (degrees), arm xyz and torso yaw/lean (radians), easing.
    private static final float[][] THRUST={
        {0,    .43f,-.32f,-.93f, -58, -9,-16, -.82f,-.18f,-.10f,-.18f, .025f,SMOOTH},
        {.15f, .50f,-.27f,-.73f, -64,  8,-23, -.72f, .14f,-.16f,-.30f,-.04f, SETTLE},
        // Overshoot past the hold: grip further out, rolled and leaning in; the arm itself stays level.
        {.30f, .10f,-.15f,-1.82f,-86, -4, 14, -1.57f,-.05f, 0,    .30f, .20f, SNAP},
        {.36f, .13f,-.18f,-1.60f,-80,  0,  3, -1.57f,  0,   0,    .16f, .12f, SETTLE},
        {.46f, .13f,-.18f,-1.60f,-80,  0,  3, -1.57f,  0,   0,    .16f, .12f, SMOOTH},
        {.72f, .36f,-.25f,-1.06f,-68, -3, -7, -1.02f,-.08f,-.02f,-.08f,.05f, SETTLE},
        {.88f, .46f,-.30f,-.88f, -55,-12,-20, -.78f,-.22f,-.12f,-.21f,.02f, SMOOTH},
        {1,    .43f,-.32f,-.93f, -58, -9,-16, -.82f,-.18f,-.10f,-.18f,.025f,SMOOTH}
    };
    private static final float[][] SWEEP={
        {0,    .43f,-.32f,-.93f, -58, -9,-16, -.82f,-.18f,-.10f,-.18f,.025f,SMOOTH},
        {.18f, .18f,-.18f,-.80f, -48, 34,-38, -1.18f, .42f,-.22f,-.26f,.02f,  SETTLE},
        {.36f, .34f,-.08f,-1.22f,-78,-14, 24, -1.52f,-.28f, .12f, .12f,.08f,  SNAP},
        {.52f, .64f,-.15f,-1.12f,-68,-66, 48, -1.36f,-1.02f,.30f, .42f,.10f,  SNAP},
        {.66f, .64f,-.22f,-1.02f,-56,-62, 30, -1.18f,-1.00f,.22f, .34f,.06f,  SETTLE},
        {.84f, .51f,-.34f,-.85f, -49,-25,-22, -.76f,-.38f,-.06f,-.06f,.03f, SETTLE},
        {1,    .43f,-.32f,-.93f, -58, -9,-16, -.82f,-.18f,-.10f,-.18f,.025f,SMOOTH}
    };
    // A forward point, tucked wrist and bladed torso instead of the upright vanilla sword carry.
    private static final Pose GUARD=new Pose(.43f,-.32f,-.93f,-58,-9,-16,-.82f,-.18f,-.10f,-.18f,.025f);
    private static float progress(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    private static float ease(float kind,float u){
        if(kind==SNAP)return u*u;                                    // slow start, whip into the end pose
        if(kind==SETTLE){float v=1-u;return 1-v*v*v*v;}              // arrives fast, decelerates into it
        return u*u*(3-2*u);
    }
    /**
     * First-person camera kick for an action-film impact, in degrees: zero before the strike lands, a sharp jolt
     * at impact, then a quick decay. {@code pitch} nods the view, {@code roll} tilts toward the striking hand.
     */
    public static float kick(boolean sweep,float progress){
        float t=progress(progress),hit=sweep?.52f:.30f;
        if(t<=hit-.04f||t>=1)return 0;
        if(t<hit)return (t-hit+.04f)/.04f;
        return (float)Math.exp(-(t-hit)*9);
    }
    private static float lerp(float[] a,float[] b,int index,float u){return (1-u)*a[index]+u*b[index];}
    public static Pose sample(boolean sweep,float progress){
        float[][] keys=sweep?SWEEP:THRUST;
        float t=progress(progress);
        if(t==0||t==1)return GUARD;
        int i=1;while(i<keys.length-1&&t>keys[i][0])i++;
        float[] a=keys[i-1],b=keys[i];
        float u=ease(b[12],(t-a[0])/(b[0]-a[0]));
        return new Pose(lerp(a,b,1,u),lerp(a,b,2,u),lerp(a,b,3,u),lerp(a,b,4,u),lerp(a,b,5,u),lerp(a,b,6,u),
                lerp(a,b,7,u),lerp(a,b,8,u),lerp(a,b,9,u),lerp(a,b,10,u),lerp(a,b,11,u));
    }
    /** Cancel the upright vanilla grip as the arm extends, so the tip follows the forearm. */
    public static float wrist(boolean sweep,float progress){
        // ItemInHandLayer turns the item 180 degrees around Y before applying this wrist rotation.
        return -Math.min(90,Math.max(0,(-sample(sweep,progress).armPitch()-.35f)*90/1.22f));
    }
    private RapierMotion(){}
}
