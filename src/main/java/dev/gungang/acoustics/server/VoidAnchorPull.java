package dev.gungang.acoustics.server;

import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Separate from voluntary movement skills: a tether takes priority, without locking the camera. */
public final class VoidAnchorPull {
    private static final String OWNER="VoidAnchorMotionOwner",END="VoidAnchorMotionEnd";
    public static void receive(Player p,UUID anchor,boolean active,Vec3 motion) {
        var tag=p.getPersistentData();
        if(!active) {
            if(tag.hasUUID(OWNER)&&tag.getUUID(OWNER).equals(anchor)) {tag.remove(OWNER);tag.remove(END);p.setDeltaMovement(Vec3.ZERO);}
            return;
        }
        tag.putUUID(OWNER,anchor);tag.putLong(END,p.level().getGameTime()+10);
        tag.putDouble("VoidAnchorX",motion.x);tag.putDouble("VoidAnchorY",motion.y);tag.putDouble("VoidAnchorZ",motion.z);
    }
    public static boolean active(Player p) {return p.isAlive()&&!p.isSpectator()&&p.getPersistentData().getLong(END)>p.level().getGameTime();}
    public static boolean travel(Player p) {
        if(!p.isControlledByLocalInstance()||!active(p))return false;
        var tag=p.getPersistentData();p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);
        p.move(MoverType.SELF,new Vec3(tag.getDouble("VoidAnchorX"),tag.getDouble("VoidAnchorY"),tag.getDouble("VoidAnchorZ")));
        p.setDeltaMovement(Vec3.ZERO);return true;
    }
    private VoidAnchorPull() { }
}
