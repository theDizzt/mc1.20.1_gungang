package dev.gungang.acoustics.server;

import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;

/** Check the actual firing direction and curved path, not a line to the enemy's current eyes. */
final class BotShot {
    static boolean clear(BotEntity bot,float yaw,float pitch,double range,BotWeaponData weapon) {
        Vec3 point=bot.getEyePosition(),velocity=Vec3.directionFromRotation(pitch,yaw).scale(weapon.speed());
        double travelled=0;
        for(int tick=0;tick<80&&travelled<range;tick++) {
            double step=Math.min(velocity.length(),range-travelled);if(step<1e-5)break;
            Vec3 end=point.add(velocity.normalize().scale(step));
            var wall=bot.level().clip(new ClipContext(point,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,bot));
            if(wall.getType()==HitResult.Type.BLOCK)end=wall.getLocation();
            double margin=.25+Math.min(1,travelled*Math.tan(Math.toRadians(1)));
            var area=new AABB(point,end).inflate(margin);
            for(var friend:bot.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,area,
                    e->e!=bot&&BotEntity.participant(e)&&BotManager.side(e).equals(BotManager.side(bot))))
                if(friend.getBoundingBox().inflate(margin).clip(point,end).isPresent()||friend.getBoundingBox().inflate(margin).contains(point))return false;
            if(wall.getType()==HitResult.Type.BLOCK)break;
            point=end;travelled+=step;velocity=velocity.scale(weapon.drag()).add(0,-weapon.gravity(),0);
        }
        return true;
    }
    private BotShot(){}
}
