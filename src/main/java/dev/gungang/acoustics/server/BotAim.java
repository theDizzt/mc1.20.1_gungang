package dev.gungang.acoustics.server;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Human-like aiming instead of snapping to the target: spot → reaction delay → turn at a limited rate → an initial
 * overshoot that settles → fire once the crosshair is close enough, with a tremor that never fully goes away.
 * Like a player the bot tracks a moving target a little behind (its reaction), leads projectiles only roughly, aims
 * worse while moving and after being hit, and has to pull each shot's recoil back down ({@link #kick}).
 */
final class BotAim {
    private float yaw,pitch,errorYaw,errorPitch,solutionYaw,solutionPitch;
    /** Recoil the bot has not pulled back down yet, in degrees (negative pitch: muzzle climbs). */
    private float recoilYaw,recoilPitch;
    /** Where on the target's body this acquisition aims, relative to the upper chest; how much of the lead it judges. */
    private double offsetY,offsetSide,judged=1;
    private java.util.UUID aimed;
    private int readyAt;

    void reset(BotEntity bot) {yaw=bot.getYRot();pitch=bot.getXRot();aimed=null;errorYaw=errorPitch=0;recoilYaw=recoilPitch=0;}
    /** The crosshair: the tracked aim plus unrecovered recoil. */
    float yaw() {return yaw+recoilYaw;}
    float pitch() {return Mth.clamp(pitch+recoilPitch,-90,90);}
    /** Per-shot recoil of the gun, in degrees (TaCZ pitch/yaw keyframe peaks). */
    record Recoil(double pitch,double yaw) {static final Recoil NONE=new Recoil(0,0);}
    /** Share of a gun's camera kick a bot's shot gets; players also pull down while the kick plays. */
    static final double RECOIL_SHARE=.6;
    /** A shot left the barrel: the muzzle climbs, with some sideways wander. */
    void kick(BotEntity bot,Recoil recoil) {
        if(recoil.pitch()<=0&&recoil.yaw()<=0)return;
        var random=bot.getRandom();
        double up=recoil.pitch()*RECOIL_SHARE*(.8+random.nextDouble()*.4);
        double side=(recoil.yaw()>0?recoil.yaw()*RECOIL_SHARE:up*.25)*(random.nextDouble()*2-1);
        recoilPitch=(float)Math.max(-30,recoilPitch-up);recoilYaw=(float)Mth.clamp(recoilYaw+side,-15,15);
    }
    /** Hit: the view jolts and the aim has to settle again, more for heavier hits. */
    void flinch(BotEntity bot,float damage,BotSettings settings) {
        var random=bot.getRandom();double size=Math.min(8,damage*.35+settings.aimError()*.5);
        errorYaw+=(float)((random.nextDouble()*2-1)*size);errorPitch+=(float)((random.nextDouble()*2-1)*size*.6);
    }
    /** Pulling the recoil back down: faster for quicker (higher difficulty) hands. */
    private void recover(BotSettings settings) {
        float rate=(float)(settings.turnRate()*.08);
        recoilPitch=approach(recoilPitch,0,rate);recoilYaw=approach(recoilYaw,0,rate*.6f);
    }

