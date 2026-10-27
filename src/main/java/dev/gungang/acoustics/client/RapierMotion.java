package dev.gungang.acoustics.client;

/** Grip and body keyframes adapted from the reference's straight thrust and outward blade beat. */
public final class RapierMotion {
    public record Pose(float x,float y,float z,float pitch,float yaw,float roll,
                       float armPitch,float armYaw,float armRoll,float bodyYaw,float lean) {}
    // Progress, grip xyz, grip pitch/yaw/roll (degrees), arm xyz and torso yaw/lean (radians).
    private static final float[][] THRUST={
        {0,    .48f,-.38f,-.95f, -35,  0, -8, -.35f,-.12f, 0,     0,    0},
        {.16f, .53f,-.34f,-.80f, -60, -5,-16, -.65f,-.24f,.12f, -.10f, .02f},
        {.36f, .13f,-.18f,-1.60f,-80,  0,  3, -1.57f,  0, 0,     .16f, .12f},
        {.46f, .13f,-.18f,-1.60f,-80,  0,  3, -1.57f,  0, 0,     .16f, .12f},
        {.72f, .31f,-.23f,-1.12f,-70, -2, -3, -.95f,-.06f,.04f,  .05f, .04f},
        {1,    .48f,-.38f,-.95f, -35,  0, -8, -.35f,-.12f, 0,     0,    0}
    };
    private static final float[][] SWEEP={
        {0,    .48f,-.38f,-.95f, -35,  0, -8, -.35f,-.12f, 0,      0,   0},
        {.18f, .20f,-.19f,-.84f, -48, 28,-32, -1.15f, .35f,-.18f,-.20f,.02f},
        {.38f, .34f,-.08f,-1.20f,-78,-12, 22, -1.52f,-.25f, .12f, .10f,.08f},
        {.56f, .62f,-.15f,-1.10f,-68,-58, 42, -1.35f,-.95f, .28f, .38f,.10f},
        {.68f, .64f,-.22f,-1.02f,-56,-64, 28, -1.18f,-1.02f,.22f, .32f,.06f},
        {.84f, .55f,-.39f,-.86f, -24,-24,  4, -.65f,-.45f, .08f, .12f,.02f},
        {1,    .48f,-.38f,-.95f, -35,  0, -8, -.35f,-.12f, 0,      0,   0}
    };
    private static final Pose GUARD=new Pose(.48f,-.38f,-.95f,-35,0,-8,-.35f,-.12f,0,0,0);
    private static float progress(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    private static float lerp(float[] a,float[] b,int index,float u){return (1-u)*a[index]+u*b[index];}
    public static Pose sample(boolean sweep,float progress){
        float[][] keys=sweep?SWEEP:THRUST;
        float t=progress(progress);
        if(t==0||t==1)return GUARD;
        int i=1;while(i<keys.length-1&&t>keys[i][0])i++;
        float[] a=keys[i-1],b=keys[i];
        float u=(t-a[0])/(b[0]-a[0]);u=u*u*(3-2*u);
        return new Pose(lerp(a,b,1,u),lerp(a,b,2,u),lerp(a,b,3,u),lerp(a,b,4,u),lerp(a,b,5,u),lerp(a,b,6,u),
                lerp(a,b,7,u),lerp(a,b,8,u),lerp(a,b,9,u),lerp(a,b,10,u),lerp(a,b,11,u));
    }
    /** Cancel the upright vanilla grip as the arm extends, so the tip follows the forearm. */
    public static float wrist(boolean sweep,float progress){
        // ItemInHandLayer turns the item 180 degrees around Y before applying this wrist rotation.
        float t=progress(progress);if(t==0||t==1)return 0;
        float[][] keys=sweep?SWEEP:THRUST;
        int i=1;while(i<keys.length-1&&t>keys[i][0])i++;
        float[] a=keys[i-1],b=keys[i];float u=(t-a[0])/(b[0]-a[0]);u=u*u*(3-2*u);
        return -Math.min(90,Math.max(0,(-lerp(a,b,7,u)-.35f)*90/1.22f));
    }
    private RapierMotion(){}
}
