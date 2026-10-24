package dev.gungang.acoustics.server;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Human-like aiming instead of snapping to the target: spot → reaction delay → turn at a limited rate → an initial
 * overshoot that settles → fire once the crosshair is close enough, with a small tremor that never fully goes away.
 * Projectile weapons lead moving targets by their observed velocity and the projectile's travel time.
 */
final class BotAim {
    private float yaw,pitch,errorYaw,errorPitch,solutionYaw,solutionPitch;
    private java.util.UUID aimed;
    private int readyAt;

    void reset(BotEntity bot) {yaw=bot.getYRot();pitch=bot.getXRot();aimed=null;errorYaw=errorPitch=0;}
    float yaw() {return yaw;}
    float pitch() {return pitch;}

    /**
     * @param speed projectile speed in blocks per tick, or 0 for hitscan-like weapons; @param drop extra aim height per
     * block of distance (arrow arc)
     */
    void track(BotEntity bot,LivingEntity target,Vec3 velocity,BotSettings settings,int now,double speed,double gravity,double drag) {
        var random=bot.getRandom();
        if(!target.getUUID().equals(aimed)) {
            aimed=target.getUUID();
            // Reaction varies ±30% around the setting; the first swing overshoots in proportion to the spread.
            readyAt=now+Math.max(1,(int)Math.round(settings.reactionTicks()*(.7+random.nextDouble()*.6)));
            errorYaw=(float)((random.nextDouble()*2-1)*settings.aimError()*3);
            errorPitch=(float)((random.nextDouble()*2-1)*settings.aimError()*1.5);
        }
        Vec3 eye=bot.getEyePosition();
        // Upper chest: a sensible point that still connects when the aim drifts a little.
        Vec3 point=new Vec3(target.getX(),target.getEyeY()-.35,target.getZ());
        Vec3 observed=point;
        var flight=BotBallistics.flight(point.distanceTo(eye),speed,gravity,drag);
        for(int i=0;i<3&&speed>0&&velocity!=null;i++) {
            point=observed.add(velocity.scale(Math.min(40,flight.ticks())));
            flight=BotBallistics.flight(point.distanceTo(eye),speed,gravity,drag);
        }
        Vec3 d=point.subtract(eye);double flat=d.horizontalDistance();
        float wantYaw=(float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90;
        float wantPitch=(float)(-Mth.atan2(d.y+flight.drop(),flat)*180/Math.PI);
        solutionYaw=wantYaw;solutionPitch=wantPitch;
        // The overshoot settles over a few tenths of a second; a tremor of a fifth of the spread remains.
        errorYaw=errorYaw*.82f+(float)((random.nextDouble()*2-1)*settings.aimError()*.2);
        errorPitch=errorPitch*.82f+(float)((random.nextDouble()*2-1)*settings.aimError()*.12);
        float rate=(float)(double)settings.turnRate();
        yaw=approach(yaw,wantYaw+errorYaw,rate);pitch=Mth.clamp(approach(pitch,wantPitch+errorPitch,rate),-90,90);
        apply(bot);
    }
    /** Turn the view toward a point of interest without shooting (search, chase, cover watch). */
    void look(BotEntity bot,Vec3 point,BotSettings settings) {
        Vec3 d=point.subtract(bot.getEyePosition());double flat=d.horizontalDistance();
        float wantYaw=(float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90,wantPitch=(float)(-Mth.atan2(d.y,flat)*180/Math.PI);
        float rate=(float)(double)settings.turnRate()*.6f;
        yaw=approach(yaw,wantYaw,rate);pitch=approach(pitch,wantPitch,rate);aimed=null;
        apply(bot);
    }
    /** Follow wherever the body is going; keeps the stored aim in sync for the next acquisition. */
    void idle(BotEntity bot) {yaw=bot.getYHeadRot();pitch=bot.getXRot();aimed=null;}
    private void apply(BotEntity bot) {
        // Drive the head through LookControl at full speed so the entity's own tick does not pull it back.
        Vec3 eye=bot.getEyePosition(),dir=Vec3.directionFromRotation(pitch,yaw);
        bot.getLookControl().setLookAt(eye.x+dir.x*10,eye.y+dir.y*10,eye.z+dir.z*10,180,180);
        bot.setYHeadRot(yaw);bot.setXRot(pitch);
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
        return Math.abs(Mth.wrapDegrees(solutionYaw-yaw))<=tolerance+Math.abs(errorYaw)&&Math.abs(solutionPitch-pitch)<=tolerance*2+Math.abs(errorPitch);
    }
    private static float approach(float from,float to,float rate) {
        float delta=Mth.wrapDegrees(to-from);
        return from+Mth.clamp(delta,-rate,rate);
    }
}