    /**
     * @param speed projectile speed in blocks per tick, or 0 for hitscan-like weapons; @param drop extra aim height per
     * block of distance (arrow arc)
     */
    void track(BotEntity bot,LivingEntity target,Vec3 velocity,BotSettings settings,int now,double speed,double gravity,double drag) {
        var random=bot.getRandom();
        recover(settings);
        if(!target.getUUID().equals(aimed)) {
            aimed=target.getUUID();
            // Reaction varies ±30% around the setting; the first swing overshoots in proportion to the spread.
            readyAt=now+Math.max(1,(int)Math.round(settings.reactionTicks()*(.7+random.nextDouble()*.6)));
            errorYaw=(float)((random.nextDouble()*2-1)*settings.aimError()*3);
            errorPitch=(float)((random.nextDouble()*2-1)*settings.aimError()*1.5);
            // Not always the same spot: anywhere from the neck to the belly, a little off centre; and a projectile's
            // lead is a judgement, 70-110% of the true one.
            offsetY=(random.nextDouble()*2-1)*.4;offsetSide=(random.nextDouble()*2-1)*.2;judged=.7+random.nextDouble()*.4;
        }
        Vec3 eye=bot.getEyePosition();
        // Upper chest: a sensible point that still connects when the aim drifts a little.
        Vec3 point=new Vec3(target.getX(),target.getEyeY()-.35+offsetY,target.getZ());
        Vec3 across=point.subtract(eye).cross(new Vec3(0,1,0));
        if(across.lengthSqr()>1e-6)point=point.add(across.normalize().scale(offsetSide));
        // The view follows what the eyes saw a moment ago: a strafing target pulls the crosshair behind it.
        double lag=settings.reactionTicks()*.5;
        if(velocity!=null)point=point.subtract(velocity.x*lag,0,velocity.z*lag);
        Vec3 observed=point;
        var flight=BotBallistics.flight(point.distanceTo(eye),speed,gravity,drag);
        for(int i=0;i<3&&speed>0&&velocity!=null;i++) {
            point=observed.add(velocity.scale(Math.min(40,flight.ticks())*judged));
            flight=BotBallistics.flight(point.distanceTo(eye),speed,gravity,drag);
        }
        Vec3 d=point.subtract(eye);double flat=d.horizontalDistance();
        float wantYaw=(float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90;
        float wantPitch=(float)(-Mth.atan2(d.y+flight.drop(),flat)*180/Math.PI);
        solutionYaw=wantYaw;solutionPitch=wantPitch;
        // The overshoot settles over half a second or so; a tremor of about a third of the spread remains, up to
        // twice that while the bot itself is moving.
        double moving=1+Math.min(1,bot.getDeltaMovement().horizontalDistance()*5);
        errorYaw=errorYaw*.88f+(float)((random.nextDouble()*2-1)*settings.aimError()*.3*moving);
        errorPitch=errorPitch*.88f+(float)((random.nextDouble()*2-1)*settings.aimError()*.18*moving);
        float rate=(float)(double)settings.turnRate();
        yaw=approach(yaw,wantYaw+errorYaw,rate);pitch=Mth.clamp(approach(pitch,wantPitch+errorPitch,rate),-90,90);
        apply(bot);
    }
    /** Turn the view toward a point of interest without shooting (search, chase, cover watch). */
    void look(BotEntity bot,Vec3 point,BotSettings settings) {
        Vec3 d=point.subtract(bot.getEyePosition());double flat=d.horizontalDistance();
        float wantYaw=(float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90,wantPitch=(float)(-Mth.atan2(d.y,flat)*180/Math.PI);
        float rate=(float)(double)settings.turnRate()*.6f;
        recover(settings);
        yaw=approach(yaw,wantYaw,rate);pitch=approach(pitch,wantPitch,rate);aimed=null;
        apply(bot);
    }
    /** Follow wherever the body is going; keeps the stored aim in sync for the next acquisition. */
    void idle(BotEntity bot) {yaw=bot.getYHeadRot();pitch=bot.getXRot();aimed=null;recoilYaw=recoilPitch=0;}
    private void apply(BotEntity bot) {
        // Drive the head through LookControl at full speed so the entity's own tick does not pull it back.
        Vec3 eye=bot.getEyePosition(),dir=Vec3.directionFromRotation(pitch(),yaw());
        bot.getLookControl().setLookAt(eye.x+dir.x*10,eye.y+dir.y*10,eye.z+dir.z*10,180,180);
        bot.setYHeadRot(yaw());bot.setXRot(pitch());
    }
    /**
     * Fire only after the reaction delay and when the crosshair is within half a body width of the target point
     * (never tighter than 1.5°), so long-range shots wait for the aim to settle.
     */
    boolean ready(BotEntity bot,LivingEntity target,int now,boolean melee) {
        if(now<readyAt)return false;
        Vec3 d=new Vec3(target.getX(),target.getEyeY()-.35,target.getZ()).subtract(bot.getEyePosition());
        // A swing only needs to face the target roughly.
        if(melee)return Math.abs(Mth.wrapDegrees((float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90-yaw))<=45;
        double tolerance=Math.max(1.5,Math.toDegrees(Math.atan2(.45,Math.max(1,d.length()))));
        // Recoil counts: a climbing muzzle stops the burst until it is pulled back near the target.
        return Math.abs(Mth.wrapDegrees(solutionYaw-yaw()))<=tolerance+Math.abs(errorYaw)&&Math.abs(solutionPitch-pitch())<=tolerance*2+Math.abs(errorPitch);
    }
    private static float approach(float from,float to,float rate) {
        float delta=Mth.wrapDegrees(to-from);
        return from+Mth.clamp(delta,-rate,rate);
    }
}
