package dev.gungang.acoustics.server;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Short-lived server-authored motion, applied by the locally controlled player's travel loop. */
public final class AirAssaultMotion {
    private static final String END="AirAssaultMotionEnd",X="AirAssaultMotionX",Y="AirAssaultMotionY",Z="AirAssaultMotionZ";
    public static void receive(Player p,boolean active,Vec3 motion) {
        var tag=p.getPersistentData();
        if(!active) {tag.remove(END);p.setDeltaMovement(Vec3.ZERO);return;}
        tag.putLong(END,p.level().getGameTime()+10);
        tag.putDouble(X,motion.x);tag.putDouble(Y,motion.y);tag.putDouble(Z,motion.z);
    }
    public static boolean active(Player p) {
        return p.isAlive()&&!p.isSpectator()&&!p.isPassenger()
                &&p.getPersistentData().getLong(END)>p.level().getGameTime();
    }
    public static boolean travel(Player p) {
        return p.isControlledByLocalInstance()&&step(p);
    }
    static boolean step(Player p) {
        if(!active(p))return false;
        var tag=p.getPersistentData();p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);
        p.move(MoverType.SELF,new Vec3(tag.getDouble(X),tag.getDouble(Y),tag.getDouble(Z)));
        p.setDeltaMovement(Vec3.ZERO);
        return true;
    }
    private AirAssaultMotion() { }
}
